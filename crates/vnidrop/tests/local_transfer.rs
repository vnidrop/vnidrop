use std::{
    collections::HashMap,
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};

use vnidrop::{
    CoreEvent, CoreEventSink, ReceiveOutputSink, ReceiverRequest, ShareMetadataInput, ShareSource,
    SourceKind, VnidropCore, VnidropError,
};

#[derive(Default)]
struct RecordingSink {
    events: Mutex<Vec<CoreEvent>>,
}

impl CoreEventSink for RecordingSink {
    fn on_event(&self, event: CoreEvent) {
        self.events.lock().unwrap().push(event);
    }
}

impl RecordingSink {
    fn events(&self) -> Vec<CoreEvent> {
        self.events.lock().unwrap().clone()
    }
}

#[derive(Default)]
struct MemoryOutputSink {
    files: Mutex<HashMap<String, Vec<u8>>>,
    fail_writes: bool,
}

impl ReceiveOutputSink for MemoryOutputSink {
    fn start_file(&self, relative_path: String) -> Result<(), VnidropError> {
        self.files.lock().unwrap().insert(relative_path, Vec::new());
        Ok(())
    }

    fn write_chunk(&self, relative_path: String, bytes: Vec<u8>) -> Result<(), VnidropError> {
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

    fn finish_file(&self, _relative_path: String) -> Result<(), VnidropError> {
        Ok(())
    }
}

impl MemoryOutputSink {
    fn file(&self, relative_path: &str) -> Vec<u8> {
        self.files.lock().unwrap()[relative_path].clone()
    }
}

fn wait_for_receiver_request(sender: &VnidropCore, transfer_id: u64) -> ReceiverRequest {
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
        std::thread::sleep(Duration::from_millis(50));
    }
}

fn receive_with_response(
    sender: &VnidropCore,
    transfer_id: u64,
    receiver: Arc<VnidropCore>,
    ticket: String,
    output_dir: String,
    receiver_name: Option<String>,
    accepted: bool,
) -> Result<(), String> {
    let handle = std::thread::spawn(move || {
        receiver
            .receive(ticket, output_dir, receiver_name)
            .map_err(|error| error.to_string())
    });
    let request = wait_for_receiver_request(sender, transfer_id);
    sender
        .respond_receiver_request(
            request.id,
            accepted,
            (!accepted).then(|| "sender-refused".to_string()),
        )
        .unwrap();
    handle.join().unwrap()
}

fn receive_with_sink_response(
    sender: &VnidropCore,
    transfer_id: u64,
    receiver: Arc<VnidropCore>,
    ticket: String,
    output_sink: Arc<dyn ReceiveOutputSink>,
    receiver_name: Option<String>,
    accepted: bool,
) -> Result<(), String> {
    let handle = std::thread::spawn(move || {
        receiver
            .receive_with_output_sink(ticket, output_sink, receiver_name)
            .map_err(|error| error.to_string())
    });
    let request = wait_for_receiver_request(sender, transfer_id);
    sender
        .respond_receiver_request(
            request.id,
            accepted,
            (!accepted).then(|| "sender-refused".to_string()),
        )
        .unwrap();
    handle.join().unwrap()
}

#[test]
fn two_local_cores_transfer_file() {
    let sender_dir = tempfile::tempdir().unwrap();
    let receiver_dir = tempfile::tempdir().unwrap();
    let output_dir = tempfile::tempdir().unwrap();
    let source_path = sender_dir.path().join("hello.txt");
    std::fs::write(&source_path, b"hello from vnidrop").unwrap();

    let sender_sink = Arc::new(RecordingSink::default());
    let receiver_sink = Arc::new(RecordingSink::default());
    let sender = VnidropCore::initialize(
        sender_dir.path().join("core").to_string_lossy().to_string(),
        sender_sink.clone(),
    )
    .unwrap();
    let receiver = VnidropCore::initialize(
        receiver_dir
            .path()
            .join("core")
            .to_string_lossy()
            .to_string(),
        receiver_sink.clone(),
    )
    .unwrap();

    let share = sender
        .share_files(
            vec![ShareSource {
                kind: SourceKind::Path,
                value: source_path.to_string_lossy().to_string(),
                display_name: Some("hello.txt".to_string()),
                is_directory: false,
            }],
            ShareMetadataInput {
                transfer_id: 7,
                transfer_name: Some("hello".to_string()),
                sender_name: Some("sender".to_string()),
            },
        )
        .unwrap();

    receive_with_response(
        &sender,
        share.transfer_id,
        receiver.clone(),
        share.ticket,
        output_dir.path().to_string_lossy().to_string(),
        Some("receiver".to_string()),
        true,
    )
    .unwrap();

    assert_eq!(
        std::fs::read(output_dir.path().join("hello.txt")).unwrap(),
        b"hello from vnidrop"
    );

    sender.shutdown();
    receiver.shutdown();
}

