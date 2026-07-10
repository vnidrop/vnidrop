use std::{
    collections::HashMap,
    io,
    path::{Path, PathBuf},
    str::FromStr,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc,
    },
};

use anyhow::{Context, Result};
use bytes::Bytes;
use futures_lite::StreamExt as _;
use iroh::{endpoint::presets, protocol::Router, Endpoint};
use iroh_blobs::{
    api::{blobs::AddProgressItem, proto::ExportRangesItem, remote::GetProgressItem, TempTag},
    format::collection::Collection,
    get::request::get_hash_seq_and_sizes,
    provider::events::{EventMask, EventSender, ProviderMessage, RequestUpdate},
    store::fs::FsStore,
    ticket::BlobTicket,
    BlobFormat, BlobsProtocol, Hash,
};
use n0_future::BufferedStreamExt;
use serde_json::json;
use tokio::{
    sync::{mpsc, oneshot, Mutex as TokioMutex, Semaphore},
    task::JoinHandle,
};

use crate::{
    access_policy::{mode_from_storage, mode_to_storage, AccessDecision, AccessPolicy},
    api::{
        CoreEvent, CoreEventSink, CoreLimits, ReceiveOutputSink, ReceiverRequest, RuntimeStatus,
        ShareMetadataInput, ShareResult, ShareSource, StoredTransfer, TicketInspection,
        TransferAccessMode, TransferMetadata,
    },
    approval::ApprovalService,
    error::VnidropError,
    event_hub::EventHub,
    filesystem::{
        collect_import_files_with_limits, default_collection_name, platform_path,
        read_stream_from_blocking_reader, validated_relative_string, wait_for_writer,
        write_stream_to_blocking_writer, AtomicOutputFile, TransferImport,
    },
    handshake::{HandshakeResponse, HandshakeService},
    logging::init_logging,
    repository::{Repository, TransferUpsert},
    secret::load_or_create_secret,
    ticket::{parse_transfer_ticket_with_limits, ParsedTransferTicket, VnidropTicket},
    transfer_state::{TransferDirection, TransferStatus},
    util::{non_empty, unique_transfer_id},
};

#[derive(uniffi::Object)]
pub struct VnidropCore {
    runtime: tokio::runtime::Runtime,
    inner: Arc<CoreInner>,
}

struct CoreInner {
    // Kotlin owns app lifecycle and platform file picking; this Rust object
    // owns the Iroh endpoint, blob store, transfer history, and byte streaming.
    endpoint: Endpoint,
    router: Router,
    store: FsStore,
    repository: Repository,
    event_hub: Arc<EventHub>,
    approval: ApprovalService,
    limits: CoreLimits,
    transfer_slots: Semaphore,
    access_policy: Arc<AccessPolicy>,
    active_transfers: TokioMutex<HashMap<u64, ActiveTransfer>>,
    // Newly imported shares retain a TempTag for the lifetime of this process.
    // Restored shares have no in-memory tag, but remain tracked so they can be
    // counted and explicitly revoked after a restart.
    active_shares: TokioMutex<HashMap<u64, Option<TempTag>>>,
    hash_to_transfer: TokioMutex<HashMap<String, u64>>,
    connection_endpoints: TokioMutex<HashMap<u64, String>>,
    provider_task: TokioMutex<Option<JoinHandle<()>>>,
    shutdown_started: AtomicBool,
}

struct ActiveTransfer {
    direction: TransferDirection,
    cancel: oneshot::Sender<()>,
}

enum ReceiveTarget {
    Directory(PathBuf),
    OutputSink(Arc<dyn ReceiveOutputSink>),
}

struct OutputSinkFile<'a> {
    sink: &'a dyn ReceiveOutputSink,
    relative_path: String,
    terminal: bool,
}

impl<'a> OutputSinkFile<'a> {
    fn start(sink: &'a dyn ReceiveOutputSink, relative_path: String) -> Result<Self> {
        sink.start_file(relative_path.clone())
            .map_err(|error| anyhow::anyhow!(error.to_string()))?;
        Ok(Self {
            sink,
            relative_path,
            terminal: false,
        })
    }

    fn write(&self, bytes: Vec<u8>) -> Result<()> {
        self.sink
            .write_chunk(self.relative_path.clone(), bytes)
            .map_err(|error| anyhow::anyhow!(error.to_string()))
    }

    fn finish(mut self) -> Result<()> {
        // finish_file is terminal even when the foreign implementation reports
        // an error; implementations must release their open resource before
        // returning so Rust never invokes two terminal callbacks.
        self.terminal = true;
        self.sink
            .finish_file(self.relative_path.clone())
            .map_err(|error| anyhow::anyhow!(error.to_string()))
    }
}

impl Drop for OutputSinkFile<'_> {
    fn drop(&mut self) {
        if !self.terminal {
            self.terminal = true;
            if let Err(error) = self.sink.abort_file(
                self.relative_path.clone(),
                "transfer interrupted before file completion".to_string(),
            ) {
                tracing::warn!(
                    %error,
                    relative_path = %self.relative_path,
                    "failed to abort receive output sink file"
                );
            }
        }
    }
}

#[uniffi::export]
impl VnidropCore {
    #[uniffi::constructor]
    pub fn initialize(
        app_data_dir: String,
        event_sink: Arc<dyn CoreEventSink>,
    ) -> Result<Arc<Self>, VnidropError> {
        Self::initialize_with_limits(app_data_dir, event_sink, CoreLimits::default())
    }

