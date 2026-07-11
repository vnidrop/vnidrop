use std::str::FromStr;

use anyhow::{Context, Result};
use data_encoding::BASE64URL_NOPAD;
use iroh_blobs::ticket::BlobTicket;
use serde::{Deserialize, Serialize};

use crate::api::{CoreLimits, TransferMetadata};

const VNIDROP_TICKET_PREFIX: &str = "vnd1:";
const VNIDROP_TICKET_VERSION: u8 = 1;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub(crate) struct VnidropTicket {
    version: u8,
    blob_ticket: String,
    metadata: TransferMetadata,
}

impl VnidropTicket {
    pub(crate) fn new(blob_ticket: BlobTicket, metadata: TransferMetadata) -> Self {
        Self {
            version: VNIDROP_TICKET_VERSION,
            blob_ticket: blob_ticket.to_string(),
            metadata,
        }
    }

    pub(crate) fn encode(&self) -> Result<String> {
        let bytes = serde_json::to_vec(self)?;
        Ok(format!(
            "{VNIDROP_TICKET_PREFIX}{}",
            BASE64URL_NOPAD.encode(&bytes)
        ))
    }

    fn decode(value: &str) -> Result<Self> {
        let normalized = normalize_ticket_input(value);
        let encoded = normalized
            .strip_prefix(VNIDROP_TICKET_PREFIX)
            .context("not a VniDrop ticket")?;
        let bytes = BASE64URL_NOPAD
            .decode(encoded.as_bytes())
            .context("invalid VniDrop ticket encoding")?;
        serde_json::from_slice(&bytes).context("invalid VniDrop ticket payload")
    }
}

#[derive(Debug, Clone)]
pub(crate) struct ParsedTransferTicket {
    pub(crate) blob_ticket: BlobTicket,
    pub(crate) metadata: Option<TransferMetadata>,
}

#[cfg(test)]
pub(crate) fn parse_transfer_ticket(value: &str) -> Result<ParsedTransferTicket> {
    parse_transfer_ticket_with_limits(value, &CoreLimits::default())
}

pub(crate) fn parse_transfer_ticket_with_limits(
    value: &str,
    limits: &CoreLimits,
) -> Result<ParsedTransferTicket> {
    if value.len() as u64 > limits.max_ticket_bytes {
        anyhow::bail!(
            "ticket is {} bytes, limit is {}",
            value.len(),
            limits.max_ticket_bytes
        );
    }
    let normalized = normalize_ticket_input(value);
    if normalized.starts_with(VNIDROP_TICKET_PREFIX) {
        let ticket = VnidropTicket::decode(&normalized)?;
        if ticket.version != VNIDROP_TICKET_VERSION {
            anyhow::bail!("unsupported VniDrop ticket version {}", ticket.version);
        }
        if ticket.metadata.version != VNIDROP_TICKET_VERSION {
            anyhow::bail!(
                "unsupported VniDrop metadata version {}",
                ticket.metadata.version
            );
        }
        if ticket.metadata.transfer_id == 0 {
            anyhow::bail!("VniDrop ticket metadata is missing a valid transfer id");
        }
        if ticket.metadata.transfer_name.trim().is_empty() {
            anyhow::bail!("VniDrop ticket metadata is missing a transfer name");
        }
        limits.validate_metadata_text(
            "transfer name",
            Some(ticket.metadata.transfer_name.as_str()),
        )?;
        limits.validate_metadata_text("sender name", ticket.metadata.sender_name.as_deref())?;
        let blob_ticket = BlobTicket::from_str(&ticket.blob_ticket)
            .context("invalid BlobTicket inside VniDrop ticket")?;
        if ticket.metadata.content_hash != blob_ticket.hash().to_string() {
            anyhow::bail!("VniDrop ticket metadata hash does not match BlobTicket hash");
        }
        return Ok(ParsedTransferTicket {
            blob_ticket,
            metadata: Some(ticket.metadata),
        });
    }

    let blob_ticket = BlobTicket::from_str(&normalized).context("invalid BlobTicket")?;
    Ok(ParsedTransferTicket {
        blob_ticket,
        metadata: None,
    })
}

fn normalize_ticket_input(value: &str) -> String {
    // Tickets are commonly copied from text views or chat apps that insert line
    // breaks.  Strip whitespace only; other corrupt characters should still be
    // rejected by the base64 or BlobTicket decoders.
    value.chars().filter(|char| !char.is_whitespace()).collect()
}