#[test]
fn two_local_cores_transfer_directory() {
    let sender_dir = tempfile::tempdir().unwrap();
    let receiver_dir = tempfile::tempdir().unwrap();
    let output_dir = tempfile::tempdir().unwrap();
    let source_root = sender_dir.path().join("photos");
    std::fs::create_dir_all(source_root.join("nested")).unwrap();
    std::fs::write(source_root.join("cover.txt"), b"cover").unwrap();
    std::fs::write(source_root.join("nested").join("inside.txt"), b"inside").unwrap();

    let sender = VnidropCore::initialize(
        sender_dir.path().join("core").to_string_lossy().to_string(),
        Arc::new(RecordingSink::default()),
    )
    .unwrap();
    let receiver = VnidropCore::initialize(
        receiver_dir
            .path()
            .join("core")
            .to_string_lossy()
            .to_string(),
        Arc::new(RecordingSink::default()),
    )
    .unwrap();

    let share = sender
        .share_files(
            vec![ShareSource {
                kind: SourceKind::Path,
                value: source_root.to_string_lossy().to_string(),
                display_name: Some("photos".to_string()),
                is_directory: true,
            }],
            ShareMetadataInput {
                transfer_id: 8,
                transfer_name: Some("photos".to_string()),
                sender_name: Some("sender".to_string()),
            },
        )
        .unwrap();

    receive_with_response(
        &sender,
        share.transfer_id,
        receiver.clone(),
        share.ticket,
        output_dir.path().to_string_lossy().to_string(),
        Some("receiver".to_string()),
        true,
    )
    .unwrap();

    assert_eq!(
        std::fs::read(output_dir.path().join("photos").join("cover.txt")).unwrap(),
        b"cover"
    );
    assert_eq!(
        std::fs::read(
            output_dir
                .path()
                .join("photos")
                .join("nested")
                .join("inside.txt")
        )
        .unwrap(),
        b"inside"
    );

    sender.shutdown();
    receiver.shutdown();
}

#[test]
fn output_sink_receive_exports_nested_files() {
    let sender_dir = tempfile::tempdir().unwrap();
    let receiver_dir = tempfile::tempdir().unwrap();
    let source_root = sender_dir.path().join("photos");
    std::fs::create_dir_all(source_root.join("nested")).unwrap();
    std::fs::write(source_root.join("cover.txt"), b"cover").unwrap();
    std::fs::write(source_root.join("nested").join("inside.txt"), b"inside").unwrap();

    let sender = VnidropCore::initialize(
        sender_dir.path().join("core").to_string_lossy().to_string(),
        Arc::new(RecordingSink::default()),
    )
    .unwrap();
    let receiver = VnidropCore::initialize(
        receiver_dir
            .path()
            .join("core")
            .to_string_lossy()
            .to_string(),
        Arc::new(RecordingSink::default()),
    )
    .unwrap();

    let share = sender
        .share_files(
            vec![ShareSource {
                kind: SourceKind::Path,
                value: source_root.to_string_lossy().to_string(),
                display_name: Some("photos".to_string()),
                is_directory: true,
            }],
            ShareMetadataInput {
                transfer_id: 18,
                transfer_name: Some("photos".to_string()),
                sender_name: Some("sender".to_string()),
            },
        )
        .unwrap();
    let output_sink = Arc::new(MemoryOutputSink::default());

    receive_with_sink_response(
        &sender,
        share.transfer_id,
        receiver.clone(),
        share.ticket,
        output_sink.clone(),
        Some("receiver".to_string()),
        true,
    )
    .unwrap();

    assert_eq!(output_sink.file("photos/cover.txt"), b"cover");
    assert_eq!(output_sink.file("photos/nested/inside.txt"), b"inside");

    sender.shutdown();
    receiver.shutdown();
}

