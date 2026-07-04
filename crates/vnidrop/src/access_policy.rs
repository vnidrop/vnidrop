use std::{collections::HashMap, sync::Arc};

use tokio::sync::RwLock;

use crate::api::TransferAccessMode;
use crate::util::now_ms;

#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) enum AccessDecision {
    Allow,
    Deny { reason: &'static str },
}

#[derive(Debug, Default)]
pub(crate) struct AccessPolicy {
    modes: RwLock<HashMap<u64, TransferAccessMode>>,
    approved_sessions: RwLock<HashMap<(u64, String), ApprovalSession>>,
}

impl AccessPolicy {
    pub(crate) fn new() -> Arc<Self> {
        Arc::new(Self::default())
    }

    pub(crate) async fn set_mode(&self, transfer_id: u64, mode: TransferAccessMode) {
        self.modes.write().await.insert(transfer_id, mode);
    }

    pub(crate) async fn remove_transfer(&self, transfer_id: u64) {
        self.modes.write().await.remove(&transfer_id);
        self.approved_sessions
            .write()
            .await
            .retain(|(id, _), _| *id != transfer_id);
    }

    pub(crate) async fn approve_endpoint(&self, transfer_id: u64, endpoint_id: String) {
        self.approve_endpoint_until(transfer_id, endpoint_id, None)
            .await;
    }

    pub(crate) async fn approve_endpoint_until(
        &self,
        transfer_id: u64,
        endpoint_id: String,
        expires_at: Option<i64>,
    ) {
        self.approved_sessions
            .write()
            .await
            .insert((transfer_id, endpoint_id), ApprovalSession { expires_at });
    }

    pub(crate) async fn decide(
        &self,
        transfer_id: u64,
        endpoint_id: Option<&str>,
    ) -> AccessDecision {
        match self
            .modes
            .read()
            .await
            .get(&transfer_id)
            .cloned()
            .unwrap_or(TransferAccessMode::Public)
        {
            TransferAccessMode::Public => AccessDecision::Allow,
            TransferAccessMode::ApprovalRequired => {
                let Some(endpoint_id) = endpoint_id else {
                    return AccessDecision::Deny {
                        reason: "missing-endpoint-id",
                    };
                };
                let key = (transfer_id, endpoint_id.to_string());
                let mut sessions = self.approved_sessions.write().await;
                match sessions.get(&key) {
                    Some(session) if session.is_valid(now_ms()) => AccessDecision::Allow,
                    Some(_) => {
                        sessions.remove(&key);
                        AccessDecision::Deny {
                            reason: "approval-expired",
                        }
                    }
                    None => AccessDecision::Deny {
                        reason: "approval-required",
                    },
                }
            }
        }
    }
}

#[derive(Debug, Clone)]
struct ApprovalSession {
    expires_at: Option<i64>,
}

impl ApprovalSession {
    fn is_valid(&self, now: i64) -> bool {
        self.expires_at.is_none_or(|expires_at| expires_at >= now)
    }
}
