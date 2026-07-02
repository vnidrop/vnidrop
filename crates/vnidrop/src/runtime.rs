use std::{
    collections::HashMap,
    fs::File,
    io,
    path::{Path, PathBuf},
    sync::{Arc, Mutex},
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
use tokio::sync::{mpsc, oneshot, Mutex as TokioMutex};

use crate::{
    access_policy::{AccessDecision, AccessPolicy},
    api::{
        CoreEvent, CoreEventSink, RuntimeStatus, ShareMetadataInput, ShareResult, ShareSource,
        StoredTransfer, TicketInspection, TransferAccessMode, TransferMetadata,
    },
    error::VnidropError,
    filesystem::{
        collect_import_files, default_collection_name, read_stream_from_blocking_reader,
        safe_output_path, wait_for_writer, write_stream_to_blocking_writer, TransferImport,
    },
    logging::init_logging,
    repository::Repository,
    secret::load_or_create_secret,
    ticket::{parse_transfer_ticket, ParsedTransferTicket, VnidropTicket},
    util::{non_empty, now_ms, unique_transfer_id},
};

#[derive(uniffi::Object)]
pub struct VnidropCore {
    runtime: tokio::runtime::Runtime,
    inner: Arc<CoreInner>,
}

struct CoreInner {
    endpoint: Endpoint,
    router: Router,
    store: FsStore,
    repository: Repository,
    access_policy: Arc<AccessPolicy>,
    event_sink: Arc<dyn CoreEventSink>,
    active_transfers: TokioMutex<HashMap<u64, oneshot::Sender<()>>>,
    active_shares: TokioMutex<HashMap<u64, TempTag>>,
    hash_to_transfer: TokioMutex<HashMap<String, u64>>,
    connection_endpoints: TokioMutex<HashMap<u64, String>>,
    sequence: Mutex<u64>,
}

#[uniffi::export]
impl VnidropCore {
    #[uniffi::constructor]
    pub fn initialize(
        app_data_dir: String,
        event_sink: Arc<dyn CoreEventSink>,
    ) -> Result<Arc<Self>, VnidropError> {
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .thread_name("vnidrop")
            .build()?;
        let app_data_dir = PathBuf::from(app_data_dir);
        let inner = runtime.block_on(CoreInner::start(app_data_dir, event_sink))?;
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
            .map_err(Into::into)
    }

    pub fn receive(
        &self,
        ticket: String,
        output_dir: String,
        receiver_name: Option<String>,
    ) -> Result<(), VnidropError> {
        self.runtime
            .block_on(
                self.inner
                    .receive(ticket, PathBuf::from(output_dir), receiver_name),
            )
            .map_err(Into::into)
    }

    pub fn cancel_transfer(&self, transfer_id: u64) -> Result<(), VnidropError> {
        self.runtime
            .block_on(self.inner.cancel_transfer(transfer_id))
            .map_err(Into::into)
    }

    pub fn set_transfer_access_mode(
        &self,
        transfer_id: u64,
        mode: TransferAccessMode,
    ) -> Result<(), VnidropError> {
        self.runtime
            .block_on(self.inner.set_transfer_access_mode(transfer_id, mode))
            .map_err(Into::into)
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
            .map_err(Into::into)
    }

    pub fn list_transfers(&self) -> Result<Vec<StoredTransfer>, VnidropError> {
        self.runtime
            .block_on(self.inner.repository.list_transfers())
            .map_err(Into::into)
    }

    pub fn list_events(&self, transfer_id: Option<u64>) -> Result<Vec<CoreEvent>, VnidropError> {
        self.runtime
            .block_on(self.inner.repository.list_events(transfer_id))
            .map_err(Into::into)
    }

    pub fn inspect_ticket(&self, ticket: String) -> Result<TicketInspection, VnidropError> {
        let parsed = parse_transfer_ticket(&ticket).context("failed to parse transfer ticket")?;
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
    async fn start(app_data_dir: PathBuf, event_sink: Arc<dyn CoreEventSink>) -> Result<Arc<Self>> {
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

        let (events, event_rx) = EventSender::channel(128, EventMask::ALL_READONLY);
        let blobs = BlobsProtocol::new(&store, Some(events));
        let router = Router::builder(endpoint.clone())
            .accept(iroh_blobs::ALPN, blobs)
            .spawn();

        let inner = Arc::new(Self {
            endpoint,
            router,
            store,
            repository,
            access_policy: AccessPolicy::new(),
            event_sink,
            active_transfers: TokioMutex::new(HashMap::new()),
            active_shares: TokioMutex::new(HashMap::new()),
            hash_to_transfer: TokioMutex::new(HashMap::new()),
            connection_endpoints: TokioMutex::new(HashMap::new()),
            sequence: Mutex::new(1),
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
        inner.spawn_provider_event_task(event_rx);
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
        if sources.is_empty() {
            anyhow::bail!("at least one source is required");
        }

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

        self.hash_to_transfer
            .lock()
            .await
            .insert(import.root_hash.to_string(), metadata.transfer_id);
        self.access_policy
            .set_mode(metadata.transfer_id, TransferAccessMode::Public)
            .await;
        self.active_shares
            .lock()
            .await
            .insert(metadata.transfer_id, import.tag);
        self.repository
            .upsert_transfer(
                metadata.transfer_id,
                "send",
                "sharing",
                Some(&transfer_name),
                Some(&import.root_hash.to_string()),
                Some(&ticket),
                import.file_count,
                import.total_size,
            )
            .await?;

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
        let parsed = parse_transfer_ticket(&ticket).context("failed to parse transfer ticket")?;
        let transfer_id = parsed
            .metadata
            .as_ref()
            .map(|metadata| metadata.transfer_id)
            .unwrap_or_else(unique_transfer_id);
        let (shutdown_tx, mut shutdown_rx) = oneshot::channel();
        self.active_transfers
            .lock()
            .await
            .insert(transfer_id, shutdown_tx);

        let result = tokio::select! {
            result = self.receive_inner(transfer_id, parsed, output_dir, receiver_name) => result,
            _ = &mut shutdown_rx => Err(anyhow::anyhow!("transfer cancelled")),
        };

        self.active_transfers.lock().await.remove(&transfer_id);
        result
    }

    async fn receive_inner(
        self: &Arc<Self>,
        transfer_id: u64,
        parsed: ParsedTransferTicket,
        output_dir: PathBuf,
        receiver_name: Option<String>,
    ) -> Result<()> {
        let metadata_json = serde_json::to_value(&parsed.metadata).unwrap_or_else(|_| json!(null));
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
        self.repository
            .upsert_transfer(
                transfer_id,
                "receive",
                "receiving",
                parsed
                    .metadata
                    .as_ref()
                    .map(|metadata| metadata.transfer_name.as_str()),
                parsed
                    .metadata
                    .as_ref()
                    .map(|metadata| metadata.content_hash.as_str()),
                None,
                parsed
                    .metadata
                    .as_ref()
                    .map(|metadata| metadata.file_count)
                    .unwrap_or_default(),
                parsed
                    .metadata
                    .as_ref()
                    .map(|metadata| metadata.total_size)
                    .unwrap_or_default(),
            )
            .await?;
        tokio::fs::create_dir_all(&output_dir).await?;

        self.emit_transfer(transfer_id, "receive", "network", "connecting", json!({}));
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
        let total_size = sizes.iter().copied().sum::<u64>();
        let total_files = sizes.len().saturating_sub(1) as u64;
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
        self.export_collection(transfer_id, total_files, output_dir, collection)
            .await?;
        self.repository
            .update_transfer_status(transfer_id, "done")
            .await?;
        self.emit_transfer(transfer_id, "receive", "lifecycle", "done", json!({}));
        Ok(())
    }

    async fn cancel_transfer(&self, transfer_id: u64) -> Result<()> {
        if let Some(tx) = self.active_transfers.lock().await.remove(&transfer_id) {
            let _ = tx.send(());
            self.emit_transfer(
                transfer_id,
                "app",
                "lifecycle",
                "cancel-requested",
                json!({}),
            );
            self.repository
                .update_transfer_status(transfer_id, "cancelled")
                .await?;
            return Ok(());
        }
        if self
            .active_shares
            .lock()
            .await
            .remove(&transfer_id)
            .is_some()
        {
            self.hash_to_transfer
                .lock()
                .await
                .retain(|_, id| *id != transfer_id);
            self.access_policy.remove_transfer(transfer_id).await;
            self.repository
                .update_transfer_status(transfer_id, "stopped")
                .await?;
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

    async fn shutdown(&self) {
        self.emit_endpoint("shutdown", "service-shutdown", json!({}));
        if let Err(error) = self.router.shutdown().await {
            self.emit_endpoint(
                "shutdown",
                "router-error",
                json!({ "error": error.to_string() }),
            );
        }
    }

    async fn import_sources(
        self: &Arc<Self>,
        transfer_id: u64,
        sources: Vec<ShareSource>,
    ) -> Result<TransferImport> {
        let files = collect_import_files(sources)?;
        let default_name = default_collection_name(&files);
        let parallelism = num_cpus::get().min(8);
        let mut names_and_tags = n0_future::stream::iter(files)
            .map(|file| {
                let core = self.clone();
                async move {
                    let reader = File::open(&file.path)
                        .with_context(|| format!("failed to open {}", file.path.display()))?;
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
        let total_size = names_and_tags.iter().map(|(_, _, size)| *size).sum::<u64>();
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
                AddProgressItem::Size(item_size) => size = item_size,
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
        output_dir: PathBuf,
        collection: Collection,
    ) -> Result<()> {
        for (i, (name, hash)) in collection.iter().enumerate() {
            self.export_blob(
                transfer_id,
                total_files,
                i as u64,
                &output_dir,
                name.as_ref(),
                *hash,
            )
            .await?;
        }
        Ok(())
    }

    async fn export_blob(
        &self,
        transfer_id: u64,
        total_files: u64,
        current_file_index: u64,
        output_dir: &Path,
        relative_path: &str,
        hash: Hash,
    ) -> Result<()> {
        let target = safe_output_path(output_dir, relative_path)?;
        if let Some(parent) = target.parent() {
            tokio::fs::create_dir_all(parent).await?;
        }
        let (tx, rx) = async_channel::bounded::<io::Result<Option<Bytes>>>(2);
        let writer = File::create(&target)
            .with_context(|| format!("failed to create {}", target.display()))?;
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
        wait_for_writer(writer_task).await??;
        Ok(())
    }

    fn spawn_provider_event_task(self: &Arc<Self>, mut rx: mpsc::Receiver<ProviderMessage>) {
        let core = self.clone();
        tokio::spawn(async move {
            while let Some(message) = rx.recv().await {
                core.handle_provider_message(message).await;
            }
        });
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
        self.emit("endpoint", None, None, phase, kind, data);
    }

    fn emit_transfer(
        &self,
        transfer_id: u64,
        direction: &str,
        phase: &str,
        kind: &str,
        data: serde_json::Value,
    ) {
        self.emit(
            "transfer",
            Some(transfer_id),
            Some(direction.to_string()),
            phase,
            kind,
            data,
        );
    }

    fn emit(
        &self,
        scope: &str,
        transfer_id: Option<u64>,
        direction: Option<String>,
        phase: &str,
        kind: &str,
        data: serde_json::Value,
    ) {
        let timestamp = now_ms();
        let mut sequence = self.sequence.lock().expect("event sequence lock poisoned");
        let id = format!("{timestamp}-{}", *sequence);
        *sequence += 1;
        drop(sequence);
        let event = CoreEvent {
            id,
            timestamp,
            scope: scope.to_string(),
            transfer_id,
            direction,
            phase: phase.to_string(),
            kind: kind.to_string(),
            data_json: data.to_string(),
        };
        let repository = self.repository.clone();
        let event_for_repository = event.clone();
        tokio::spawn(async move {
            if let Err(error) = repository.insert_event(&event_for_repository).await {
                tracing::warn!(%error, "failed to persist core event");
            }
        });
        self.event_sink.on_event(event);
    }
}
