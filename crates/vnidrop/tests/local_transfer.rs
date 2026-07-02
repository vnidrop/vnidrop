use std::sync::{Arc, Mutex};

use vnidrop::{CoreEvent, CoreEventSink, ShareMetadataInput, ShareSource, SourceKind, VnidropCore};

#[derive(Default)]
struct RecordingSink {
    events: Mutex<Vec<CoreEvent>>,
}

impl CoreEventSink for RecordingSink {
    fn on_event(&self, event: CoreEvent) {
        self.events.lock().unwrap().push(event);
    }
}

#[test]
fn two_local_cores_transfer_file() {
    let sender_dir = tempfile::tempdir().unwrap();
    let receiver_dir = tempfile::tempdir().unwrap();
    let output_dir = tempfile::tempdir().unwrap();
    let source_path = sender_dir.path().join("hello.txt");
    std::fs::write(&source_path, b"hello from vnidrop").unwrap();

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
                transfer_id: 7,
                transfer_name: Some("hello".to_string()),
                sender_name: Some("sender".to_string()),
            },
        )
        .unwrap();

    receiver
        .receive(
            share.ticket,
            output_dir.path().to_string_lossy().to_string(),
            Some("receiver".to_string()),
        )
        .unwrap();

    assert_eq!(
        std::fs::read(output_dir.path().join("hello.txt")).unwrap(),
        b"hello from vnidrop"
    );

    sender.shutdown();
    receiver.shutdown();
}
