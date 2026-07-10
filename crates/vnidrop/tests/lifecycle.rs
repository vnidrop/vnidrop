mod support;

use std::sync::Arc;
use std::time::{Duration, Instant};

use support::{share_path, CoreGuard, RecordingSink, TestNode};
use vnidrop::{CoreLimits, ShareMetadataInput, ShareSource, SourceKind, TransferAccessMode};

#[test]
fn cancelling_share_updates_status_and_events() {
    let source_dir = tempfile::tempdir().unwrap();
    let source_path = source_dir.path().join("cancel.txt");
    std::fs::write(&source_path, b"cancel me").unwrap();
    let sender = TestNode::new();
    let share = share_path(&sender.core, &source_path, 10, "cancel.txt", false);

    sender.core.cancel_transfer(share.transfer_id).unwrap();

    let transfers = sender.core.list_transfers().unwrap();
    assert_eq!(transfers[0].status, "stopped");
    assert!(sender
        .sink
        .events()
        .iter()
        .any(|event| event.kind == "share-stopped"));
}

#[test]
fn persisted_share_is_recovered_and_can_be_stopped_after_restart() {
    let source_dir = tempfile::tempdir().unwrap();
    let core_dir = tempfile::tempdir().unwrap();
    let source_path = source_dir.path().join("persistent.txt");
    std::fs::write(&source_path, b"survives restart").unwrap();

    let sender = CoreGuard::start(core_dir.path(), Arc::new(RecordingSink::default()));
    let share = share_path(&sender, &source_path, 20, "persistent.txt", false);
    drop(sender);

    let restarted = CoreGuard::start(core_dir.path(), Arc::new(RecordingSink::default()));
    assert_eq!(restarted.status().active_shares, 1);
    restarted.cancel_transfer(share.transfer_id).unwrap();

    let transfer = restarted
        .list_transfers()
        .unwrap()
        .into_iter()
        .find(|transfer| transfer.transfer_id == share.transfer_id)
        .unwrap();
    assert_eq!(transfer.status, "stopped");
    assert_eq!(restarted.status().active_shares, 0);
}

#[test]
fn stopped_share_rejects_direct_legacy_blob_ticket() {
    let source_dir = tempfile::tempdir().unwrap();
    let output_dir = tempfile::tempdir().unwrap();
    let source_path = source_dir.path().join("revoked.txt");
    std::fs::write(&source_path, b"must not be served").unwrap();
    let sender = TestNode::new();
    let receiver = TestNode::new();
    let share = share_path(&sender.core, &source_path, 21, "revoked.txt", false);

    sender.core.cancel_transfer(share.transfer_id).unwrap();
    let result = receiver.core.receive(
        share.blob_ticket,
        output_dir.path().to_string_lossy().to_string(),
        Some("receiver".to_string()),
    );

    assert!(result.is_err(), "a stopped share must not serve blob bytes");
    assert!(!output_dir.path().join("revoked.txt").exists());
}

#[test]
fn failed_import_leaves_durable_failed_transfer() {
    let source_dir = tempfile::tempdir().unwrap();
    let sender = TestNode::new();
    let transfer_id = 22;

    let result = sender.core.share_files(
        vec![ShareSource {
            kind: SourceKind::Path,
            value: source_dir
                .path()
                .join("missing.txt")
                .to_string_lossy()
                .to_string(),
            display_name: Some("missing.txt".to_string()),
            is_directory: false,
        }],
        ShareMetadataInput {
            transfer_id,
            transfer_name: Some("missing".to_string()),
            sender_name: None,
        },
    );

    assert!(result.is_err());
    let transfer = sender
        .core
        .list_transfers()
        .unwrap()
        .into_iter()
        .find(|transfer| transfer.transfer_id == transfer_id)
        .unwrap();
    assert_eq!(transfer.status, "failed");
    assert_eq!(sender.core.status().active_shares, 0);
}

