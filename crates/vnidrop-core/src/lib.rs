use std::{
    collections::HashMap,
    fs::File,
    io::{self, Read, Write},
    path::{Component, Path, PathBuf},
    str::FromStr,
    sync::{Arc, Mutex},
    time::{SystemTime, UNIX_EPOCH},
};

use anyhow::{Context, Result};
use bytes::Bytes;
use data_encoding::{BASE64URL_NOPAD, HEXLOWER};
use futures_lite::StreamExt as _;
use iroh::{
    endpoint::presets,
    protocol::Router,
    Endpoint, SecretKey,
};
use iroh_blobs::{
    api::{
        blobs::AddProgressItem,
        proto::ExportRangesItem,
        remote::GetProgressItem,
        TempTag,
    },
    format::collection::Collection,
    get::request::get_hash_seq_and_sizes,
    provider::events::{EventMask, EventSender, ProviderMessage, RequestUpdate},
    store::fs::FsStore,
    ticket::BlobTicket,
    BlobFormat, BlobsProtocol, Hash,
};
use n0_future::BufferedStreamExt;
use serde::{Deserialize, Serialize};
use serde_json::json;
use tokio::sync::{mpsc, oneshot, Mutex as TokioMutex};

const VNIDROP_TICKET_PREFIX: &str = "vnd1:";
const STREAM_BUFFER_LEN: usize = 1024 * 1024;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum VnidropError {
    #[error("{reason}")]
    Generic { reason: String },
}

impl From<anyhow::Error> for VnidropError {
    fn from(error: anyhow::Error) -> Self {
        Self::Generic {
            reason: error.to_string(),
        }
    }
}

