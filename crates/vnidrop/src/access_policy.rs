use std::{
    collections::{HashMap, HashSet},
    sync::Arc,
};

use tokio::sync::RwLock;

use crate::api::TransferAccessMode;

#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) enum AccessDecision {
    Allow,
    Deny { reason: &'static str },
}

#[derive(Debug, Default)]
pub(crate) struct AccessPolicy {
    modes: RwLock<HashMap<u64, TransferAccessMode>>,
    approved_sessions: RwLock<HashSet<(u64, String)>>,
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
            .retain(|(id, _)| *id != transfer_id);
    }

    pub(crate) async fn approve_endpoint(&self, transfer_id: u64, endpoint_id: String) {
        self.approved_sessions
            .write()
            .await
            .insert((transfer_id, endpoint_id));
    }

    pub(crate) async fn decide(
        &self,
        transfer_id: u64,
        endpoint_id: Option<&str>,
    ) -> AccessDecision {
        // This is intentionally only the provider-side gate for milestone one.
        // A later handshake can add receiver-request/sender-approval events on
        // top without weakening the default public sharing behavior.
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
                if self
                    .approved_sessions
                    .read()
                    .await
                    .contains(&(transfer_id, endpoint_id.to_string()))
                {
                    AccessDecision::Allow
                } else {
                    AccessDecision::Deny {
                        reason: "approval-required",
                    }
                }
            }
        }
    }
}
