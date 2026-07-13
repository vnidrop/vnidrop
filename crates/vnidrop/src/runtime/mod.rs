//! Core runtime orchestration for the VniDrop Iroh backend.
//!
//! Split by concern so send/receive/provider/lifecycle changes stay reviewable:
//! - [`facade`] — UniFFI `VnidropCore` surface and runtime entry
//! - [`share`] — import/share pipeline
//! - [`receive`] — ticket receive, download, export
//! - [`lifecycle`] — cancel/delete/shutdown/status/access
//! - [`provider`] — blob provider events and per-connection send progress

mod facade;
mod lifecycle;
mod provider;
mod receive;
mod share;

pub use facade::VnidropCore;

use std::{
    collections::{HashMap, HashSet},
    path::PathBuf,
    str::FromStr,
    sync::{atomic::AtomicBool, Arc},
};

use anyhow::Result;
use iroh::{endpoint::presets, protocol::Router, Endpoint};
use iroh_blobs::{
    api::TempTag,
    format::collection::Collection,
    provider::events::{EventMask, EventSender},
    store::fs::FsStore,
    BlobsProtocol, Hash,
};
use serde_json::json;
use tokio::{
    sync::{oneshot, Mutex as TokioMutex, Semaphore},
    task::JoinHandle,
};

use crate::{
    access_policy::{mode_from_storage, AccessPolicy},
    api::{CoreEvent, CoreEventSink, CoreLimits},
    approval::ApprovalService,
    event_hub::EventHub,
    handshake::HandshakeService,
    logging::init_logging,
    repository::Repository,
    secret::load_or_create_secret,
    transfer_state::{TransferDirection, TransferStatus},
};

/// Owns the Iroh endpoint, blob store, transfer history, and byte streaming.
/// Kotlin owns app lifecycle and platform file picking.
pub(super) struct CoreInner {
    pub(super) endpoint: Endpoint,
    pub(super) router: Router,
    pub(super) store: FsStore,
    pub(super) repository: Repository,
    pub(super) event_hub: Arc<EventHub>,
    pub(super) approval: ApprovalService,
    pub(super) limits: CoreLimits,
    pub(super) transfer_slots: Semaphore,
    pub(super) access_policy: Arc<AccessPolicy>,
    /// Sync mutex so cancel can remove + signal without awaiting (and without
    /// holding a Tokio lock across repository I/O).
    pub(super) active_transfers: std::sync::Mutex<HashMap<u64, ActiveTransfer>>,
    // Newly imported shares retain a TempTag for the lifetime of this process.
    // Restored shares have no in-memory tag, but remain tracked so they can be
    // counted and explicitly revoked after a restart.
    pub(super) active_shares: TokioMutex<HashMap<u64, Option<TempTag>>>,
    /// Content hash → active share transfer ids (root and collection members).
    /// Multiple transfers can share the same content-addressed hash.
    pub(super) hash_to_transfer: TokioMutex<HashMap<String, HashSet<u64>>>,
    pub(super) connection_endpoints: TokioMutex<HashMap<u64, String>>,
    pub(super) provider_task: TokioMutex<Option<JoinHandle<()>>>,
    pub(super) shutdown_started: AtomicBool,
}

pub(super) struct ActiveTransfer {
    pub(super) direction: TransferDirection,
    pub(super) cancel: oneshot::Sender<()>,
}

