use data_encoding::BASE64URL_NOPAD;
use iroh::SecretKey;
use iroh_blobs::{ticket::BlobTicket, BlobFormat, Hash};
use serde_json::json;

use crate::{
    api::{CoreLimits, TransferMetadata},
    ticket::{parse_transfer_ticket, parse_transfer_ticket_with_limits, VnidropTicket},
};

fn blob_ticket(hash_byte: u8) -> BlobTicket {
    let secret = SecretKey::generate();
    let addr = iroh::EndpointAddr::new(secret.public());
    BlobTicket::new(addr, Hash::new([hash_byte; 32]), BlobFormat::HashSeq)
}

#[test]
fn metadata_ticket_round_trips() {
    let blob_ticket = blob_ticket(7);
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
    assert_eq!(parsed.metadata.transfer_name, metadata.transfer_name);
}

#[test]
fn metadata_ticket_tolerates_wrapped_whitespace() {
    let blob_ticket = blob_ticket(9);
    let metadata = TransferMetadata::new(7, "Wrapped", None, blob_ticket.hash(), 1, 10);
    let encoded = VnidropTicket::new(blob_ticket.clone(), metadata)
        .encode()
        .unwrap();
    let wrapped = encoded
        .as_bytes()
        .chunks(8)
        .map(|chunk| std::str::from_utf8(chunk).unwrap())
        .collect::<Vec<_>>()
        .join("\n  ");

    let parsed = parse_transfer_ticket(&wrapped).unwrap();
    assert_eq!(parsed.blob_ticket.hash(), blob_ticket.hash());
}

#[test]
fn invalid_ticket_is_rejected() {
    assert!(parse_transfer_ticket("not-a-ticket").is_err());
}

#[test]
fn rejects_raw_blob_tickets() {
    let raw = blob_ticket(3).to_string();
    let error = parse_transfer_ticket(&raw).unwrap_err().to_string();
    assert!(
        error.contains("not a VniDrop ticket"),
        "raw BlobTicket must not be accepted as a transfer invitation: {error}"
    );
}

#[test]
fn rejects_unsupported_versions_and_mismatched_hashes() {
    let blob_ticket = blob_ticket(5);
    let payload = json!({
        "version": 2,
        "blob_ticket": blob_ticket.to_string(),
        "metadata": {
            "version": 1,
            "transfer_id": 7,
            "transfer_name": "bad version",
            "sender_name": null,
            "created_at": 1,
            "content_hash": blob_ticket.hash().to_string(),
            "file_count": 1,
            "total_size": 10
        }
    });
    let encoded = format!(
        "vnd1:{}",
        BASE64URL_NOPAD.encode(payload.to_string().as_bytes())
    );
    assert!(parse_transfer_ticket(&encoded)
        .unwrap_err()
        .to_string()
        .contains("unsupported VniDrop ticket version"));

    let payload = json!({
        "version": 1,
        "blob_ticket": blob_ticket.to_string(),
        "metadata": {
            "version": 1,
            "transfer_id": 7,
            "transfer_name": "bad hash",
            "sender_name": null,
            "created_at": 1,
            "content_hash": Hash::new([6; 32]).to_string(),
            "file_count": 1,
            "total_size": 10
        }
    });
    let encoded = format!(
        "vnd1:{}",
        BASE64URL_NOPAD.encode(payload.to_string().as_bytes())
    );
    assert!(parse_transfer_ticket(&encoded)
        .unwrap_err()
        .to_string()
        .contains("metadata hash does not match"));
}

#[test]
fn rejects_ticket_over_configured_size_limit() {
    let limits = CoreLimits {
        max_ticket_bytes: 8,
        ..CoreLimits::default()
    };
    assert!(parse_transfer_ticket_with_limits("not-a-ticket", &limits).is_err());
}

#[test]
fn parser_rejects_or_parses_generated_inputs_without_panicking() {
    let alphabet = b"vnd1:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_ /\\\n";
    let mut state = 0x9e37_79b9u32;
    for len in 0..512usize {
        let mut input = String::with_capacity(len);
        for _ in 0..len {
            state = state.wrapping_mul(1_664_525).wrapping_add(1_013_904_223);
            input.push(alphabet[state as usize % alphabet.len()] as char);
        }
        let _ = parse_transfer_ticket(&input);
    }
}
