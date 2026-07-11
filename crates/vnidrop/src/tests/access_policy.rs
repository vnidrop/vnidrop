use crate::{
    access_policy::{AccessDecision, AccessPolicy},
    util::now_ms,
    TransferAccessMode,
};

#[tokio::test]
async fn requires_approved_endpoint_when_locked() {
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

#[tokio::test]
async fn rejects_expired_approval_sessions() {
    let policy = AccessPolicy::new();
    policy
        .set_mode(100, TransferAccessMode::ApprovalRequired)
        .await;
    policy
        .approve_endpoint_until(100, "node-a".to_string(), Some(now_ms() - 1))
        .await;

    assert_eq!(
        policy.decide(100, Some("node-a")).await,
        AccessDecision::Deny {
            reason: "approval-expired"
        }
    );
    assert_eq!(
        policy.decide(100, Some("node-a")).await,
        AccessDecision::Deny {
            reason: "approval-required"
        }
    );
}
