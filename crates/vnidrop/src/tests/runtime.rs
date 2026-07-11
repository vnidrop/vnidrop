use std::sync::Arc;

use iroh_blobs::Hash;

use crate::{
    repository::{Repository, TransferUpsert},
    transfer_state::{TransferDirection, TransferStatus},
    CoreEvent, CoreEventSink, VnidropCore, VnidropError,
};

struct TestSink;

impl CoreEventSink for TestSink {
    fn on_event(&self, _event: CoreEvent) {}
}

#[test]
fn initializes_and_reports_endpoint() {
    let temp = tempfile::tempdir().unwrap();
    let core = VnidropCore::initialize(
        temp.path().to_string_lossy().to_string(),
        Arc::new(TestSink),
    )
    .unwrap();

    assert!(!core.status().endpoint_id.is_empty());
    core.shutdown();
}

#[test]
fn invalid_receive_ticket_is_typed_and_persisted_as_event() {
    let temp = tempfile::tempdir().unwrap();
    let core = VnidropCore::initialize(
        temp.path().to_string_lossy().to_string(),
        Arc::new(TestSink),
    )
    .unwrap();

    let error = core
        .receive(
            "not-a-ticket".to_string(),
            temp.path().to_string_lossy().to_string(),
            None,
        )
        .unwrap_err();
    assert!(matches!(error, VnidropError::Ticket { .. }));

    let events = core.list_events(None).unwrap();
    assert!(events
        .iter()
        .any(|event| event.phase == "error" && event.kind == "invalid-ticket"));
    core.shutdown();
}

#[test]
fn startup_recovers_interrupted_transfer_and_persists_event() {
    let temp = tempfile::tempdir().unwrap();
    let preparation_runtime = tokio::runtime::Runtime::new().unwrap();
    preparation_runtime.block_on(async {
        let repository = Repository::open(temp.path()).await.unwrap();
        repository
            .insert_transfer(TransferUpsert {
                transfer_id: 91,
                peer_id: None,
                direction: TransferDirection::Receive,
                status: TransferStatus::Receiving,
                transfer_name: Some("interrupted"),
                content_hash: Some("hash"),
                ticket: None,
                file_count: 1,
                total_size: 5,
                access_mode: "approval_required",
            })
            .await
            .unwrap();
    });
    drop(preparation_runtime);

    let core = VnidropCore::initialize(
        temp.path().to_string_lossy().to_string(),
        Arc::new(TestSink),
    )
    .unwrap();
    let transfer = core
        .list_transfers()
        .unwrap()
        .into_iter()
        .find(|transfer| transfer.transfer_id == 91)
        .unwrap();
    assert_eq!(transfer.status, "failed");

    let events = core.list_events(Some(91)).unwrap();
    assert!(events.iter().any(|event| {
        event.phase == "recovery"
            && event.kind == "interrupted-transfer-failed"
            && event.data_json.contains("receiving")
    }));
    core.shutdown();
}

#[test]
fn startup_fails_persisted_share_when_root_blob_is_missing() {
    let temp = tempfile::tempdir().unwrap();
    let preparation_runtime = tokio::runtime::Runtime::new().unwrap();
    preparation_runtime.block_on(async {
        let repository = Repository::open(temp.path()).await.unwrap();
        let missing_hash = Hash::new([42; 32]).to_string();
        repository
            .insert_transfer(TransferUpsert {
                transfer_id: 92,
                peer_id: None,
                direction: TransferDirection::Send,
                status: TransferStatus::Sharing,
                transfer_name: Some("missing blob"),
                content_hash: Some(&missing_hash),
                ticket: Some("ticket"),
                file_count: 1,
                total_size: 5,
                access_mode: "approval_required",
            })
            .await
            .unwrap();
    });
    drop(preparation_runtime);

    let core = VnidropCore::initialize(
        temp.path().to_string_lossy().to_string(),
        Arc::new(TestSink),
    )
    .unwrap();
    let transfer = core
        .list_transfers()
        .unwrap()
        .into_iter()
        .find(|transfer| transfer.transfer_id == 92)
        .unwrap();
    assert_eq!(transfer.status, "failed");
    assert_eq!(core.status().active_shares, 0);
    assert!(core.list_events(Some(92)).unwrap().iter().any(|event| {
        event.phase == "recovery" && event.kind == "share-root-missing-or-corrupt"
    }));
    core.shutdown();
}
