#[cfg(test)]
mod tests {
    use std::{path::Path, sync::Arc};

    use iroh::SecretKey;
    use iroh_blobs::{ticket::BlobTicket, BlobFormat, Hash};

    use crate::{
        access_policy::{AccessDecision, AccessPolicy},
        api::{CoreEvent, CoreEventSink, TransferMetadata},
        filesystem::{path_to_string, percent_decode_file_url_path, validated_relative_string},
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

    #[tokio::test]
    async fn repository_persists_transfers_and_events() {
        let temp = tempfile::tempdir().unwrap();
        let repository = Repository::open(temp.path()).await.unwrap();
        repository
            .upsert_transfer(
                7,
                "send",
                "sharing",
                Some("demo"),
                Some("hash"),
                Some("ticket"),
                1,
                12,
            )
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
    }
}
