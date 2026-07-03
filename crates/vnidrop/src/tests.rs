#[cfg(test)]
mod tests {
    #[cfg(unix)]
    use std::os::fd::AsRawFd;
    use std::{io::Read, path::Path, sync::Arc};

    use data_encoding::BASE64URL_NOPAD;
    use iroh::SecretKey;
    use iroh_blobs::{ticket::BlobTicket, BlobFormat, Hash};
    use serde_json::json;

    use crate::{
        access_policy::{AccessDecision, AccessPolicy},
        api::{CoreEvent, CoreEventSink, ShareSource, SourceKind, TransferMetadata},
        error::VnidropError,
        filesystem::{
            collect_import_files, default_collection_name, path_to_string,
            percent_decode_file_url_path, validated_relative_string,
        },
        repository::Repository,
        runtime::VnidropCore,
        secret::load_or_create_secret,
        ticket::{parse_transfer_ticket, VnidropTicket},
        TransferAccessMode,
    };

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
        assert_eq!(
            parsed.metadata.unwrap().transfer_name,
            metadata.transfer_name
        );
    }

    #[test]
    fn metadata_ticket_round_trip_tolerates_wrapped_whitespace() {
        let secret = SecretKey::generate();
        let addr = iroh::EndpointAddr::new(secret.public());
        let blob_ticket = BlobTicket::new(addr, Hash::new([9; 32]), BlobFormat::HashSeq);
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
    fn ticket_rejects_unsupported_versions_and_mismatched_hashes() {
        let secret = SecretKey::generate();
        let addr = iroh::EndpointAddr::new(secret.public());
        let blob_ticket = BlobTicket::new(addr, Hash::new([5; 32]), BlobFormat::HashSeq);
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
        assert!(validated_relative_string("").is_err());
    }

    #[test]
    fn file_url_decodes_spaces() {
        assert_eq!(
            percent_decode_file_url_path("/tmp/My%20File.txt").unwrap(),
            "/tmp/My File.txt"
        );
    }

    #[cfg(unix)]
    #[test]
    fn file_descriptor_source_duplicates_and_streams() {
        let mut temp = tempfile::tempfile().unwrap();
        std::io::Write::write_all(&mut temp, b"fd-backed import").unwrap();
        std::io::Seek::rewind(&mut temp).unwrap();

        let files = collect_import_files(vec![ShareSource {
            kind: SourceKind::FileDescriptor,
            value: temp.as_raw_fd().to_string(),
            display_name: Some("from-fd.txt".to_string()),
            is_directory: false,
        }])
        .unwrap();

        let mut imported = files.into_iter().next().unwrap().source.open().unwrap();
        let mut content = String::new();
        imported.read_to_string(&mut content).unwrap();
        assert_eq!(content, "fd-backed import");
    }

    #[cfg(unix)]
    #[test]
    fn file_descriptor_source_rejects_invalid_values() {
        assert!(collect_import_files(vec![ShareSource {
            kind: SourceKind::FileDescriptor,
            value: "not-an-fd".to_string(),
            display_name: Some("from-fd.txt".to_string()),
            is_directory: false,
        }])
        .is_err());

        assert!(collect_import_files(vec![ShareSource {
            kind: SourceKind::FileDescriptor,
            value: "-1".to_string(),
            display_name: Some("from-fd.txt".to_string()),
            is_directory: false,
        }])
        .is_err());
    }

    #[test]
    fn android_content_uri_must_be_opened_by_platform_code() {
        let error = collect_import_files(vec![ShareSource {
            kind: SourceKind::AndroidContentUri,
            value: "content://media/item".to_string(),
            display_name: Some("from-uri.txt".to_string()),
            is_directory: false,
        }])
        .unwrap_err()
        .to_string();
        assert!(error.contains("ParcelFileDescriptor"));
    }

    #[test]
    fn directory_sources_preserve_safe_relative_names() {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path().join("picked");
        std::fs::create_dir_all(root.join("nested")).unwrap();
        std::fs::write(root.join("nested").join("a.txt"), b"a").unwrap();
        std::fs::write(root.join("b.txt"), b"b").unwrap();

        let mut files = collect_import_files(vec![ShareSource {
            kind: SourceKind::Path,
            value: root.to_string_lossy().to_string(),
            display_name: Some("Album".to_string()),
            is_directory: true,
        }])
        .unwrap();
        files.sort_by(|a, b| a.collection_name.cmp(&b.collection_name));

        assert_eq!(default_collection_name(&files), "Album");
        assert_eq!(files[0].collection_name, "Album/b.txt");
        assert_eq!(files[1].collection_name, "Album/nested/a.txt");
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

    #[tokio::test]
    async fn repository_persists_transfers_and_events() {
        let temp = tempfile::tempdir().unwrap();
        let repository = Repository::open(temp.path()).await.unwrap();
        assert_eq!(repository.schema_version().await.unwrap(), 1);
        repository
            .upsert_transfer(crate::repository::TransferUpsert {
                transfer_id: 7,
                direction: "send",
                status: "sharing",
                transfer_name: Some("demo"),
                content_hash: Some("hash"),
                ticket: Some("ticket"),
                file_count: 1,
                total_size: 12,
            })
            .await
            .unwrap();
        repository
            .insert_event(&CoreEvent {
                id: "event-1".to_string(),
                timestamp: 10,
                scope: "transfer".to_string(),
                transfer_id: Some(7),
                direction: Some("send".to_string()),
                phase: "ticket".to_string(),
                kind: "created".to_string(),
                data_json: "{}".to_string(),
            })
            .await
            .unwrap();

        let transfers = repository.list_transfers().await.unwrap();
        assert_eq!(transfers.len(), 1);
        assert_eq!(transfers[0].transfer_name.as_deref(), Some("demo"));

        let events = repository.list_events(Some(7)).await.unwrap();
        assert_eq!(events.len(), 1);
        assert_eq!(events[0].kind, "created");

        let reopened = Repository::open(temp.path()).await.unwrap();
        let transfers = reopened.list_transfers().await.unwrap();
        assert_eq!(transfers.len(), 1);
        let events = reopened.list_events(Some(7)).await.unwrap();
        assert_eq!(events[0].id, "event-1");
    }

    #[tokio::test]
    async fn access_policy_requires_approved_endpoint_when_locked() {
        let policy = AccessPolicy::new();
        policy
            .set_mode(99, TransferAccessMode::ApprovalRequired)
            .await;

        assert_eq!(
            policy.decide(99, Some("node-a")).await,
            AccessDecision::Deny {
                reason: "approval-required"
            }
        );

        policy.approve_endpoint(99, "node-a".to_string()).await;
        assert_eq!(
            policy.decide(99, Some("node-a")).await,
            AccessDecision::Allow
        );
        assert_eq!(
            policy.decide(99, None).await,
            AccessDecision::Deny {
                reason: "missing-endpoint-id"
            }
        );
    }

    #[test]
    fn invalid_receive_ticket_is_typed_and_persisted_as_event() {
        let temp = tempfile::tempdir().unwrap();
        let core = VnidropCore::initialize(
            temp.path().to_string_lossy().to_string(),
            Arc::new(TestSink),
        )
        .unwrap();

        let error = core
            .receive(
                "not-a-ticket".to_string(),
                temp.path().to_string_lossy().to_string(),
                None,
            )
            .unwrap_err();
        assert!(matches!(error, VnidropError::Ticket { .. }));

        let events = core.list_events(None).unwrap();
        assert!(events
            .iter()
            .any(|event| event.phase == "error" && event.kind == "invalid-ticket"));
        core.shutdown();
    }
}