impl CoreInner {
    pub(super) async fn start(
        app_data_dir: PathBuf,
        event_sink: Arc<dyn CoreEventSink>,
        limits: CoreLimits,
    ) -> Result<Arc<Self>> {
        tokio::fs::create_dir_all(&app_data_dir).await?;
        init_logging(&app_data_dir)?;
        let secret_key = load_or_create_secret(&app_data_dir).await?;
        let repository = Repository::open(&app_data_dir).await?;
        let store_root = app_data_dir.join("blobs");
        let store = FsStore::load(&store_root).await?;
        let endpoint = Endpoint::builder(presets::N0)
            .secret_key(secret_key)
            .bind()
            .await?;
        endpoint.online().await;

        // Provider events are where the sender sees remote readers.  The core
        // uses them for send progress and for the current approval gate.
        let (events, event_rx) = EventSender::channel(128, EventMask::ALL_READONLY);
        let blobs = BlobsProtocol::new(&store, Some(events));
        let recovered_transfers = repository.recover_interrupted_transfers().await?;
        let event_hub = Arc::new(EventHub::start(
            repository.clone(),
            event_sink,
            limits.event_queue_capacity as usize,
            limits.max_events,
        ));
        for recovered in recovered_transfers {
            event_hub.emit_transfer(
                recovered.transfer_id,
                recovered.direction.as_str(),
                "recovery",
                "interrupted-transfer-failed",
                json!({ "previous_status": recovered.previous_status.as_str() }),
            );
        }
        let expired_requests = repository
            .expire_pending_receiver_requests("application restarted before approval")
            .await?;
        if expired_requests > 0 {
            event_hub.emit_endpoint(
                "recovery",
                "pending-approvals-expired",
                json!({ "count": expired_requests }),
            );
        }
        let access_policy = AccessPolicy::new();
        // Restore share ownership and access mode before the router can serve
        // any request. Unknown persisted modes fail closed in mode_from_storage.
        // Register root + every collection member so child gets stay under ACL.
        let mut restored_hashes: HashMap<String, HashSet<u64>> = HashMap::new();
        let mut restored_active_shares = HashMap::new();
        for share in repository.list_active_shares().await? {
            let transfer_id = share.transfer_id;
            let Ok(root_hash) = Hash::from_str(&share.content_hash) else {
                repository
                    .transition_transfer_status(
                        transfer_id,
                        TransferStatus::Sharing,
                        TransferStatus::Failed,
                    )
                    .await?;
                event_hub.emit_transfer(
                    transfer_id,
                    TransferDirection::Send.as_str(),
                    "recovery",
                    "share-root-missing-or-corrupt",
                    json!({ "content_hash": share.content_hash }),
                );
                continue;
            };
            let collection = if store.blobs().has(root_hash).await.unwrap_or(false) {
                Collection::load(root_hash, store.as_ref()).await.ok()
            } else {
                None
            };
            let Some(collection) = collection else {
                repository
                    .transition_transfer_status(
                        transfer_id,
                        TransferStatus::Sharing,
                        TransferStatus::Failed,
                    )
                    .await?;
                event_hub.emit_transfer(
                    transfer_id,
                    TransferDirection::Send.as_str(),
                    "recovery",
                    "share-root-missing-or-corrupt",
                    json!({ "content_hash": share.content_hash }),
                );
                continue;
            };
            restored_hashes
                .entry(root_hash.to_string())
                .or_default()
                .insert(transfer_id);
            for (_, member_hash) in collection.iter() {
                restored_hashes
                    .entry(member_hash.to_string())
                    .or_default()
                    .insert(transfer_id);
            }
            restored_active_shares.insert(transfer_id, None);
            access_policy
                .set_mode(transfer_id, mode_from_storage(&share.access_mode))
                .await;
        }
        let approval = ApprovalService::new(
            repository.clone(),
            event_hub.clone(),
            access_policy.clone(),
            limits.max_pending_approvals as usize,
            limits.max_metadata_bytes,
        );
        let handshake = HandshakeService::new(approval.clone());
        let router = Router::builder(endpoint.clone())
            .accept(iroh_blobs::ALPN, blobs)
            .accept(HandshakeService::ALPN, handshake)
            .spawn();

        let inner = Arc::new(Self {
            endpoint,
            router,
            store,
            repository,
            event_hub,
            approval,
            transfer_slots: Semaphore::new(limits.max_concurrent_transfers as usize),
            limits,
            access_policy,
            active_transfers: std::sync::Mutex::new(HashMap::new()),
            active_shares: TokioMutex::new(restored_active_shares),
            hash_to_transfer: TokioMutex::new(restored_hashes),
            connection_endpoints: TokioMutex::new(HashMap::new()),
            provider_task: TokioMutex::new(None),
            shutdown_started: AtomicBool::new(false),
        });

        inner.emit_endpoint(
            "startup",
            "endpoint-online",
            json!({
                "endpoint_id": inner.endpoint.id().to_string(),
                "addr": format!("{:?}", inner.endpoint.addr()),
                "store_root": store_root.to_string_lossy(),
            }),
        );
        inner.spawn_provider_event_task(event_rx).await;
        Ok(inner)
    }

    pub(super) fn emit_endpoint(&self, phase: &str, kind: &str, data: serde_json::Value) {
        self.event_hub.emit_endpoint(phase, kind, data);
    }

    pub(super) fn emit_transfer(
        &self,
        transfer_id: u64,
        direction: &str,
        phase: &str,
        kind: &str,
        data: serde_json::Value,
    ) {
        self.event_hub
            .emit_transfer(transfer_id, direction, phase, kind, data);
    }

    pub(super) async fn register_share_hashes(
        &self,
        transfer_id: u64,
        hashes: impl IntoIterator<Item = Hash>,
    ) {
        let mut map = self.hash_to_transfer.lock().await;
        for hash in hashes {
            map.entry(hash.to_string()).or_default().insert(transfer_id);
        }
    }

    pub(super) async fn unregister_transfer_hashes(&self, transfer_id: u64) {
        let mut map = self.hash_to_transfer.lock().await;
        map.retain(|_, transfers| {
            transfers.remove(&transfer_id);
            !transfers.is_empty()
        });
    }

    pub(super) async fn list_events(&self, transfer_id: Option<u64>) -> Result<Vec<CoreEvent>> {
        self.event_hub.flush().await;
        self.repository
            .list_events(transfer_id, self.limits.max_events)
            .await
    }
}
