use iroh_blobs::Hash;
use serde::{Deserialize, Serialize};

use crate::util::{non_empty, now_ms};

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

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Enum)]
pub enum TransferAccessMode {
    Public,
    ApprovalRequired,
}

#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct StoredTransfer {
    pub transfer_id: u64,
    pub direction: String,
    pub status: String,
    pub transfer_name: Option<String>,
    pub content_hash: Option<String>,
    pub ticket: Option<String>,
    pub file_count: u64,
    pub total_size: u64,
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