    #[uniffi::constructor]
    pub fn initialize_with_limits(
        app_data_dir: String,
        event_sink: Arc<dyn CoreEventSink>,
        limits: CoreLimits,
    ) -> Result<Arc<Self>, VnidropError> {
        limits.validate().map_err(VnidropError::initialization)?;
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .thread_name("vnidrop")
            .build()?;
        let app_data_dir = PathBuf::from(app_data_dir);
        let inner = runtime
            .block_on(CoreInner::start(app_data_dir, event_sink, limits))
            .map_err(VnidropError::initialization)?;
        Ok(Arc::new(Self { runtime, inner }))
    }

    pub fn status(&self) -> RuntimeStatus {
        self.runtime.block_on(self.inner.status())
    }

    pub fn share_files(
        &self,
        sources: Vec<ShareSource>,
        metadata: ShareMetadataInput,
    ) -> Result<ShareResult, VnidropError> {
        self.runtime
            .block_on(self.inner.share_files(sources, metadata))
            .map_err(VnidropError::transfer)
    }

    pub fn receive(
        &self,
        ticket: String,
        output_dir: String,
        receiver_name: Option<String>,
    ) -> Result<(), VnidropError> {
        if let Err(error) = parse_transfer_ticket_with_limits(&ticket, &self.inner.limits)
            .context("failed to parse transfer ticket")
        {
            self.runtime.block_on(async {
                self.inner.emit_endpoint(
                    "error",
                    "invalid-ticket",
                    json!({ "reason": error.to_string() }),
                );
                self.inner.event_hub.flush().await;
            });
            return Err(VnidropError::ticket(error));
        }
        let output_dir = platform_path(&output_dir).map_err(VnidropError::filesystem)?;
        self.runtime
            .block_on(self.inner.receive(ticket, output_dir, receiver_name))
            .map_err(VnidropError::transfer)
    }

    pub fn receive_with_output_sink(
        &self,
        ticket: String,
        output_sink: Arc<dyn ReceiveOutputSink>,
        receiver_name: Option<String>,
    ) -> Result<(), VnidropError> {
        if let Err(error) = parse_transfer_ticket_with_limits(&ticket, &self.inner.limits)
            .context("failed to parse transfer ticket")
        {
            self.runtime.block_on(async {
                self.inner.emit_endpoint(
                    "error",
                    "invalid-ticket",
                    json!({ "reason": error.to_string() }),
                );
                self.inner.event_hub.flush().await;
            });
            return Err(VnidropError::ticket(error));
        }
        self.runtime
            .block_on(
                self.inner
                    .receive_with_output_sink(ticket, output_sink, receiver_name),
            )
            .map_err(VnidropError::transfer)
    }

    pub fn cancel_transfer(&self, transfer_id: u64) -> Result<(), VnidropError> {
        self.runtime
            .block_on(self.inner.cancel_transfer(transfer_id))
            .map_err(VnidropError::transfer)
    }

    pub fn set_transfer_access_mode(
        &self,
        transfer_id: u64,
        mode: TransferAccessMode,
    ) -> Result<(), VnidropError> {
        self.runtime
            .block_on(self.inner.set_transfer_access_mode(transfer_id, mode))
            .map_err(VnidropError::permission)
    }

    pub fn approve_endpoint_for_transfer(
        &self,
        transfer_id: u64,
        endpoint_id: String,
    ) -> Result<(), VnidropError> {
        self.runtime
            .block_on(
                self.inner
                    .approve_endpoint_for_transfer(transfer_id, endpoint_id),
            )
            .map_err(VnidropError::permission)
    }

    pub fn list_receiver_requests(
        &self,
        transfer_id: u64,
    ) -> Result<Vec<ReceiverRequest>, VnidropError> {
        self.runtime
            .block_on(self.inner.repository.list_receiver_requests(transfer_id))
            .map_err(VnidropError::repository)
    }

    pub fn respond_receiver_request(
        &self,
        request_id: String,
        accepted: bool,
        reason: Option<String>,
    ) -> Result<(), VnidropError> {
        self.runtime
            .block_on(self.inner.approval.respond(request_id, accepted, reason))
            .map_err(VnidropError::permission)
    }

    pub fn list_transfers(&self) -> Result<Vec<StoredTransfer>, VnidropError> {
        self.runtime
            .block_on(self.inner.repository.list_transfers())
            .map_err(VnidropError::repository)
    }

    pub fn list_events(&self, transfer_id: Option<u64>) -> Result<Vec<CoreEvent>, VnidropError> {
        self.runtime
            .block_on(self.inner.list_events(transfer_id))
            .map_err(VnidropError::repository)
    }

    pub fn inspect_ticket(&self, ticket: String) -> Result<TicketInspection, VnidropError> {
        let parsed = parse_transfer_ticket_with_limits(&ticket, &self.inner.limits)
            .context("failed to parse transfer ticket")
            .map_err(VnidropError::ticket)?;
        Ok(TicketInspection {
            kind: if parsed.metadata.is_some() {
                "vnidrop".to_string()
            } else {
                "legacy".to_string()
            },
            blob_ticket: parsed.blob_ticket.to_string(),
            metadata: parsed.metadata,
        })
    }

    pub fn shutdown(&self) {
        self.runtime.block_on(self.inner.shutdown());
    }
}

