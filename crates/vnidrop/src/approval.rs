use std::{collections::HashMap, sync::Arc, time::Duration};

use serde::{Deserialize, Serialize};
use serde_json::json;
use tokio::sync::{oneshot, Mutex};
use uuid::Uuid;

use crate::{
    access_policy::AccessPolicy,
    event_hub::EventHub,
    handshake::{HandshakeResponse, RequestTransfer},
    repository::{ReceiverRequestInsert, Repository},
    util::now_ms,
};

const APPROVAL_TTL_MS: i64 = 10 * 60 * 1000;
const APPROVAL_WAIT_TIMEOUT: Duration = Duration::from_secs(120);

#[derive(Debug, Clone, Serialize, Deserialize)]
pub(crate) struct ApprovalDecision {
    pub(crate) request_id: String,
    pub(crate) accepted: bool,
    pub(crate) reason: Option<String>,
}

#[derive(Clone)]
pub(crate) struct ApprovalService {
    repository: Repository,
    event_hub: Arc<EventHub>,
    access_policy: Arc<AccessPolicy>,
    pending: Arc<Mutex<HashMap<String, oneshot::Sender<ApprovalDecision>>>>,
}

impl ApprovalService {
    pub(crate) fn new(
        repository: Repository,
        event_hub: Arc<EventHub>,
        access_policy: Arc<AccessPolicy>,
    ) -> Self {
        Self {
            repository,
            event_hub,
            access_policy,
            pending: Arc::new(Mutex::new(HashMap::new())),
        }
    }

    pub(crate) async fn respond(
        &self,
        request_id: String,
        accepted: bool,
        reason: Option<String>,
    ) -> anyhow::Result<()> {
        let sender = self.pending.lock().await.remove(&request_id);
        let status = if accepted { "accepted" } else { "refused" };
        self.repository
            .update_receiver_request_status(&request_id, status, reason.as_deref())
            .await?;

        if let Some(sender) = sender {
            let _ = sender.send(ApprovalDecision {
                request_id,
                accepted,
                reason,
            });
        }

        Ok(())
    }

    pub(crate) async fn request_transfer(
        &self,
        remote_endpoint_id: String,
        request: RequestTransfer,
    ) -> HandshakeResponse {
        self.event_hub.emit_transfer(
            request.transfer_id,
            "send",
            "handshake",
            "transfer-requested",
            json!({
                "remote_endpoint_id": remote_endpoint_id,
                "request": request,
            }),
        );

        match self
            .repository
            .send_exists(request.transfer_id, &request.transfer_hash)
            .await
        {
            Ok(true) => {
                self.wait_for_sender_decision(remote_endpoint_id, request)
                    .await
            }
            Ok(false) => {
                self.deny(request.transfer_id, remote_endpoint_id, "unknown-transfer")
                    .await
            }
            Err(error) => {
                tracing::error!(%error, "failed to validate handshake transfer request");
                self.deny(request.transfer_id, remote_endpoint_id, "repository-error")
                    .await
            }
        }
    }

    async fn wait_for_sender_decision(
        &self,
        remote_endpoint_id: String,
        request: RequestTransfer,
    ) -> HandshakeResponse {
        let request_id = Uuid::new_v4().to_string();
        let (tx, rx) = oneshot::channel();
        self.pending.lock().await.insert(request_id.clone(), tx);

        let insert_result = self
            .repository
            .insert_receiver_request(ReceiverRequestInsert {
                id: &request_id,
                transfer_id: request.transfer_id,
                remote_endpoint_id: &remote_endpoint_id,
                transfer_name: &request.transfer_name,
                receiver_name: request.receiver_name.as_deref(),
                receiver_device_name: request.receiver_device_name.as_deref(),
                app_version: &request.app_version,
            })
            .await;

        if let Err(error) = insert_result {
            self.pending.lock().await.remove(&request_id);
            tracing::error!(%error, "failed to persist receiver request");
            return self
                .deny(request.transfer_id, remote_endpoint_id, "repository-error")
                .await;
        }

        self.event_hub.emit_transfer(
            request.transfer_id,
            "send",
            "approval",
            "receiver-requested",
            json!({
                "request_id": request_id,
                "remote_endpoint_id": remote_endpoint_id,
                "receiver_name": request.receiver_name,
                "receiver_device_name": request.receiver_device_name,
                "transfer_name": request.transfer_name,
            }),
        );

        match tokio::time::timeout(APPROVAL_WAIT_TIMEOUT, rx).await {
            Ok(Ok(decision)) if decision.accepted => {
                let token = Uuid::new_v4().to_string();
                let expires_at = now_ms() + APPROVAL_TTL_MS;
                self.access_policy
                    .approve_endpoint_until(
                        request.transfer_id,
                        remote_endpoint_id.clone(),
                        Some(expires_at),
                    )
                    .await;
                self.event_hub.emit_transfer(
                    request.transfer_id,
                    "send",
                    "approval",
                    "receiver-accepted",
                    json!({
                        "request_id": decision.request_id,
                        "remote_endpoint_id": remote_endpoint_id,
                        "expires_at": expires_at,
                    }),
                );
                HandshakeResponse::Approved { token, expires_at }
            }
            Ok(Ok(decision)) => {
                self.deny(
                    request.transfer_id,
                    remote_endpoint_id,
                    decision
                        .reason
                        .unwrap_or_else(|| "sender-refused".to_string()),
                )
                .await
            }
            Ok(Err(_)) | Err(_) => {
                self.pending.lock().await.remove(&request_id);
                let _ = self
                    .repository
                    .update_receiver_request_status(
                        &request_id,
                        "expired",
                        Some("approval timed out"),
                    )
                    .await;
                self.deny(request.transfer_id, remote_endpoint_id, "approval-timeout")
                    .await
            }
        }
    }

    async fn deny(
        &self,
        transfer_id: u64,
        remote_endpoint_id: String,
        reason: impl Into<String>,
    ) -> HandshakeResponse {
        let reason = reason.into();
        self.event_hub.emit_transfer(
            transfer_id,
            "send",
            "approval",
            "receiver-refused",
            json!({
                "remote_endpoint_id": remote_endpoint_id,
                "reason": reason,
            }),
        );
        HandshakeResponse::Denied { reason }
    }
}
