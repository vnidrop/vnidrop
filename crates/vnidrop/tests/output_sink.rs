mod support;

use std::sync::Arc;

use std::time::{Duration, Instant};

use support::{
    receive_with_sink_response, share_path, wait_for_receiver_request, MemoryOutputSink, TestNode,
};

#[test]
fn exports_nested_files_to_output_sink() {
    let source_dir = tempfile::tempdir().unwrap();
    let source_root = source_dir.path().join("photos");
    std::fs::create_dir_all(source_root.join("nested")).unwrap();
    std::fs::write(source_root.join("cover.txt"), b"cover").unwrap();
    std::fs::write(source_root.join("nested/inside.txt"), b"inside").unwrap();
    let sender = TestNode::new();
    let receiver = TestNode::new();
    let share = share_path(&sender.core, &source_root, 18, "photos", true);
    let output_sink = Arc::new(MemoryOutputSink::default());

    receive_with_sink_response(
        &sender.core,
        share.transfer_id,
        receiver.core.arc(),
        share.ticket,
        output_sink.clone(),
        true,
    )
    .unwrap();

    assert_eq!(output_sink.file("photos/cover.txt"), b"cover");
    assert_eq!(output_sink.file("photos/nested/inside.txt"), b"inside");
    assert_eq!(
        output_sink.terminal_state("photos/cover.txt"),
        Some("finished")
    );
    assert_eq!(
        output_sink.terminal_state("photos/nested/inside.txt"),
        Some("finished")
    );
}

#[test]
fn reports_output_sink_write_failure() {
    let source_dir = tempfile::tempdir().unwrap();
    let source_path = source_dir.path().join("hello.txt");
    std::fs::write(&source_path, b"hello").unwrap();
    let sender = TestNode::new();
    let receiver = TestNode::new();
    let share = share_path(&sender.core, &source_path, 19, "hello.txt", false);
    let output_sink = Arc::new(MemoryOutputSink::failing_writes());

    let error = receive_with_sink_response(
        &sender.core,
        share.transfer_id,
        receiver.core.arc(),
        share.ticket,
        output_sink.clone(),
        true,
    )
    .unwrap_err();

    assert!(error.contains("sink write failed"));
    assert_eq!(output_sink.terminal_state("hello.txt"), Some("aborted"));
}

#[test]
fn cancellation_during_export_aborts_open_sink_file() {
    let source_dir = tempfile::tempdir().unwrap();
    let source_path = source_dir.path().join("slow.bin");
    std::fs::File::create(&source_path)
        .unwrap()
        .set_len(32 * 1024 * 1024)
        .unwrap();
    let sender = TestNode::new();
    let receiver = TestNode::new();
    let share = share_path(&sender.core, &source_path, 29, "slow.bin", false);
    let output_sink = Arc::new(MemoryOutputSink::slow_writes(Duration::from_millis(25)));
    let receiver_core = receiver.core.arc();
    let sink_for_worker = output_sink.clone();
    let worker = std::thread::spawn(move || {
        receiver_core.receive_with_output_sink(
            share.ticket,
            sink_for_worker,
            Some("receiver".to_string()),
        )
    });

    let request = wait_for_receiver_request(&sender.core, share.transfer_id);
    sender
        .core
        .respond_receiver_request(request.id, true, None)
        .unwrap();
    let started = Instant::now();
    while !output_sink.has_started("slow.bin") {
        assert!(started.elapsed() < Duration::from_secs(15));
        std::thread::sleep(Duration::from_millis(10));
    }
    receiver.core.cancel_transfer(share.transfer_id).unwrap();
    assert!(worker.join().unwrap().is_err());
    assert_eq!(output_sink.terminal_state("slow.bin"), Some("aborted"));
}