impl CoreInner {
    async fn start(
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
        let mut restored_hashes = HashMap::new();
        let mut restored_active_shares = HashMap::new();
        for share in repository.list_active_shares().await? {
            let transfer_id = share.transfer_id;
            let valid_root = match Hash::from_str(&share.content_hash) {
                Ok(hash) => {
                    store.blobs().has(hash).await.unwrap_or(false)
                        && Collection::load(hash, store.as_ref()).await.is_ok()
                }
                Err(_) => false,
            };
            if !valid_root {
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
            }
            restored_hashes.insert(share.content_hash, transfer_id);
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
            active_transfers: TokioMutex::new(HashMap::new()),
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

    async fn status(&self) -> RuntimeStatus {
        RuntimeStatus {
            endpoint_id: self.endpoint.id().to_string(),
            addr: format!("{:?}", self.endpoint.addr()),
            active_transfers: self.active_transfers.lock().await.len() as u64,
            active_shares: self.active_shares.lock().await.len() as u64,
        }
    }

    async fn share_files(
        self: &Arc<Self>,
        sources: Vec<ShareSource>,
        metadata: ShareMetadataInput,
    ) -> Result<ShareResult> {
        let _permit = self
            .transfer_slots
            .acquire()
            .await
            .context("transfer limiter is closed")?;
        let transfer_id = metadata.transfer_id;
        if sources.is_empty() {
            anyhow::bail!("at least one source is required");
        }
        if sources.len() as u64 > self.limits.max_sources {
            anyhow::bail!(
                "source count {} exceeds limit {}",
                sources.len(),
                self.limits.max_sources
            );
        }
        self.limits
            .validate_metadata_text("transfer name", metadata.transfer_name.as_deref())?;
        self.limits
            .validate_metadata_text("sender name", metadata.sender_name.as_deref())?;
        self.repository
            .insert_transfer(TransferUpsert {
                transfer_id,
                peer_id: None,
                direction: TransferDirection::Send,
                status: TransferStatus::Importing,
                transfer_name: metadata.transfer_name.as_deref(),
                content_hash: None,
                ticket: None,
                file_count: 0,
                total_size: 0,
                access_mode: mode_to_storage(&metadata.access_mode),
            })
            .await?;
        let (cancel, mut cancelled) = oneshot::channel();
        self.active_transfers.lock().await.insert(
            transfer_id,
            ActiveTransfer {
                direction: TransferDirection::Send,
                cancel,
            },
        );
        let (result, was_cancelled) = tokio::select! {
            result = self.share_files_inner(sources, metadata) => (result, false),
            _ = &mut cancelled => (Err(anyhow::anyhow!("transfer cancelled")), true),
        };
        self.active_transfers.lock().await.remove(&transfer_id);
        if let Err(error) = &result {
            if was_cancelled {
                self.emit_transfer(transfer_id, "send", "lifecycle", "cancelled", json!({}));
            } else {
                self.emit_transfer(
                    transfer_id,
                    "send",
                    "error",
                    "failed",
                    json!({ "reason": error.to_string() }),
                );
                let _ = self
                    .repository
                    .transition_transfer_status(
                        transfer_id,
                        TransferStatus::Importing,
                        TransferStatus::Failed,
                    )
                    .await;
            }
        }
        result
    }

    async fn share_files_inner(
        self: &Arc<Self>,
        sources: Vec<ShareSource>,
        metadata: ShareMetadataInput,
    ) -> Result<ShareResult> {
        let access_mode = metadata.access_mode.clone();
        self.emit_transfer(
            metadata.transfer_id,
            "send",
            "import",
            "started",
            json!({ "source_count": sources.len() }),
        );
        let import = self.import_sources(metadata.transfer_id, sources).await?;
        let blob_ticket =
            BlobTicket::new(self.endpoint.addr(), import.root_hash, BlobFormat::HashSeq);
        let transfer_name = metadata
            .transfer_name
            .and_then(non_empty)
            .unwrap_or_else(|| import.default_name.clone());
        let ticket_metadata = TransferMetadata::new(
            metadata.transfer_id,
            transfer_name.clone(),
            metadata.sender_name,
            import.root_hash,
            import.file_count,
            import.total_size,
        );
        let ticket = VnidropTicket::new(blob_ticket.clone(), ticket_metadata)
            .encode()
            .context("failed to encode VniDrop transfer ticket")?;
        let content_hash = import.root_hash.to_string();

        // Persist the completed share before exposing it through the provider.
        // The remaining in-memory registrations are infallible and can be
        // reconstructed from SQLite if the process exits immediately after.
        self.repository
            .complete_share_import(TransferUpsert {
                transfer_id: metadata.transfer_id,
                peer_id: None,
                direction: TransferDirection::Send,
                status: TransferStatus::Sharing,
                transfer_name: Some(&transfer_name),
                content_hash: Some(&content_hash),
                ticket: Some(&ticket),
                file_count: import.file_count,
                total_size: import.total_size,
                access_mode: mode_to_storage(&access_mode),
            })
            .await?;
        self.hash_to_transfer
            .lock()
            .await
            .insert(content_hash, metadata.transfer_id);
        self.access_policy
            .set_mode(metadata.transfer_id, access_mode)
            .await;
        self.active_shares
            .lock()
            .await
            .insert(metadata.transfer_id, Some(import.tag));

        self.emit_transfer(
            metadata.transfer_id,
            "send",
            "ticket",
            "created",
            json!({
                "ticket": ticket,
                "hash": import.root_hash.to_string(),
                "total_size": import.total_size,
                "file_count": import.file_count,
            }),
        );

        Ok(ShareResult {
            transfer_id: metadata.transfer_id,
            ticket,
            blob_ticket: blob_ticket.to_string(),
            hash: import.root_hash.to_string(),
            transfer_name,
            file_count: import.file_count,
            total_size: import.total_size,
        })
    }

    async fn receive(
        self: &Arc<Self>,
        ticket: String,
        output_dir: PathBuf,
        receiver_name: Option<String>,
    ) -> Result<()> {
        self.receive_to_target(ticket, ReceiveTarget::Directory(output_dir), receiver_name)
            .await
    }

    async fn receive_with_output_sink(
        self: &Arc<Self>,
        ticket: String,
        output_sink: Arc<dyn ReceiveOutputSink>,
        receiver_name: Option<String>,
    ) -> Result<()> {
        self.receive_to_target(
            ticket,
            ReceiveTarget::OutputSink(output_sink),
            receiver_name,
        )
        .await
    }

    async fn receive_to_target(
        self: &Arc<Self>,
        ticket: String,
        target: ReceiveTarget,
        receiver_name: Option<String>,
    ) -> Result<()> {
        let _permit = self
            .transfer_slots
            .acquire()
            .await
            .context("transfer limiter is closed")?;
        let parsed = match parse_transfer_ticket_with_limits(&ticket, &self.limits)
            .context("failed to parse transfer ticket")
        {
            Ok(parsed) => parsed,
            Err(error) => {
                self.emit_endpoint(
                    "error",
                    "invalid-ticket",
                    json!({ "reason": error.to_string() }),
                );
                return Err(error);
            }
        };
        let transfer_id = parsed
            .metadata
            .as_ref()
            .map(|metadata| metadata.transfer_id)
            .unwrap_or_else(unique_transfer_id);
        self.persist_receive_start(transfer_id, &parsed, receiver_name.as_deref())
            .await?;
        // Cancellation is cooperative: it stops our receive future and marks
        // local state while lower-level Iroh work unwinds naturally.
        let (shutdown_tx, mut shutdown_rx) = oneshot::channel();
        self.active_transfers.lock().await.insert(
            transfer_id,
            ActiveTransfer {
                direction: TransferDirection::Receive,
                cancel: shutdown_tx,
            },
        );

        let (result, cancelled) = tokio::select! {
            result = self.receive_inner(transfer_id, parsed, target, receiver_name) => (result, false),
            _ = &mut shutdown_rx => (Err(anyhow::anyhow!("transfer cancelled")), true),
        };

        self.active_transfers.lock().await.remove(&transfer_id);
        if let Err(error) = &result {
            if cancelled {
                self.emit_transfer(transfer_id, "receive", "lifecycle", "cancelled", json!({}));
            } else {
                self.emit_transfer(
                    transfer_id,
                    "receive",
                    "error",
                    "failed",
                    json!({ "reason": error.to_string() }),
                );
                let _ = self
                    .repository
                    .transition_transfer_status(
                        transfer_id,
                        TransferStatus::Receiving,
                        TransferStatus::Failed,
                    )
                    .await;
            }
        }
        result
    }

    async fn receive_inner(
        self: &Arc<Self>,
        transfer_id: u64,
        parsed: ParsedTransferTicket,
        target: ReceiveTarget,
        receiver_name: Option<String>,
    ) -> Result<()> {
        if let ReceiveTarget::Directory(output_dir) = &target {
            tokio::fs::create_dir_all(output_dir).await?;
        }

        self.emit_transfer(transfer_id, "receive", "network", "connecting", json!({}));
        if let Some(metadata) = &parsed.metadata {
            self.request_transfer_approval(
                transfer_id,
                parsed.blob_ticket.addr().clone(),
                metadata,
                receiver_name.as_deref(),
            )
            .await?;
        }
        let connection = self
            .endpoint
            .connect(parsed.blob_ticket.addr().clone(), iroh_blobs::ALPN)
            .await?;
        self.emit_transfer(transfer_id, "receive", "network", "connected", json!({}));

        let hash_and_format = parsed.blob_ticket.hash_and_format();
        let (_hash_seq, sizes) =
            get_hash_seq_and_sizes(&connection, &hash_and_format.hash, 1024 * 1024 * 32, None)
                .await
                .context("failed to get file sizes")?;
        let total_size = sizes
            .iter()
            .try_fold(0u64, |total, size| total.checked_add(*size))
            .context("remote collection size overflow")?;
        let total_files = sizes.len().saturating_sub(1) as u64;
        if total_files > self.limits.max_collection_files {
            anyhow::bail!(
                "remote collection has {total_files} files, limit is {}",
                self.limits.max_collection_files
            );
        }
        if total_size > self.limits.max_total_bytes {
            anyhow::bail!(
                "remote collection size {total_size} exceeds limit {}",
                self.limits.max_total_bytes
            );
        }
        self.emit_transfer(
            transfer_id,
            "receive",
            "download",
            "found-collection",
            json!({ "total_files": total_files, "total_size": total_size }),
        );

        let get = self.store.remote().fetch(connection, hash_and_format);
        let mut stream = get.stream();
        while let Some(item) = stream.next().await {
            match item {
                GetProgressItem::Progress(downloaded) => {
                    self.emit_transfer(
                        transfer_id,
                        "receive",
                        "download",
                        "progress",
                        json!({ "downloaded": downloaded, "total_size": total_size }),
                    );
                }
                GetProgressItem::Done(_) => break,
                GetProgressItem::Error(error) => anyhow::bail!("download failed: {error}"),
            }
        }

        let collection = Collection::load(hash_and_format.hash, self.store.as_ref()).await?;
        self.export_collection(transfer_id, total_files, target, collection)
            .await?;
        self.repository
            .transition_transfer_status(
                transfer_id,
                TransferStatus::Receiving,
                TransferStatus::Done,
            )
            .await?;
        self.emit_transfer(transfer_id, "receive", "lifecycle", "done", json!({}));
        Ok(())
    }

    async fn persist_receive_start(
        &self,
        transfer_id: u64,
        parsed: &ParsedTransferTicket,
        receiver_name: Option<&str>,
    ) -> Result<()> {
        let peer_id = parsed.blob_ticket.addr().id.to_string();
        self.repository
            .start_receive(TransferUpsert {
                transfer_id,
                peer_id: Some(&peer_id),
                direction: TransferDirection::Receive,
                status: TransferStatus::Receiving,
                transfer_name: parsed
                    .metadata
                    .as_ref()
                    .map(|metadata| metadata.transfer_name.as_str()),
                content_hash: parsed
                    .metadata
                    .as_ref()
                    .map(|metadata| metadata.content_hash.as_str()),
                ticket: None,
                file_count: parsed
                    .metadata
                    .as_ref()
                    .map(|metadata| metadata.file_count)
                    .unwrap_or_default(),
                total_size: parsed
                    .metadata
                    .as_ref()
                    .map(|metadata| metadata.total_size)
                    .unwrap_or_default(),
                access_mode: mode_to_storage(&TransferAccessMode::ApprovalRequired),
            })
            .await?;
        let metadata_json =
            serde_json::to_value(&parsed.metadata).unwrap_or(serde_json::Value::Null);
        self.emit_transfer(
            transfer_id,
            "receive",
            "lifecycle",
            "started",
            json!({
                "metadata": metadata_json,
                "receiver_name": receiver_name,
            }),
        );
        Ok(())
    }

    async fn cancel_transfer(&self, transfer_id: u64) -> Result<()> {
        let mut active_transfers = self.active_transfers.lock().await;
        if let Some(direction) = active_transfers
            .get(&transfer_id)
            .map(|active| active.direction)
        {
            let expected = match direction {
                TransferDirection::Send => TransferStatus::Importing,
                TransferDirection::Receive => TransferStatus::Receiving,
            };
            self.repository
                .transition_transfer_status(transfer_id, expected, TransferStatus::Cancelled)
                .await?;
            let active = active_transfers
                .remove(&transfer_id)
                .ok_or_else(|| anyhow::anyhow!("active transfer registration disappeared"))?;
            drop(active_transfers);
            let _ = active.cancel.send(());
            self.emit_transfer(
                transfer_id,
                direction.as_str(),
                "lifecycle",
                "cancel-requested",
                json!({}),
            );
            return Ok(());
        }
        drop(active_transfers);

        let mut active_shares = self.active_shares.lock().await;
        if active_shares.contains_key(&transfer_id) {
            self.repository
                .transition_transfer_status(
                    transfer_id,
                    TransferStatus::Sharing,
                    TransferStatus::Stopped,
                )
                .await?;
            active_shares.remove(&transfer_id);
            drop(active_shares);
            self.hash_to_transfer
                .lock()
                .await
                .retain(|_, id| *id != transfer_id);
            self.access_policy.remove_transfer(transfer_id).await;
            self.emit_transfer(transfer_id, "send", "lifecycle", "share-stopped", json!({}));
            return Ok(());
        }
        anyhow::bail!("transfer not found")
    }

    async fn set_transfer_access_mode(
        &self,
        transfer_id: u64,
        mode: TransferAccessMode,
    ) -> Result<()> {
        self.repository
            .update_active_share_access_mode(transfer_id, mode_to_storage(&mode))
            .await?;
        self.access_policy.set_mode(transfer_id, mode.clone()).await;
        self.emit_transfer(
            transfer_id,
            "send",
            "access",
            "mode-updated",
            json!({ "mode": format!("{mode:?}") }),
        );
        Ok(())
    }

    async fn approve_endpoint_for_transfer(
        &self,
        transfer_id: u64,
        endpoint_id: String,
    ) -> Result<()> {
        self.access_policy
            .approve_endpoint(transfer_id, endpoint_id.clone())
            .await;
        self.emit_transfer(
            transfer_id,
            "send",
            "access",
            "endpoint-approved",
            json!({ "endpoint_id": endpoint_id }),
        );
        Ok(())
    }

    async fn request_transfer_approval(
        &self,
        local_transfer_id: u64,
        addr: iroh::EndpointAddr,
        metadata: &TransferMetadata,
        receiver_name: Option<&str>,
    ) -> Result<()> {
        self.emit_transfer(
            local_transfer_id,
            "receive",
            "handshake",
            "approval-requesting",
            json!({
                "sender_transfer_id": metadata.transfer_id,
                "metadata": metadata,
            }),
        );

        let client = HandshakeService::client(self.endpoint.clone(), addr);
        match client
            .request_transfer(metadata, receiver_name)
            .await
            .map_err(|error| anyhow::anyhow!("handshake request failed: {error}"))?
        {
            HandshakeResponse::Approved { expires_at, .. } => {
                self.emit_transfer(
                    local_transfer_id,
                    "receive",
                    "handshake",
                    "approval-granted",
                    json!({
                        "sender_transfer_id": metadata.transfer_id,
                        "expires_at": expires_at,
                    }),
                );
                Ok(())
            }
            HandshakeResponse::Denied { reason } => {
                anyhow::bail!("transfer request was denied by sender: {reason}")
            }
        }
    }

    async fn shutdown(&self) {
        if self.shutdown_started.swap(true, Ordering::SeqCst) {
            return;
        }
        self.emit_endpoint("shutdown", "service-shutdown", json!({}));
        // Flush before stopping the router so the app can show the shutdown
        // event even if the process exits soon after Compose disposes the core.
        self.event_hub.flush().await;
        if let Err(error) = self.router.shutdown().await {
            self.emit_endpoint(
                "shutdown",
                "router-error",
                json!({ "error": error.to_string() }),
            );
        }
        if let Some(task) = self.provider_task.lock().await.take() {
            task.abort();
            let _ = task.await;
        }
        self.event_hub.shutdown().await;
    }

    async fn import_sources(
        self: &Arc<Self>,
        transfer_id: u64,
        sources: Vec<ShareSource>,
    ) -> Result<TransferImport> {
        let files = collect_import_files_with_limits(sources, &self.limits)?;
        let default_name = default_collection_name(&files);
        let parallelism = num_cpus::get().min(8);
        let mut names_and_tags = n0_future::stream::iter(files)
            .map(|file| {
                let core = self.clone();
                async move {
                    let reader = file.source.open()?;
                    let stream = read_stream_from_blocking_reader(reader);
                    let import = core.store.add_stream(stream).await;
                    let (tag, size) = core
                        .consume_add_progress(
                            transfer_id,
                            file.collection_name.clone(),
                            import.stream().await,
                        )
                        .await?;
                    Result::<_>::Ok((file.collection_name, tag, size))
                }
            })
            .buffered_unordered(parallelism)
            .collect::<Vec<_>>()
            .await
            .into_iter()
            .collect::<Result<Vec<_>>>()?;

        names_and_tags.sort_by(|(a, _, _), (b, _, _)| a.cmp(b));
        let total_size = names_and_tags
            .iter()
            .try_fold(0u64, |total, (_, _, size)| total.checked_add(*size))
            .context("collection size overflow")?;
        if total_size > self.limits.max_total_bytes {
            anyhow::bail!(
                "collection size {total_size} exceeds transfer limit {}",
                self.limits.max_total_bytes
            );
        }
        let (collection, tags) = names_and_tags
            .into_iter()
            .map(|(name, tag, _)| ((name, tag.hash()), tag))
            .unzip::<_, _, Collection, Vec<_>>();
        let collection_tag = collection.clone().store(&self.store).await?;
        let root_hash = collection_tag.hash();
        let file_count = tags.len() as u64;
        drop(tags);

        self.emit_transfer(
            transfer_id,
            "send",
            "import",
            "done",
            json!({ "file_count": file_count, "total_size": total_size }),
        );

        Ok(TransferImport {
            tag: collection_tag,
            root_hash,
            total_size,
            file_count,
            default_name,
        })
    }

    async fn consume_add_progress(
        &self,
        transfer_id: u64,
        file_name: String,
        mut stream: impl futures_lite::Stream<Item = AddProgressItem> + Unpin,
    ) -> Result<(TempTag, u64)> {
        let mut size = 0;
        loop {
            let Some(item) = stream.next().await else {
                anyhow::bail!("import stream ended without a tag");
            };
            match item {
                AddProgressItem::Size(item_size) => {
                    if item_size > self.limits.max_total_bytes {
                        anyhow::bail!(
                            "file size {item_size} exceeds transfer limit {}",
                            self.limits.max_total_bytes
                        );
                    }
                    size = item_size;
                }
                AddProgressItem::CopyProgress(offset) => {
                    self.emit_transfer(
                        transfer_id,
                        "send",
                        "import",
                        "copy-progress",
                        json!({ "file_name": file_name, "offset": offset, "size": size }),
                    );
                }
                AddProgressItem::CopyDone => {
                    self.emit_transfer(
                        transfer_id,
                        "send",
                        "import",
                        "copy-done",
                        json!({ "file_name": file_name, "size": size }),
                    );
                }
                AddProgressItem::OutboardProgress(offset) => {
                    self.emit_transfer(
                        transfer_id,
                        "send",
                        "import",
                        "outboard-progress",
                        json!({ "file_name": file_name, "offset": offset }),
                    );
                }
                AddProgressItem::Done(tag) => return Ok((tag, size)),
                AddProgressItem::Error(error) => anyhow::bail!("import failed: {error}"),
            }
        }
    }

    async fn export_collection(
        &self,
        transfer_id: u64,
        total_files: u64,
        target: ReceiveTarget,
        collection: Collection,
    ) -> Result<()> {
        for (i, (name, hash)) in collection.iter().enumerate() {
            match &target {
                ReceiveTarget::Directory(output_dir) => {
                    self.export_blob_to_directory(
                        transfer_id,
                        total_files,
                        i as u64,
                        output_dir,
                        name.as_ref(),
                        *hash,
                    )
                    .await?;
                }
                ReceiveTarget::OutputSink(output_sink) => {
                    self.export_blob_to_sink(
                        transfer_id,
                        total_files,
                        i as u64,
                        output_sink.as_ref(),
                        name.as_ref(),
                        *hash,
                    )
                    .await?;
                }
            }
        }
        Ok(())
    }

    async fn export_blob_to_directory(
        &self,
        transfer_id: u64,
        total_files: u64,
        current_file_index: u64,
        output_dir: &Path,
        relative_path: &str,
        hash: Hash,
    ) -> Result<()> {
        if relative_path.len() as u64 > self.limits.max_path_bytes {
            anyhow::bail!(
                "output path exceeds {} bytes: {relative_path}",
                self.limits.max_path_bytes
            );
        }
        let (pending_file, writer) = AtomicOutputFile::create(output_dir, relative_path)?;
        let (tx, rx) = async_channel::bounded::<io::Result<Option<Bytes>>>(2);
        let writer_task = std::thread::spawn(move || write_stream_to_blocking_writer(writer, rx));
        let mut stream = self.store.export_ranges(hash, 0..u64::MAX).stream();
        let mut file_size = 0;
        let mut exported = 0;

        while let Some(item) = stream.next().await {
            match item {
                ExportRangesItem::Size(size) => file_size = size,
                ExportRangesItem::Data(leaf) => {
                    if leaf.offset != exported {
                        anyhow::bail!(
                            "export stream for {relative_path} yielded out-of-order data"
                        );
                    }
                    exported += leaf.data.len() as u64;
                    tx.send(Ok(Some(leaf.data)))
                        .await
                        .map_err(|_| anyhow::anyhow!("export writer closed for {relative_path}"))?;
                    self.emit_transfer(
                        transfer_id,
                        "receive",
                        "export",
                        "progress",
                        json!({
                            "total_files": total_files,
                            "current_file_index": current_file_index,
                            "file_name": relative_path,
                            "file_size": file_size,
                            "exported": exported,
                        }),
                    );
                }
                ExportRangesItem::Error(error) => {
                    anyhow::bail!("export failed for {relative_path}: {error}");
                }
            }
        }

        tx.send(Ok(None))
            .await
            .map_err(|_| anyhow::anyhow!("export writer closed for {relative_path}"))?;
        let writer = wait_for_writer(writer_task).await??;
        tokio::task::spawn_blocking(move || writer.sync_all()).await??;
        pending_file.commit()?;
        Ok(())
    }

    async fn export_blob_to_sink(
        &self,
        transfer_id: u64,
        total_files: u64,
        current_file_index: u64,
        output_sink: &dyn ReceiveOutputSink,
        relative_path: &str,
        hash: Hash,
    ) -> Result<()> {
        if relative_path.len() as u64 > self.limits.max_path_bytes {
            anyhow::bail!(
                "output path exceeds {} bytes: {relative_path}",
                self.limits.max_path_bytes
            );
        }
        let relative_path = validated_relative_string(relative_path)?;
        let output_file = OutputSinkFile::start(output_sink, relative_path.clone())?;
        let mut stream = self.store.export_ranges(hash, 0..u64::MAX).stream();
        let mut file_size = 0;
        let mut exported = 0;

        while let Some(item) = stream.next().await {
            match item {
                ExportRangesItem::Size(size) => file_size = size,
                ExportRangesItem::Data(leaf) => {
                    if leaf.offset != exported {
                        anyhow::bail!(
                            "export stream for {relative_path} yielded out-of-order data"
                        );
                    }
                    exported += leaf.data.len() as u64;
                    output_file.write(leaf.data.to_vec())?;
                    self.emit_transfer(
                        transfer_id,
                        "receive",
                        "export",
                        "progress",
                        json!({
                            "total_files": total_files,
                            "current_file_index": current_file_index,
                            "file_name": relative_path,
                            "file_size": file_size,
                            "exported": exported,
                        }),
                    );
                }
                ExportRangesItem::Error(error) => {
                    anyhow::bail!("export failed for {relative_path}: {error}");
                }
            }
        }

        output_file.finish()?;
        Ok(())
    }

    async fn spawn_provider_event_task(self: &Arc<Self>, mut rx: mpsc::Receiver<ProviderMessage>) {
        let core = self.clone();
        let task = tokio::spawn(async move {
            while let Some(message) = rx.recv().await {
                core.handle_provider_message(message).await;
            }
        });
        *self.provider_task.lock().await = Some(task);
    }

    async fn handle_provider_message(self: &Arc<Self>, message: ProviderMessage) {
        match message {
            ProviderMessage::ClientConnected(message) => {
                self.emit_endpoint(
                    "provider",
                    "client-connected",
                    json!({
                        "connection_id": message.inner.connection_id,
                        "endpoint_id": message.inner.endpoint_id.map(|id| id.to_string()),
                    }),
                );
                if let Some(endpoint_id) = message.inner.endpoint_id {
                    self.connection_endpoints
                        .lock()
                        .await
                        .insert(message.inner.connection_id, endpoint_id.to_string());
                }
                let _ = message.tx.send(Ok(())).await;
            }
            ProviderMessage::ClientConnectedNotify(message) => {
                self.emit_endpoint(
                    "provider",
                    "client-connected",
                    json!({
                        "connection_id": message.inner.connection_id,
                        "endpoint_id": message.inner.endpoint_id.map(|id| id.to_string()),
                    }),
                );
                if let Some(endpoint_id) = message.inner.endpoint_id {
                    self.connection_endpoints
                        .lock()
                        .await
                        .insert(message.inner.connection_id, endpoint_id.to_string());
                }
            }
            ProviderMessage::ConnectionClosed(message) => {
                self.connection_endpoints
                    .lock()
                    .await
                    .remove(&message.inner.connection_id);
                self.emit_endpoint(
                    "provider",
                    "connection-closed",
                    json!({ "connection_id": message.inner.connection_id }),
                );
            }
            ProviderMessage::GetRequestReceived(message) => {
                let transfer_id = self.transfer_for_hash(message.inner.request.hash).await;
                if let Some(transfer_id) = transfer_id {
                    let decision = self
                        .access_decision(transfer_id, message.inner.connection_id)
                        .await;
                    if let AccessDecision::Deny { reason } = decision {
                        self.emit_transfer(
                            transfer_id,
                            "send",
                            "access",
                            "request-denied",
                            json!({
                                "connection_id": message.inner.connection_id,
                                "request_id": message.inner.request_id,
                                "reason": reason,
                            }),
                        );
                        let _ = message
                            .tx
                            .send(Err(iroh_blobs::provider::events::AbortReason::Permission))
                            .await;
                        return;
                    }
                    self.track_request_updates(
                        transfer_id,
                        message.inner.connection_id,
                        message.inner.request_id,
                        message.rx,
                    );
                }
                let _ = message.tx.send(Ok(())).await;
            }
            ProviderMessage::GetRequestReceivedNotify(message) => {
                if let Some(transfer_id) = self.transfer_for_hash(message.inner.request.hash).await
                {
                    self.track_request_updates(
                        transfer_id,
                        message.inner.connection_id,
                        message.inner.request_id,
                        message.rx,
                    );
                }
            }
            ProviderMessage::GetManyRequestReceived(message) => {
                let transfer_id = self
                    .transfer_for_any_hash(&message.inner.request.hashes)
                    .await;
                if let Some(transfer_id) = transfer_id {
                    let decision = self
                        .access_decision(transfer_id, message.inner.connection_id)
                        .await;
                    if let AccessDecision::Deny { reason } = decision {
                        self.emit_transfer(
                            transfer_id,
                            "send",
                            "access",
                            "request-denied",
                            json!({
                                "connection_id": message.inner.connection_id,
                                "request_id": message.inner.request_id,
                                "reason": reason,
                            }),
                        );
                        let _ = message
                            .tx
                            .send(Err(iroh_blobs::provider::events::AbortReason::Permission))
                            .await;
                        return;
                    }
                    self.track_request_updates(
                        transfer_id,
                        message.inner.connection_id,
                        message.inner.request_id,
                        message.rx,
                    );
                }
                let _ = message.tx.send(Ok(())).await;
            }
            ProviderMessage::GetManyRequestReceivedNotify(message) => {
                if let Some(transfer_id) = self
                    .transfer_for_any_hash(&message.inner.request.hashes)
                    .await
                {
                    self.track_request_updates(
                        transfer_id,
                        message.inner.connection_id,
                        message.inner.request_id,
                        message.rx,
                    );
                }
            }
            ProviderMessage::ObserveRequestReceived(message) => {
                self.emit_endpoint(
                    "provider",
                    "observe-request",
                    json!({
                        "connection_id": message.inner.connection_id,
                        "request_id": message.inner.request_id,
                    }),
                );
                let _ = message.tx.send(Ok(())).await;
            }
            ProviderMessage::ObserveRequestReceivedNotify(message) => {
                self.emit_endpoint(
                    "provider",
                    "observe-request",
                    json!({
                        "connection_id": message.inner.connection_id,
                        "request_id": message.inner.request_id,
                    }),
                );
            }
            ProviderMessage::Throttle(message) => {
                self.emit_endpoint(
                    "provider",
                    "throttle-request",
                    json!({
                        "connection_id": message.inner.connection_id,
                        "request_id": message.inner.request_id,
                        "size": message.inner.size,
                    }),
                );
                let _ = message.tx.send(Ok(())).await;
            }
            other => {
                self.emit_endpoint(
                    "provider",
                    "provider-message",
                    json!({ "debug": format!("{other:?}") }),
                );
            }
        }
    }

    async fn transfer_for_hash(&self, hash: Hash) -> Option<u64> {
        self.hash_to_transfer
            .lock()
            .await
            .get(&hash.to_string())
            .copied()
    }

    async fn transfer_for_any_hash(&self, hashes: &[Hash]) -> Option<u64> {
        let map = self.hash_to_transfer.lock().await;
        hashes
            .iter()
            .find_map(|hash| map.get(&hash.to_string()).copied())
    }

    async fn access_decision(&self, transfer_id: u64, connection_id: u64) -> AccessDecision {
        let endpoint_id = self
            .connection_endpoints
            .lock()
            .await
            .get(&connection_id)
            .cloned();
        self.access_policy
            .decide(transfer_id, endpoint_id.as_deref())
            .await
    }

    fn track_request_updates(
        self: &Arc<Self>,
        transfer_id: u64,
        connection_id: u64,
        request_id: u64,
        mut rx: irpc::channel::mpsc::Receiver<RequestUpdate>,
    ) {
        // Request update tasks are tied to individual provider streams.  Router
        // shutdown closes those streams; only the long-lived provider receiver
        // is tracked directly for explicit shutdown.
        let core = self.clone();
        tokio::spawn(async move {
            while let Ok(Some(update)) = rx.recv().await {
                match update {
                    RequestUpdate::Started(started) => core.emit_transfer(
                        transfer_id,
                        "send",
                        "transfer",
                        "started",
                        json!({
                            "connection_id": connection_id,
                            "request_id": request_id,
                            "hash": started.hash.to_string(),
                            "size": started.size,
                            "index": started.index,
                        }),
                    ),
                    RequestUpdate::Progress(progress) => core.emit_transfer(
                        transfer_id,
                        "send",
                        "transfer",
                        "progress",
                        json!({
                            "connection_id": connection_id,
                            "request_id": request_id,
                            "end_offset": progress.end_offset,
                        }),
                    ),
                    RequestUpdate::Completed(_) => core.emit_transfer(
                        transfer_id,
                        "send",
                        "transfer",
                        "completed",
                        json!({ "connection_id": connection_id, "request_id": request_id }),
                    ),
                    RequestUpdate::Aborted(_) => core.emit_transfer(
                        transfer_id,
                        "send",
                        "transfer",
                        "aborted",
                        json!({ "connection_id": connection_id, "request_id": request_id }),
                    ),
                }
            }
        });
    }

    fn emit_endpoint(&self, phase: &str, kind: &str, data: serde_json::Value) {
        self.event_hub.emit_endpoint(phase, kind, data);
    }

    fn emit_transfer(
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

    async fn list_events(&self, transfer_id: Option<u64>) -> Result<Vec<CoreEvent>> {
        self.event_hub.flush().await;
        self.repository
            .list_events(transfer_id, self.limits.max_events)
            .await
    }
}
