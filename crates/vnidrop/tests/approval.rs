mod support;

use std::sync::Arc;

use support::{
    receive_with_response, share_path, wait_for_receiver_request, CoreGuard, RecordingSink,
    TestNode,
};
use vnidrop::CoreLimits;

#[test]
fn approval_required_denies_then_allows_receiver() {
    let source_dir = tempfile::tempdir().unwrap();
    let denied_output = tempfile::tempdir().unwrap();
    let allowed_output = tempfile::tempdir().unwrap();
    let source_path = source_dir.path().join("private.txt");
    std::fs::write(&source_path, b"approved content").unwrap();
    let sender = TestNode::new();
    let receiver = TestNode::new();
    let share = share_path(&sender.core, &source_path, 9, "private.txt", false);

    assert!(receive_with_response(
        &sender.core,
        share.transfer_id,
        receiver.core.arc(),
        share.ticket.clone(),
        denied_output.path(),
        false,
    )
    .is_err());
    assert!(sender
        .sink
        .events()
        .iter()
        .any(|event| event.phase == "approval" && event.kind == "receiver-refused"));

    receive_with_response(
        &sender.core,
        share.transfer_id,
        receiver.core.arc(),
        share.ticket,
        allowed_output.path(),
        true,
    )
    .unwrap();
    assert_eq!(
        std::fs::read(allowed_output.path().join("private.txt")).unwrap(),
        b"approved content"
    );
}

#[test]
fn receiver_can_cancel_while_waiting_for_approval() {
    let source_dir = tempfile::tempdir().unwrap();
    let output_dir = tempfile::tempdir().unwrap();
    let source_path = source_dir.path().join("waiting.txt");
    std::fs::write(&source_path, b"waiting").unwrap();
    let sender = TestNode::new();
    let receiver = TestNode::new();
    let share = share_path(&sender.core, &source_path, 26, "waiting.txt", false);
    let receiver_core = receiver.core.arc();
    let ticket = share.ticket;
    let output = output_dir.path().to_string_lossy().to_string();
    let worker = std::thread::spawn(move || {
        receiver_core.receive(ticket, output, Some("receiver".to_string()))
    });

    let request = wait_for_receiver_request(&sender.core, share.transfer_id);
    receiver.core.cancel_transfer(share.transfer_id).unwrap();
    let _ = sender.core.respond_receiver_request(
        request.id,
        false,
        Some("receiver-cancelled".to_string()),
    );
    assert!(worker.join().unwrap().is_err());

    let transfer = receiver
        .core
        .list_transfers()
        .unwrap()
        .into_iter()
        .find(|transfer| transfer.transfer_id == share.transfer_id)
        .unwrap();
    assert_eq!(transfer.status, "cancelled");
}

#[test]
fn pending_approval_limit_denies_excess_receiver() {
    let sender_dir = tempfile::tempdir().unwrap();
    let source_dir = tempfile::tempdir().unwrap();
    let output_one = tempfile::tempdir().unwrap();
    let output_two = tempfile::tempdir().unwrap();
    let source_path = source_dir.path().join("limited.txt");
    std::fs::write(&source_path, b"limited").unwrap();
    let limits = CoreLimits {
        max_pending_approvals: 1,
        ..CoreLimits::default()
    };
    let sender = CoreGuard::start_with_limits(
        sender_dir.path(),
        Arc::new(RecordingSink::default()),
        limits,
    );
    let receiver_one = TestNode::new();
    let receiver_two = TestNode::new();
    let share = share_path(&sender, &source_path, 28, "limited.txt", false);

    let first_core = receiver_one.core.arc();
    let first_ticket = share.ticket.clone();
    let first_output = output_one.path().to_string_lossy().to_string();
    let first = std::thread::spawn(move || {
        first_core.receive(first_ticket, first_output, Some("first".to_string()))
    });
    let request = wait_for_receiver_request(&sender, share.transfer_id);

    let second = receiver_two.core.receive(
        share.ticket,
        output_two.path().to_string_lossy().to_string(),
        Some("second".to_string()),
    );
    assert!(second
        .unwrap_err()
        .to_string()
        .contains("too-many-pending-approvals"));

    sender
        .respond_receiver_request(request.id, false, Some("test complete".to_string()))
        .unwrap();
    assert!(first.join().unwrap().is_err());
}