impl From<io::Error> for VnidropError {
    fn from(error: io::Error) -> Self {
        Self::Generic {
            reason: error.to_string(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct CoreEvent {
    pub id: String,
    pub timestamp: i64,
    pub scope: String,
    pub transfer_id: Option<u64>,
    pub direction: Option<String>,
    pub phase: String,
    pub kind: String,
    pub data_json: String,
}

#[uniffi::export(with_foreign)]
pub trait CoreEventSink: Send + Sync {
    fn on_event(&self, event: CoreEvent);
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct RuntimeStatus {
    pub endpoint_id: String,
    pub addr: String,
    pub active_transfers: u64,
    pub active_shares: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Enum)]
pub enum SourceKind {
    Path,
    AndroidContentUri,
    IosSecurityScopedUrl,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct ShareSource {
    pub kind: SourceKind,
    pub value: String,
    pub display_name: Option<String>,
    pub is_directory: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct ShareMetadataInput {
    pub transfer_id: u64,
    pub transfer_name: Option<String>,
    pub sender_name: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct ShareResult {
    pub transfer_id: u64,
    pub ticket: String,
    pub blob_ticket: String,
    pub hash: String,
    pub transfer_name: String,
    pub file_count: u64,
    pub total_size: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct TransferMetadata {
    pub version: u8,
    pub transfer_id: u64,
    pub transfer_name: String,
    pub sender_name: Option<String>,
    pub created_at: i64,
    pub content_hash: String,
    pub file_count: u64,
    pub total_size: u64,
}

impl TransferMetadata {
    fn new(
        transfer_id: u64,
        transfer_name: impl Into<String>,
        sender_name: Option<String>,
        content_hash: Hash,
        file_count: u64,
        total_size: u64,
    ) -> Self {
        Self {
            version: 1,
            transfer_id,
            transfer_name: transfer_name.into(),
            sender_name: sender_name.and_then(non_empty),
            created_at: now_ms(),
            content_hash: content_hash.to_string(),
            file_count,
            total_size,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct VnidropTicket {
    version: u8,
    blob_ticket: String,
    metadata: TransferMetadata,
}

impl VnidropTicket {
    fn new(blob_ticket: BlobTicket, metadata: TransferMetadata) -> Self {
        Self {
            version: 1,
            blob_ticket: blob_ticket.to_string(),
            metadata,
        }
    }

    fn encode(&self) -> Result<String> {
        let bytes = serde_json::to_vec(self)?;
        Ok(format!(
            "{VNIDROP_TICKET_PREFIX}{}",
            BASE64URL_NOPAD.encode(&bytes)
        ))
    }

    fn decode(value: &str) -> Result<Self> {
        let encoded = value
            .strip_prefix(VNIDROP_TICKET_PREFIX)
            .context("not a VniDrop ticket")?;
        let bytes = BASE64URL_NOPAD
            .decode(encoded.as_bytes())
            .context("invalid VniDrop ticket encoding")?;
        serde_json::from_slice(&bytes).context("invalid VniDrop ticket payload")
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct TicketInspection {
    pub kind: String,
    pub blob_ticket: String,
    pub metadata: Option<TransferMetadata>,
}

#[derive(Debug, Clone)]
struct ParsedTransferTicket {
    blob_ticket: BlobTicket,
    metadata: Option<TransferMetadata>,
}

fn parse_transfer_ticket(value: &str) -> Result<ParsedTransferTicket> {
    if value.starts_with(VNIDROP_TICKET_PREFIX) {
        let ticket = VnidropTicket::decode(value)?;
        let blob_ticket = BlobTicket::from_str(&ticket.blob_ticket)
            .context("invalid BlobTicket inside VniDrop ticket")?;
        return Ok(ParsedTransferTicket {
            blob_ticket,
            metadata: Some(ticket.metadata),
        });
    }

    let blob_ticket = BlobTicket::from_str(value).context("invalid BlobTicket")?;
    Ok(ParsedTransferTicket {
        blob_ticket,
        metadata: None,
    })
}

#[derive(Debug)]
struct TransferImport {
    tag: TempTag,
    root_hash: Hash,
    total_size: u64,
    file_count: u64,
    default_name: String,
}

#[derive(Debug)]
struct ImportSourceFile {
    path: PathBuf,
    collection_name: String,
}

#[derive(uniffi::Object)]
pub struct VnidropCore {
    runtime: tokio::runtime::Runtime,
    inner: Arc<CoreInner>,
}

struct CoreInner {
    endpoint: Endpoint,
    router: Router,
    store: FsStore,
    event_sink: Arc<dyn CoreEventSink>,
    active_transfers: TokioMutex<HashMap<u64, oneshot::Sender<()>>>,
    active_shares: TokioMutex<HashMap<u64, TempTag>>,
    hash_to_transfer: TokioMutex<HashMap<String, u64>>,
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
            .thread_name("vnidrop-core")
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
            .block_on(self.inner.receive(ticket, PathBuf::from(output_dir), receiver_name))
            .map_err(Into::into)
    }

    pub fn cancel_transfer(&self, transfer_id: u64) -> Result<(), VnidropError> {
        self.runtime
            .block_on(self.inner.cancel_transfer(transfer_id))
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
    async fn start(
        app_data_dir: PathBuf,
        event_sink: Arc<dyn CoreEventSink>,
    ) -> Result<Arc<Self>> {
        tokio::fs::create_dir_all(&app_data_dir).await?;
        let secret_key = load_or_create_secret(&app_data_dir).await?;
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
            event_sink,
            active_transfers: TokioMutex::new(HashMap::new()),
            active_shares: TokioMutex::new(HashMap::new()),
            hash_to_transfer: TokioMutex::new(HashMap::new()),
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
        let blob_ticket = BlobTicket::new(self.endpoint.addr(), import.root_hash, BlobFormat::HashSeq);
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
        self.active_shares
            .lock()
            .await
            .insert(metadata.transfer_id, import.tag);

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
            return Ok(());
        }
        if self.active_shares.lock().await.remove(&transfer_id).is_some() {
            self.hash_to_transfer
                .lock()
                .await
                .retain(|_, id| *id != transfer_id);
            self.emit_transfer(transfer_id, "send", "lifecycle", "share-stopped", json!({}));
            return Ok(());
        }
        anyhow::bail!("transfer not found")
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
                        .consume_add_progress(transfer_id, file.collection_name.clone(), import.stream().await)
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
                        anyhow::bail!("export stream for {relative_path} yielded out-of-order data");
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
            }
            ProviderMessage::GetRequestReceived(message) => {
                let transfer_id = self.transfer_for_hash(message.inner.request.hash).await;
                if let Some(transfer_id) = transfer_id {
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
                if let Some(transfer_id) = self.transfer_for_hash(message.inner.request.hash).await {
                    self.track_request_updates(
                        transfer_id,
                        message.inner.connection_id,
                        message.inner.request_id,
                        message.rx,
                    );
                }
            }
            ProviderMessage::GetManyRequestReceived(message) => {
                let transfer_id = self.transfer_for_any_hash(&message.inner.request.hashes).await;
                if let Some(transfer_id) = transfer_id {
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
                if let Some(transfer_id) = self.transfer_for_any_hash(&message.inner.request.hashes).await {
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
        self.event_sink.on_event(CoreEvent {
            id,
            timestamp,
            scope: scope.to_string(),
            transfer_id,
            direction,
            phase: phase.to_string(),
            kind: kind.to_string(),
            data_json: data.to_string(),
        });
    }
}

fn collect_import_files(sources: Vec<ShareSource>) -> Result<Vec<ImportSourceFile>> {
    let mut files = Vec::new();
    for source in sources {
        match source.kind {
            SourceKind::Path | SourceKind::IosSecurityScopedUrl => {
                let path = source_path(&source)?;
                let display_name = source
                    .display_name
                    .clone()
                    .and_then(non_empty)
                    .or_else(|| path.file_name().and_then(|name| name.to_str()).map(ToOwned::to_owned))
                    .unwrap_or_else(|| "transfer".to_string());
                if source.is_directory || path.is_dir() {
                    collect_dir_files(&path, &display_name, &mut files)?;
                } else {
                    files.push(ImportSourceFile {
                        path,
                        collection_name: validated_relative_string(&display_name)?,
                    });
                }
            }
            SourceKind::AndroidContentUri => {
                anyhow::bail!(
                    "Android content URI streaming needs platform file descriptor glue before it can be imported without copying"
                );
            }
        }
    }
    if files.is_empty() {
        anyhow::bail!("no files found in selected sources");
    }
    Ok(files)
}

fn source_path(source: &ShareSource) -> Result<PathBuf> {
    if matches!(source.kind, SourceKind::IosSecurityScopedUrl) && source.value.starts_with("file://") {
        let without_scheme = source.value.trim_start_matches("file://");
        return Ok(PathBuf::from(percent_decode_file_url_path(without_scheme)?));
    }
    Ok(PathBuf::from(&source.value))
}

fn collect_dir_files(root: &Path, display_name: &str, files: &mut Vec<ImportSourceFile>) -> Result<()> {
    for entry in walkdir::WalkDir::new(root).follow_links(false) {
        let entry = entry?;
        if !entry.file_type().is_file() {
            continue;
        }
        let relative = entry
            .path()
            .strip_prefix(root)
            .context("failed to compute relative path")?;
        let collection_name = path_to_string(Path::new(display_name).join(relative), true)?;
        files.push(ImportSourceFile {
            path: entry.path().to_path_buf(),
            collection_name,
        });
    }
    Ok(())
}

fn default_collection_name(files: &[ImportSourceFile]) -> String {
    files
        .first()
        .and_then(|file| file.collection_name.split('/').next())
        .filter(|name| !name.is_empty())
        .unwrap_or("transfer")
        .to_string()
}

fn safe_output_path(output_dir: &Path, relative_path: &str) -> Result<PathBuf> {
    let relative = Path::new(relative_path);
    path_to_string(relative, true)?;
    Ok(output_dir.join(relative))
}

fn read_stream_from_blocking_reader<R>(
    mut reader: R,
) -> impl futures::Stream<Item = io::Result<Bytes>> + Send + Sync + 'static
where
    R: Read + Send + 'static,
{
    let (tx, rx) = async_channel::bounded(2);
    std::thread::spawn(move || {
        let mut buffer = vec![0; STREAM_BUFFER_LEN];
        loop {
            match reader.read(&mut buffer) {
                Ok(0) => break,
                Ok(read) => {
                    if tx
                        .send_blocking(Ok(Bytes::copy_from_slice(&buffer[..read])))
                        .is_err()
                    {
                        break;
                    }
                }
                Err(error) => {
                    let _ = tx.send_blocking(Err(error));
                    break;
                }
            }
        }
    });
    rx
}

fn write_stream_to_blocking_writer<W>(
    mut writer: W,
    rx: async_channel::Receiver<io::Result<Option<Bytes>>>,
) -> io::Result<()>
where
    W: Write,
{
    while let Ok(item) = rx.recv_blocking() {
        match item? {
            Some(bytes) => writer.write_all(&bytes)?,
            None => break,
        }
    }
    writer.flush()
}

async fn wait_for_writer(task: std::thread::JoinHandle<io::Result<()>>) -> Result<io::Result<()>> {
    tokio::task::spawn_blocking(move || {
        task.join()
            .map_err(|_| anyhow::anyhow!("export writer thread panicked"))
    })
    .await?
}

fn validated_relative_string(name: &str) -> Result<String> {
    path_to_string(Path::new(name), true)
}

fn path_to_string(path: impl AsRef<Path>, must_be_relative: bool) -> Result<String> {
    let mut path_str = String::new();
    let parts = path
        .as_ref()
        .components()
        .filter_map(|component| match component {
            Component::Normal(x) => {
                let Some(component) = x.to_str() else {
                    return Some(Err(anyhow::anyhow!("invalid character in path")));
                };
                if !component.contains('/') && !component.contains('\\') {
                    Some(Ok(component))
                } else {
                    Some(Err(anyhow::anyhow!("invalid path component {component:?}")))
                }
            }
            Component::RootDir => {
                if must_be_relative {
                    Some(Err(anyhow::anyhow!("invalid root path component")))
                } else {
                    path_str.push('/');
                    None
                }
            }
            other => Some(Err(anyhow::anyhow!("invalid path component {other:?}"))),
        })
        .collect::<Result<Vec<_>>>()?;
    path_str.push_str(&parts.join("/"));
    Ok(path_str)
}

fn percent_decode_file_url_path(value: &str) -> Result<String> {
    let bytes = value.as_bytes();
    let mut output = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' {
            if i + 2 >= bytes.len() {
                anyhow::bail!("invalid percent escape in file URL");
            }
            let hex = std::str::from_utf8(&bytes[i + 1..i + 3])?;
            output.push(u8::from_str_radix(hex, 16).context("invalid percent escape in file URL")?);
            i += 3;
        } else {
            output.push(bytes[i]);
            i += 1;
        }
    }
    Ok(String::from_utf8(output)?)
}

async fn load_or_create_secret(app_data_dir: &Path) -> Result<SecretKey> {
    if let Ok(secret) = std::env::var("IROH_SECRET") {
        return SecretKey::from_str(&secret).context("invalid IROH_SECRET");
    }

    let path = app_data_dir.join("iroh.secret");
    match tokio::fs::read_to_string(&path).await {
        Ok(secret) => {
            let bytes = HEXLOWER
                .decode(secret.trim().as_bytes())
                .context("invalid persisted iroh secret encoding")?;
            let bytes: [u8; 32] = bytes
                .try_into()
                .map_err(|_| anyhow::anyhow!("invalid persisted iroh secret length"))?;
            Ok(SecretKey::from_bytes(&bytes))
        }
        Err(error) if error.kind() == io::ErrorKind::NotFound => {
            let secret = SecretKey::generate();
            tokio::fs::write(&path, HEXLOWER.encode(&secret.to_bytes())).await?;
            Ok(secret)
        }
        Err(error) => Err(error.into()),
    }
}

fn non_empty(value: String) -> Option<String> {
    let trimmed = value.trim();
    (!trimmed.is_empty()).then(|| trimmed.to_string())
}

fn now_ms() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_millis() as i64)
        .unwrap_or_default()
}

fn unique_transfer_id() -> u64 {
    now_ms() as u64
}

uniffi::setup_scaffolding!();

#[cfg(test)]
mod tests {
    use super::*;

    struct TestSink;

    impl CoreEventSink for TestSink {
        fn on_event(&self, _event: CoreEvent) {}
    }

    #[test]
    fn metadata_ticket_round_trips() {
        let secret = SecretKey::generate();
        let addr = iroh::EndpointAddr::new(secret.public());
        let blob_ticket = BlobTicket::new(addr, Hash::new([7; 32]), BlobFormat::HashSeq);
        let metadata = TransferMetadata::new(
            42,
            "Summer photos",
            Some("hammed".to_string()),
            blob_ticket.hash(),
            3,
            2048,
        );
        let encoded = VnidropTicket::new(blob_ticket.clone(), metadata.clone())
            .encode()
            .unwrap();
        let parsed = parse_transfer_ticket(&encoded).unwrap();

        assert_eq!(parsed.blob_ticket.hash(), blob_ticket.hash());
        assert_eq!(parsed.metadata.unwrap().transfer_name, metadata.transfer_name);
    }

    #[test]
    fn invalid_ticket_is_rejected() {
        assert!(parse_transfer_ticket("not-a-ticket").is_err());
    }

    #[tokio::test]
    async fn secret_persists() {
        let temp = tempfile::tempdir().unwrap();
        let first = load_or_create_secret(temp.path()).await.unwrap();
        let second = load_or_create_secret(temp.path()).await.unwrap();
        assert_eq!(first.to_bytes(), second.to_bytes());
    }

    #[test]
    fn path_validation_rejects_unsafe_paths() {
        assert!(path_to_string(Path::new("../escape"), true).is_err());
        assert!(path_to_string(Path::new("/absolute"), true).is_err());
        assert!(validated_relative_string("bad\\name").is_err());
    }

    #[test]
    fn file_url_decodes_spaces() {
        assert_eq!(
            percent_decode_file_url_path("/tmp/My%20File.txt").unwrap(),
            "/tmp/My File.txt"
        );
    }

    #[test]
    fn can_initialize_core() {
        let temp = tempfile::tempdir().unwrap();
        let core = VnidropCore::initialize(
            temp.path().to_string_lossy().to_string(),
            Arc::new(TestSink),
        )
        .unwrap();
        let status = core.status();
        assert!(!status.endpoint_id.is_empty());
        core.shutdown();
    }
}
