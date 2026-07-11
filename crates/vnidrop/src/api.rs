use anyhow::Context;
use iroh_blobs::Hash;
use serde::{Deserialize, Serialize};

use crate::util::{non_empty, now_ms};

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct CoreLimits {
    pub max_sources: u64,
    pub max_collection_files: u64,
    pub max_total_bytes: u64,
    pub max_path_bytes: u64,
    pub max_ticket_bytes: u64,
    pub max_metadata_bytes: u64,
    pub max_events: u64,
    pub max_pending_approvals: u64,
    pub max_concurrent_transfers: u64,
    pub event_queue_capacity: u64,
}

impl Default for CoreLimits {
    fn default() -> Self {
        Self {
            max_sources: 128,
            max_collection_files: 10_000,
            max_total_bytes: 1024 * 1024 * 1024 * 1024,
            max_path_bytes: 4_096,
            max_ticket_bytes: 1024 * 1024,
            max_metadata_bytes: 16 * 1024,
            max_events: 500,
            max_pending_approvals: 1_024,
            max_concurrent_transfers: 8,
            event_queue_capacity: 1_024,
        }
    }
}

impl CoreLimits {
    pub(crate) fn validate(&self) -> anyhow::Result<()> {
        let positive = [
            ("max_sources", self.max_sources),
            ("max_collection_files", self.max_collection_files),
            ("max_total_bytes", self.max_total_bytes),
            ("max_path_bytes", self.max_path_bytes),
            ("max_ticket_bytes", self.max_ticket_bytes),
            ("max_metadata_bytes", self.max_metadata_bytes),
            ("max_events", self.max_events),
            ("max_pending_approvals", self.max_pending_approvals),
            ("max_concurrent_transfers", self.max_concurrent_transfers),
            ("event_queue_capacity", self.event_queue_capacity),
        ];
        for (name, value) in positive {
            if value == 0 {
                anyhow::bail!("core limit {name} must be greater than zero");
            }
        }
        for (name, value) in [
            ("max_pending_approvals", self.max_pending_approvals),
            ("max_concurrent_transfers", self.max_concurrent_transfers),
            ("event_queue_capacity", self.event_queue_capacity),
        ] {
            usize::try_from(value)
                .with_context(|| format!("core limit {name} exceeds platform capacity"))?;
        }
        if self.max_total_bytes > i64::MAX as u64 || self.max_events > i64::MAX as u64 {
            anyhow::bail!("SQLite-backed limits must fit in a signed 64-bit integer");
        }
        Ok(())
    }

    pub(crate) fn validate_metadata_text(
        &self,
        field: &str,
        value: Option<&str>,
    ) -> anyhow::Result<()> {
        if let Some(value) = value {
            if value.len() as u64 > self.max_metadata_bytes {
                anyhow::bail!(
                    "{field} is {} bytes, limit is {}",
                    value.len(),
                    self.max_metadata_bytes
                );
            }
        }
        Ok(())
    }
}

#[uniffi::export]
pub fn default_core_limits() -> CoreLimits {
    CoreLimits::default()
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

#[uniffi::export(with_foreign)]
pub trait ReceiveOutputSink: Send + Sync {
    fn start_file(&self, relative_path: String) -> Result<(), crate::error::VnidropError>;
    fn write_chunk(
        &self,
        relative_path: String,
        bytes: Vec<u8>,
    ) -> Result<(), crate::error::VnidropError>;
    fn finish_file(&self, relative_path: String) -> Result<(), crate::error::VnidropError>;
    fn abort_file(
        &self,
        relative_path: String,
        reason: String,
    ) -> Result<(), crate::error::VnidropError>;
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
    FileDescriptor,
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
    pub access_mode: TransferAccessMode,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
pub enum TransferAccessMode {
    Public,
    ApprovalRequired,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct StoredTransfer {
    pub local_id: String,
    pub transfer_id: u64,
    pub peer_id: Option<String>,
    pub direction: String,
    pub status: String,
    pub transfer_name: Option<String>,
    pub content_hash: Option<String>,
    pub ticket: Option<String>,
    pub file_count: u64,
    pub total_size: u64,
    pub access_mode: TransferAccessMode,
    pub created_at: i64,
    pub updated_at: i64,
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
    pub(crate) fn new(
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

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct TicketInspection {
    pub kind: String,
    pub blob_ticket: String,
    pub metadata: Option<TransferMetadata>,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct ReceiverRequest {
    pub id: String,
    pub transfer_id: u64,
    pub remote_endpoint_id: String,
    pub transfer_name: String,
    pub receiver_name: Option<String>,
    pub receiver_device_name: Option<String>,
    pub app_version: String,
    pub status: String,
    pub reason: Option<String>,
    pub requested_at: i64,
    pub responded_at: Option<i64>,
    pub completed_at: Option<i64>,
}
