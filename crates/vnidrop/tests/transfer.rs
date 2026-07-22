mod support;

use support::{receive_with_response, share_path, TestNode};
use vnidrop::VnidropError;

#[test]
fn transfers_file_between_two_cores() {
    let source_dir = tempfile::tempdir().unwrap();
    let output_dir = tempfile::tempdir().unwrap();
    let source_path = source_dir.path().join("hello.txt");
    std::fs::write(&source_path, b"hello from vnidrop").unwrap();
    let sender = TestNode::new();
    let receiver = TestNode::new();

    let share = share_path(&sender.core, &source_path, 7, "hello.txt", false);
    receive_with_response(
        &sender.core,
        share.transfer_id,
        receiver.core.arc(),
        share.ticket,
        output_dir.path(),
        true,
    )
    .unwrap();

    assert_eq!(
        std::fs::read(output_dir.path().join("hello.txt")).unwrap(),
        b"hello from vnidrop"
    );
    let received = receiver
        .core
        .list_transfers()
        .unwrap()
        .into_iter()
        .find(|transfer| transfer.transfer_id == 7)
        .unwrap();
    assert!(!received.local_id.is_empty());
    assert_eq!(
        received.peer_id.as_deref(),
        Some(sender.core.status().endpoint_id.as_str())
    );
    let artifacts = receiver.core.list_received_artifacts().unwrap();
    assert_eq!(artifacts.len(), 1);
    assert_eq!(artifacts[0].relative_path, "hello.txt");
    assert_eq!(
        artifacts[0].logical_size,
        b"hello from vnidrop".len() as u64
    );
    assert_eq!(
        artifacts[0].locator,
        output_dir.path().join("hello.txt").to_string_lossy()
    );

    receiver.core.delete_receive_history().unwrap();
    assert_eq!(receiver.core.list_received_artifacts().unwrap(), artifacts);
}

#[test]
fn transfers_directory_between_two_cores() {
    let source_dir = tempfile::tempdir().unwrap();
    let output_dir = tempfile::tempdir().unwrap();
    let source_root = source_dir.path().join("photos");
    std::fs::create_dir_all(source_root.join("nested")).unwrap();
    std::fs::write(source_root.join("cover.txt"), b"cover").unwrap();
    std::fs::write(source_root.join("nested/inside.txt"), b"inside").unwrap();
    let sender = TestNode::new();
    let receiver = TestNode::new();

    let share = share_path(&sender.core, &source_root, 8, "photos", true);
    receive_with_response(
        &sender.core,
        share.transfer_id,
        receiver.core.arc(),
        share.ticket,
        output_dir.path(),
        true,
    )
    .unwrap();

    assert_eq!(
        std::fs::read(output_dir.path().join("photos/cover.txt")).unwrap(),
        b"cover"
    );
    assert_eq!(
        std::fs::read(output_dir.path().join("photos/nested/inside.txt")).unwrap(),
        b"inside"
    );
}

#[test]
fn receive_refuses_to_overwrite_existing_destination() {
    let source_dir = tempfile::tempdir().unwrap();
    let output_dir = tempfile::tempdir().unwrap();
    let source_path = source_dir.path().join("existing.txt");
    let output_path = output_dir.path().join("existing.txt");
    std::fs::write(&source_path, b"new content").unwrap();
    std::fs::write(&output_path, b"keep content").unwrap();
    let sender = TestNode::new();
    let receiver = TestNode::new();
    let share = share_path(&sender.core, &source_path, 27, "existing.txt", false);

    let error = receive_with_response(
        &sender.core,
        share.transfer_id,
        receiver.core.arc(),
        share.ticket,
        output_dir.path(),
        true,
    )
    .unwrap_err();
    assert!(matches!(error, VnidropError::DestinationExists { .. }));
    assert_eq!(std::fs::read(&output_path).unwrap(), b"keep content");
    assert!(std::fs::read_dir(output_dir.path())
        .unwrap()
        .all(|entry| !entry
            .unwrap()
            .file_name()
            .to_string_lossy()
            .contains(".part")));
    let transfer = receiver
        .core
        .list_transfers()
        .unwrap()
        .into_iter()
        .find(|transfer| transfer.transfer_id == 27)
        .unwrap();
    assert_eq!(transfer.status, "failed");
    assert!(receiver
        .core
        .list_events(Some(27))
        .unwrap()
        .iter()
        .any(|event| {
            event.phase == "error"
                && event.kind == "failed"
                && event.data_json.contains("\"code\":\"destination_exists\"")
        }));
}
