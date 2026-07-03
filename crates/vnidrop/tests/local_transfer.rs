use std::sync::{Arc, Mutex};

use vnidrop::{
    CoreEvent, CoreEventSink, ShareMetadataInput, ShareSource, SourceKind, TransferAccessMode,
    VnidropCore,
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

    receiver
        .receive(
            share.ticket,
            output_dir.path().to_string_lossy().to_string(),
            Some("receiver".to_string()),
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
    sender
        .set_transfer_access_mode(share.transfer_id, TransferAccessMode::ApprovalRequired)
        .unwrap();

    assert!(receiver
        .receive(
            share.ticket.clone(),
            denied_output.path().to_string_lossy().to_string(),
            Some("receiver".to_string()),
        )
        .is_err());
    assert!(sender_sink
        .events()
        .iter()
        .any(|event| event.phase == "access" && event.kind == "request-denied"));

    sender
        .approve_endpoint_for_transfer(share.transfer_id, receiver.status().endpoint_id)
        .unwrap();
    receiver
        .receive(
            share.ticket,
            allowed_output.path().to_string_lossy().to_string(),
            Some("receiver".to_string()),
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