#[test]
fn duplicate_transfer_id_does_not_replace_active_share() {
    let source_dir = tempfile::tempdir().unwrap();
    let first_path = source_dir.path().join("first.txt");
    let second_path = source_dir.path().join("second.txt");
    std::fs::write(&first_path, b"first").unwrap();
    std::fs::write(&second_path, b"second").unwrap();
    let sender = TestNode::new();
    let first = share_path(&sender.core, &first_path, 23, "first.txt", false);

    let duplicate = sender.core.share_files(
        vec![ShareSource {
            kind: SourceKind::Path,
            value: second_path.to_string_lossy().to_string(),
            display_name: Some("second.txt".to_string()),
            is_directory: false,
        }],
        ShareMetadataInput {
            transfer_id: first.transfer_id,
            transfer_name: Some("second".to_string()),
            sender_name: None,
        },
    );

    assert!(duplicate.is_err());
    assert_eq!(sender.core.status().active_shares, 1);
    let transfer = sender
        .core
        .list_transfers()
        .unwrap()
        .into_iter()
        .find(|transfer| transfer.transfer_id == first.transfer_id)
        .unwrap();
    assert_eq!(transfer.status, "sharing");
    assert_eq!(transfer.transfer_name.as_deref(), Some("first.txt"));
}

#[test]
fn access_mode_update_requires_active_persisted_share() {
    let sender = TestNode::new();

    assert!(sender
        .core
        .set_transfer_access_mode(999, TransferAccessMode::Public)
        .is_err());
}

#[test]
fn source_limit_rejection_creates_no_transfer_state() {
    let core_dir = tempfile::tempdir().unwrap();
    let source_dir = tempfile::tempdir().unwrap();
    let first = source_dir.path().join("one.txt");
    let second = source_dir.path().join("two.txt");
    std::fs::write(&first, b"one").unwrap();
    std::fs::write(&second, b"two").unwrap();
    let limits = CoreLimits {
        max_sources: 1,
        ..CoreLimits::default()
    };
    let sender =
        CoreGuard::start_with_limits(core_dir.path(), Arc::new(RecordingSink::default()), limits);

    let result = sender.share_files(
        vec![
            ShareSource {
                kind: SourceKind::Path,
                value: first.to_string_lossy().to_string(),
                display_name: Some("one.txt".to_string()),
                is_directory: false,
            },
            ShareSource {
                kind: SourceKind::Path,
                value: second.to_string_lossy().to_string(),
                display_name: Some("two.txt".to_string()),
                is_directory: false,
            },
        ],
        ShareMetadataInput {
            transfer_id: 24,
            transfer_name: Some("too many".to_string()),
            sender_name: None,
        },
    );

    assert!(result.is_err());
    assert!(sender.list_transfers().unwrap().is_empty());
}

#[test]
fn cancellation_during_import_is_durable() {
    let source_dir = tempfile::tempdir().unwrap();
    let source_path = source_dir.path().join("large.bin");
    std::fs::File::create(&source_path)
        .unwrap()
        .set_len(256 * 1024 * 1024)
        .unwrap();
    let sender = TestNode::new();
    let core = sender.core.arc();
    let worker = std::thread::spawn(move || {
        core.share_files(
            vec![ShareSource {
                kind: SourceKind::Path,
                value: source_path.to_string_lossy().to_string(),
                display_name: Some("large.bin".to_string()),
                is_directory: false,
            }],
            ShareMetadataInput {
                transfer_id: 25,
                transfer_name: Some("large".to_string()),
                sender_name: None,
            },
        )
    });

    let started = Instant::now();
    loop {
        if sender
            .core
            .list_transfers()
            .unwrap()
            .iter()
            .any(|transfer| transfer.transfer_id == 25 && transfer.status == "importing")
        {
            break;
        }
        assert!(started.elapsed() < Duration::from_secs(10));
        std::thread::sleep(Duration::from_millis(10));
    }
    sender.core.cancel_transfer(25).unwrap();
    assert!(worker.join().unwrap().is_err());

    let transfer = sender
        .core
        .list_transfers()
        .unwrap()
        .into_iter()
        .find(|transfer| transfer.transfer_id == 25)
        .unwrap();
    assert_eq!(transfer.status, "cancelled");
    assert_eq!(sender.core.status().active_transfers, 0);
    assert_eq!(sender.core.status().active_shares, 0);
}
