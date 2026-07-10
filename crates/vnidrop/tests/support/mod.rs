// Cargo compiles every file in `tests/` as a separate crate, and each scenario
// intentionally uses only a subset of this shared harness.
#![allow(dead_code)]

use std::{
    collections::HashMap,
    ops::Deref,
    path::Path,
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};

use vnidrop::{
    CoreEvent, CoreEventSink, CoreLimits, ReceiveOutputSink, ReceiverRequest, ShareMetadataInput,
    ShareResult, ShareSource, SourceKind, VnidropCore, VnidropError,
};

#[derive(Default)]
pub struct RecordingSink {
    events: Mutex<Vec<CoreEvent>>,
}

impl CoreEventSink for RecordingSink {
    fn on_event(&self, event: CoreEvent) {
        self.events.lock().unwrap().push(event);
    }
}

impl RecordingSink {
    pub fn events(&self) -> Vec<CoreEvent> {
        self.events.lock().unwrap().clone()
    }
}

pub struct CoreGuard(Arc<VnidropCore>);

impl CoreGuard {
    pub fn start(path: &Path, sink: Arc<dyn CoreEventSink>) -> Self {
        Self(
            VnidropCore::initialize(path.to_string_lossy().to_string(), sink)
                .expect("test core should initialize"),
        )
    }

    pub fn start_with_limits(
        path: &Path,
        sink: Arc<dyn CoreEventSink>,
        limits: CoreLimits,
    ) -> Self {
        Self(
            VnidropCore::initialize_with_limits(path.to_string_lossy().to_string(), sink, limits)
                .expect("test core should initialize with limits"),
        )
    }

    pub fn arc(&self) -> Arc<VnidropCore> {
        self.0.clone()
    }
}

impl Deref for CoreGuard {
    type Target = VnidropCore;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl Drop for CoreGuard {
    fn drop(&mut self) {
        self.0.shutdown();
    }
}

pub struct TestNode {
    _data_dir: tempfile::TempDir,
    pub core: CoreGuard,
    pub sink: Arc<RecordingSink>,
}

impl TestNode {
    pub fn new() -> Self {
        let data_dir = tempfile::tempdir().unwrap();
        let sink = Arc::new(RecordingSink::default());
        let core = CoreGuard::start(data_dir.path(), sink.clone());
        Self {
            _data_dir: data_dir,
            core,
            sink,
        }
    }
}

#[derive(Default)]
pub struct MemoryOutputSink {
    files: Mutex<HashMap<String, Vec<u8>>>,
    terminal: Mutex<HashMap<String, &'static str>>,
    fail_writes: bool,
    write_delay: Duration,
}

impl MemoryOutputSink {
    pub fn failing_writes() -> Self {
        Self {
            files: Mutex::new(HashMap::new()),
            terminal: Mutex::new(HashMap::new()),
            fail_writes: true,
            write_delay: Duration::ZERO,
        }
    }

    pub fn slow_writes(delay: Duration) -> Self {
        Self {
            files: Mutex::new(HashMap::new()),
            terminal: Mutex::new(HashMap::new()),
            fail_writes: false,
            write_delay: delay,
        }
    }

    pub fn file(&self, relative_path: &str) -> Vec<u8> {
        self.files.lock().unwrap()[relative_path].clone()
    }

    pub fn terminal_state(&self, relative_path: &str) -> Option<&'static str> {
        self.terminal.lock().unwrap().get(relative_path).copied()
    }

    pub fn has_started(&self, relative_path: &str) -> bool {
        self.files.lock().unwrap().contains_key(relative_path)
    }
}

impl ReceiveOutputSink for MemoryOutputSink {
    fn start_file(&self, relative_path: String) -> Result<(), VnidropError> {
        self.files.lock().unwrap().insert(relative_path, Vec::new());
        Ok(())
    }

    fn write_chunk(&self, relative_path: String, bytes: Vec<u8>) -> Result<(), VnidropError> {
        if !self.write_delay.is_zero() {
            std::thread::sleep(self.write_delay);
        }
        if self.fail_writes {
            return Err(VnidropError::Filesystem {
                reason: "sink write failed".to_string(),
            });
        }
        self.files
            .lock()
            .unwrap()
            .get_mut(&relative_path)
            .expect("file was not started")
            .extend(bytes);
        Ok(())
    }

    fn finish_file(&self, relative_path: String) -> Result<(), VnidropError> {
        self.terminal
            .lock()
            .unwrap()
            .insert(relative_path, "finished");
        Ok(())
    }

    fn abort_file(&self, relative_path: String, _reason: String) -> Result<(), VnidropError> {
        self.files.lock().unwrap().remove(&relative_path);
        self.terminal
            .lock()
            .unwrap()
            .insert(relative_path, "aborted");
        Ok(())
    }
}

pub fn share_path(
    sender: &VnidropCore,
    source: &Path,
    transfer_id: u64,
    display_name: &str,
    is_directory: bool,
) -> ShareResult {
    sender
        .share_files(
            vec![ShareSource {
                kind: SourceKind::Path,
                value: source.to_string_lossy().to_string(),
                display_name: Some(display_name.to_string()),
                is_directory,
            }],
            ShareMetadataInput {
                transfer_id,
                transfer_name: Some(display_name.to_string()),
                sender_name: Some("sender".to_string()),
            },
        )
        .expect("test share should be created")
}

pub fn wait_for_receiver_request(sender: &VnidropCore, transfer_id: u64) -> ReceiverRequest {
    let started = Instant::now();
    loop {
        let requests = sender.list_receiver_requests(transfer_id).unwrap();
        if let Some(request) = requests
            .into_iter()
            .find(|request| request.status == "requested")
        {
            return request;
        }
        assert!(
            started.elapsed() < Duration::from_secs(15),
            "timed out waiting for receiver request"
        );
        std::thread::sleep(Duration::from_millis(25));
    }
}

pub fn receive_with_response(
    sender: &VnidropCore,
    transfer_id: u64,
    receiver: Arc<VnidropCore>,
    ticket: String,
    output_dir: &Path,
    accepted: bool,
) -> Result<(), String> {
    let output_dir = output_dir.to_string_lossy().to_string();
    let handle = std::thread::spawn(move || {
        receiver
            .receive(ticket, output_dir, Some("receiver".to_string()))
            .map_err(|error| error.to_string())
    });
    respond_to_pending_request(sender, transfer_id, accepted);
    handle.join().unwrap()
}

pub fn receive_with_sink_response(
    sender: &VnidropCore,
    transfer_id: u64,
    receiver: Arc<VnidropCore>,
    ticket: String,
    output_sink: Arc<dyn ReceiveOutputSink>,
    accepted: bool,
) -> Result<(), String> {
    let handle = std::thread::spawn(move || {
        receiver
            .receive_with_output_sink(ticket, output_sink, Some("receiver".to_string()))
            .map_err(|error| error.to_string())
    });
    respond_to_pending_request(sender, transfer_id, accepted);
    handle.join().unwrap()
}

fn respond_to_pending_request(sender: &VnidropCore, transfer_id: u64, accepted: bool) {
    let request = wait_for_receiver_request(sender, transfer_id);
    sender
        .respond_receiver_request(
            request.id,
            accepted,
            (!accepted).then(|| "sender-refused".to_string()),
        )
        .unwrap();
}