#[test]
fn output_sink_receive_fails_when_sink_write_fails() {
    let sender_dir = tempfile::tempdir().unwrap();
    let receiver_dir = tempfile::tempdir().unwrap();
    let source_path = sender_dir.path().join("hello.txt");
    std::fs::write(&source_path, b"hello").unwrap();

    let sender = VnidropCore::initialize(
        sender_dir.path().join("core").to_string_lossy().to_string(),
        Arc::new(RecordingSink::default()),
    )
    .unwrap();
    let receiver = VnidropCore::initialize(
        receiver_dir
            .path()
            .join("core")
            .to_string_lossy()
            .to_string(),
        Arc::new(RecordingSink::default()),
    )
    .unwrap();

    let share = sender
        .share_files(
            vec![ShareSource {
                kind: SourceKind::Path,
                value: source_path.to_string_lossy().to_string(),
                display_name: Some("hello.txt".to_string()),
                is_directory: false,
            }],
            ShareMetadataInput {
                transfer_id: 19,
                transfer_name: Some("hello".to_string()),
                sender_name: Some("sender".to_string()),
            },
        )
        .unwrap();
    let output_sink = Arc::new(MemoryOutputSink {
        files: Mutex::new(HashMap::new()),
        fail_writes: true,
    });

    let error = receive_with_sink_response(
        &sender,
        share.transfer_id,
        receiver.clone(),
        share.ticket,
        output_sink,
        Some("receiver".to_string()),
        true,
    )
    .unwrap_err();

    assert!(error.contains("sink write failed"));

    sender.shutdown();
    receiver.shutdown();
}

#[test]
fn approval_required_denies_then_allows_receiver() {
    let sender_dir = tempfile::tempdir().unwrap();
    let receiver_dir = tempfile::tempdir().unwrap();
    let denied_output = tempfile::tempdir().unwrap();
    let allowed_output = tempfile::tempdir().unwrap();
    let source_path = sender_dir.path().join("private.txt");
    std::fs::write(&source_path, b"approved content").unwrap();

    let sender_sink = Arc::new(RecordingSink::default());
    let sender = VnidropCore::initialize(
        sender_dir.path().join("core").to_string_lossy().to_string(),
        sender_sink.clone(),
    )
    .unwrap();
    let receiver = VnidropCore::initialize(
        receiver_dir
            .path()
            .join("core")
            .to_string_lossy()
            .to_string(),
        Arc::new(RecordingSink::default()),
    )
    .unwrap();

    let share = sender
        .share_files(
            vec![ShareSource {
                kind: SourceKind::Path,
                value: source_path.to_string_lossy().to_string(),
                display_name: Some("private.txt".to_string()),
                is_directory: false,
            }],
            ShareMetadataInput {
                transfer_id: 9,
                transfer_name: Some("private".to_string()),
                sender_name: None,
            },
        )
        .unwrap();
    assert!(receive_with_response(
        &sender,
        share.transfer_id,
        receiver.clone(),
        share.ticket.clone(),
        denied_output.path().to_string_lossy().to_string(),
        Some("receiver".to_string()),
        false,
    )
    .is_err());
    assert!(sender_sink
        .events()
        .iter()
        .any(|event| event.phase == "approval" && event.kind == "receiver-refused"));

    receive_with_response(
        &sender,
        share.transfer_id,
        receiver.clone(),
        share.ticket,
        allowed_output.path().to_string_lossy().to_string(),
        Some("receiver".to_string()),
        true,
    )
    .unwrap();
    assert_eq!(
        std::fs::read(allowed_output.path().join("private.txt")).unwrap(),
        b"approved content"
    );

    sender.shutdown();
    receiver.shutdown();
}

#[test]
fn cancelling_share_updates_status_and_events() {
    let sender_dir = tempfile::tempdir().unwrap();
    let source_path = sender_dir.path().join("cancel.txt");
    std::fs::write(&source_path, b"cancel me").unwrap();
    let sink = Arc::new(RecordingSink::default());
    let sender = VnidropCore::initialize(
        sender_dir.path().join("core").to_string_lossy().to_string(),
        sink.clone(),
    )
    .unwrap();

    let share = sender
        .share_files(
            vec![ShareSource {
                kind: SourceKind::Path,
                value: source_path.to_string_lossy().to_string(),
                display_name: Some("cancel.txt".to_string()),
                is_directory: false,
            }],
            ShareMetadataInput {
                transfer_id: 10,
                transfer_name: Some("cancel".to_string()),
                sender_name: None,
            },
        )
        .unwrap();
    sender.cancel_transfer(share.transfer_id).unwrap();

    let transfers = sender.list_transfers().unwrap();
    assert_eq!(transfers[0].status, "stopped");
    assert!(sink
        .events()
        .iter()
        .any(|event| event.kind == "share-stopped"));
    sender.shutdown();
}
