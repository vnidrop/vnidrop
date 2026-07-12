use std::{
    io,
    path::{Path, PathBuf},
    sync::Arc,
};

use anyhow::{Context, Result};
use bytes::Bytes;
use futures_lite::StreamExt as _;
use iroh_blobs::{
    api::proto::ExportRangesItem, api::remote::GetProgressItem, format::collection::Collection,
    get::request::get_hash_seq_and_sizes, Hash,
};
use serde_json::json;
use tokio::sync::oneshot;

use super::{ActiveTransfer, CoreInner};
use crate::{
    access_policy::mode_to_storage,
    api::{ReceiveOutputSink, TransferAccessMode, TransferMetadata},
    filesystem::{
        validated_relative_string, wait_for_writer, write_stream_to_blocking_writer,
        AtomicOutputFile,
    },
    handshake::{DeliveryReceipt, DeliveryReceiptResponse, HandshakeResponse, HandshakeService},
    repository::TransferUpsert,
    ticket::{parse_transfer_ticket_with_limits, ParsedTransferTicket},
    transfer_state::{TransferDirection, TransferStatus},
    util::unique_transfer_id,
};

pub(super) enum ReceiveTarget {
    Directory(PathBuf),
    OutputSink(Arc<dyn ReceiveOutputSink>),
}

pub(super) struct OutputSinkFile<'a> {
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

impl CoreInner {
    pub(super) async fn receive(
        self: &Arc<Self>,
        ticket: String,
        output_dir: PathBuf,
        receiver_name: Option<String>,
    ) -> Result<()> {
        self.receive_to_target(ticket, ReceiveTarget::Directory(output_dir), receiver_name)
            .await
    }

    pub(super) async fn receive_with_output_sink(
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

    pub(super) async fn receive_to_target(
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
        self.active_transfers
            .lock()
            .expect("active_transfers")
            .insert(
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

        self.active_transfers
            .lock()
            .expect("active_transfers")
            .remove(&transfer_id);
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

    pub(super) async fn receive_inner(
        self: &Arc<Self>,
        transfer_id: u64,
        parsed: ParsedTransferTicket,
        target: ReceiveTarget,
        receiver_name: Option<String>,
    ) -> Result<()> {
        if let ReceiveTarget::Directory(output_dir) = &target {
            tokio::fs::create_dir_all(output_dir).await?;
        }
        let sender_addr = parsed.blob_ticket.addr().clone();

        self.emit_transfer(transfer_id, "receive", "network", "connecting", json!({}));
        let delivery_receipt = if let Some(metadata) = &parsed.metadata {
            Some(
                self.request_transfer_approval(
                    transfer_id,
                    sender_addr.clone(),
                    metadata,
                    receiver_name.as_deref(),
                )
                .await?,
            )
        } else {
            None
        };
        let connection = self
            .endpoint
            .connect(sender_addr.clone(), iroh_blobs::ALPN)
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
        if let Some(receipt) = delivery_receipt {
            let sender_transfer_id = receipt.transfer_id;
            let client = HandshakeService::client(self.endpoint.clone(), sender_addr);
            match client.report_delivery(receipt).await {
                Ok(DeliveryReceiptResponse::Recorded) => self.emit_transfer(
                    transfer_id,
                    "receive",
                    "delivery",
                    "receipt-recorded",
                    json!({ "sender_transfer_id": sender_transfer_id }),
                ),
                Ok(DeliveryReceiptResponse::Rejected { reason }) => self.emit_transfer(
                    transfer_id,
                    "receive",
                    "delivery",
                    "receipt-rejected",
                    json!({ "reason": reason }),
                ),
                Err(error) => self.emit_transfer(
                    transfer_id,
                    "receive",
                    "delivery",
                    "receipt-failed",
                    json!({ "reason": error.to_string() }),
                ),
            }
        }
        Ok(())
    }

    pub(super) async fn persist_receive_start(
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

    pub(super) async fn request_transfer_approval(
        &self,
        local_transfer_id: u64,
        addr: iroh::EndpointAddr,
        metadata: &TransferMetadata,
        receiver_name: Option<&str>,
    ) -> Result<DeliveryReceipt> {
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
            HandshakeResponse::Approved {
                request_id,
                token,
                expires_at,
            } => {
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
                Ok(DeliveryReceipt {
                    request_id,
                    transfer_id: metadata.transfer_id,
                    token,
                })
            }
            HandshakeResponse::Denied { reason } => {
                anyhow::bail!("transfer request was denied by sender: {reason}")
            }
        }
    }

    pub(super) async fn export_collection(
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

    pub(super) async fn export_blob_to_directory(
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

    pub(super) async fn export_blob_to_sink(
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
                    // Yield so outer `select!` cancel can land between chunks.
                    // Sink writes are synchronous (foreign FFI); without this,
                    // a single poll can drain the stream and miss cancellation.
                    tokio::task::yield_now().await;
                }
                ExportRangesItem::Error(error) => {
                    anyhow::bail!("export failed for {relative_path}: {error}");
                }
            }
        }

        output_file.finish()?;
        Ok(())
    }
}
