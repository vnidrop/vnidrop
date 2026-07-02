use std::str::FromStr;

use anyhow::{Context, Result};
use data_encoding::BASE64URL_NOPAD;
use iroh_blobs::ticket::BlobTicket;
use serde::{Deserialize, Serialize};

use crate::api::TransferMetadata;

const VNIDROP_TICKET_PREFIX: &str = "vnd1:";

#[derive(Debug, Clone, Serialize, Deserialize)]
pub(crate) struct VnidropTicket {
    version: u8,
    blob_ticket: String,
    metadata: TransferMetadata,
}

impl VnidropTicket {
    pub(crate) fn new(blob_ticket: BlobTicket, metadata: TransferMetadata) -> Self {
        Self {
            version: 1,
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

pub(crate) fn parse_transfer_ticket(value: &str) -> Result<ParsedTransferTicket> {
    let normalized = normalize_ticket_input(value);
    if normalized.starts_with(VNIDROP_TICKET_PREFIX) {
        let ticket = VnidropTicket::decode(&normalized)?;
        let blob_ticket = BlobTicket::from_str(&ticket.blob_ticket)
            .context("invalid BlobTicket inside VniDrop ticket")?;
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
    value.chars().filter(|char| !char.is_whitespace()).collect()
}
