use std::sync::Arc;

use iroh_blobs::{
    provider::events::{ProviderMessage, RequestUpdate},
    Hash,
};
use serde_json::json;
use tokio::sync::mpsc;

use super::CoreInner;
use crate::access_policy::AccessDecision;

impl CoreInner {
    pub(super) async fn spawn_provider_event_task(
        self: &Arc<Self>,
        mut rx: mpsc::Receiver<ProviderMessage>,
    ) {
        let core = self.clone();
        let task = tokio::spawn(async move {
            while let Some(message) = rx.recv().await {
                core.handle_provider_message(message).await;
            }
        });
        *self.provider_task.lock().await = Some(task);
    }

    pub(super) async fn handle_provider_message(self: &Arc<Self>, message: ProviderMessage) {
        match message {
            ProviderMessage::ClientConnected(message) => {
                self.emit_endpoint(
                    "provider",
                    "client-connected",
                    json!({
                        "connection_id": message.inner.connection_id,
                        "endpoint_id": message.inner.endpoint_id.map(|id| id.to_string()),
                    }),
                );
                if let Some(endpoint_id) = message.inner.endpoint_id {
                    self.connection_endpoints
                        .lock()
                        .await
                        .insert(message.inner.connection_id, endpoint_id.to_string());
                }
                let _ = message.tx.send(Ok(())).await;
            }
            ProviderMessage::ClientConnectedNotify(message) => {
                self.emit_endpoint(
                    "provider",
                    "client-connected",
                    json!({
                        "connection_id": message.inner.connection_id,
                        "endpoint_id": message.inner.endpoint_id.map(|id| id.to_string()),
                    }),
                );
                if let Some(endpoint_id) = message.inner.endpoint_id {
                    self.connection_endpoints
                        .lock()
                        .await
                        .insert(message.inner.connection_id, endpoint_id.to_string());
                }
            }
            ProviderMessage::ConnectionClosed(message) => {
                self.connection_endpoints
                    .lock()
                    .await
                    .remove(&message.inner.connection_id);
                self.emit_endpoint(
                    "provider",
                    "connection-closed",
                    json!({ "connection_id": message.inner.connection_id }),
                );
            }
            ProviderMessage::GetRequestReceived(message) => {
                let transfer_id = self.transfer_for_hash(message.inner.request.hash).await;
                if let Some(transfer_id) = transfer_id {
                    let decision = self
                        .access_decision(transfer_id, message.inner.connection_id)
                        .await;
                    if let AccessDecision::Deny { reason } = decision {
                        self.emit_transfer(
                            transfer_id,
                            "send",
                            "access",
                            "request-denied",
                            json!({
                                "connection_id": message.inner.connection_id,
                                "request_id": message.inner.request_id,
                                "reason": reason,
                            }),
                        );
                        let _ = message
                            .tx
                            .send(Err(iroh_blobs::provider::events::AbortReason::Permission))
                            .await;
                        return;
                    }
                    self.track_request_updates(
                        transfer_id,
                        message.inner.connection_id,
                        message.inner.request_id,
                        message.rx,
                    )
                    .await;
                }
                let _ = message.tx.send(Ok(())).await;
            }
            ProviderMessage::GetRequestReceivedNotify(message) => {
                if let Some(transfer_id) = self.transfer_for_hash(message.inner.request.hash).await
                {
                    self.track_request_updates(
                        transfer_id,
                        message.inner.connection_id,
                        message.inner.request_id,
                        message.rx,
                    )
                    .await;
                }
            }
            ProviderMessage::GetManyRequestReceived(message) => {
                let transfer_id = self
                    .transfer_for_any_hash(&message.inner.request.hashes)
                    .await;
                if let Some(transfer_id) = transfer_id {
                    let decision = self
                        .access_decision(transfer_id, message.inner.connection_id)
                        .await;
                    if let AccessDecision::Deny { reason } = decision {
                        self.emit_transfer(
                            transfer_id,
                            "send",
                            "access",
                            "request-denied",
                            json!({
                                "connection_id": message.inner.connection_id,
                                "request_id": message.inner.request_id,
                                "reason": reason,
                            }),
                        );
                        let _ = message
                            .tx
                            .send(Err(iroh_blobs::provider::events::AbortReason::Permission))
                            .await;
                        return;
                    }
                    self.track_request_updates(
                        transfer_id,
                        message.inner.connection_id,
                        message.inner.request_id,
                        message.rx,
                    )
                    .await;
                }
                let _ = message.tx.send(Ok(())).await;
            }
            ProviderMessage::GetManyRequestReceivedNotify(message) => {
                if let Some(transfer_id) = self
                    .transfer_for_any_hash(&message.inner.request.hashes)
                    .await
                {
                    self.track_request_updates(
                        transfer_id,
                        message.inner.connection_id,
                        message.inner.request_id,
                        message.rx,
                    )
                    .await;
                }
            }
            ProviderMessage::ObserveRequestReceived(message) => {
                self.emit_endpoint(
                    "provider",
                    "observe-request",
                    json!({
                        "connection_id": message.inner.connection_id,
                        "request_id": message.inner.request_id,
                    }),
                );
                let _ = message.tx.send(Ok(())).await;
            }
            ProviderMessage::ObserveRequestReceivedNotify(message) => {
                self.emit_endpoint(
                    "provider",
                    "observe-request",
                    json!({
                        "connection_id": message.inner.connection_id,
                        "request_id": message.inner.request_id,
                    }),
                );
            }
            ProviderMessage::Throttle(message) => {
                self.emit_endpoint(
                    "provider",
                    "throttle-request",
                    json!({
                        "connection_id": message.inner.connection_id,
                        "request_id": message.inner.request_id,
                        "size": message.inner.size,
                    }),
                );
                let _ = message.tx.send(Ok(())).await;
            }
            other => {
                self.emit_endpoint(
                    "provider",
                    "provider-message",
                    json!({ "debug": format!("{other:?}") }),
                );
            }
        }
    }

    pub(super) async fn transfer_for_hash(&self, hash: Hash) -> Option<u64> {
        self.hash_to_transfer
            .lock()
            .await
            .get(&hash.to_string())
            .copied()
    }

    pub(super) async fn transfer_for_any_hash(&self, hashes: &[Hash]) -> Option<u64> {
        let map = self.hash_to_transfer.lock().await;
        hashes
            .iter()
            .find_map(|hash| map.get(&hash.to_string()).copied())
    }

    pub(super) async fn access_decision(
        &self,
        transfer_id: u64,
        connection_id: u64,
    ) -> AccessDecision {
        let endpoint_id = self
            .connection_endpoints
            .lock()
            .await
            .get(&connection_id)
            .cloned();
        self.access_policy
            .decide(transfer_id, endpoint_id.as_deref())
            .await
    }

    pub(super) async fn track_request_updates(
        self: &Arc<Self>,
        transfer_id: u64,
        connection_id: u64,
        request_id: u64,
        mut rx: irpc::channel::mpsc::Receiver<RequestUpdate>,
    ) {
        // Request update tasks are tied to individual provider streams.  Router
        // shutdown closes those streams; only the long-lived provider receiver
        // is tracked directly for explicit shutdown.
        //
        // Attach the remote endpoint id when known so the send UI can attribute
        // byte progress to a specific receiver (not just an opaque connection).
        let endpoint_id = self
            .connection_endpoints
            .lock()
            .await
            .get(&connection_id)
            .cloned();
        let core = self.clone();
        tokio::spawn(async move {
            while let Ok(Some(update)) = rx.recv().await {
                match update {
                    RequestUpdate::Started(started) => core.emit_transfer(
                        transfer_id,
                        "send",
                        "transfer",
                        "started",
                        json!({
                            "connection_id": connection_id,
                            "request_id": request_id,
                            "endpoint_id": endpoint_id,
                            "hash": started.hash.to_string(),
                            "size": started.size,
                            "index": started.index,
                        }),
                    ),
                    RequestUpdate::Progress(progress) => core.emit_transfer(
                        transfer_id,
                        "send",
                        "transfer",
                        "progress",
                        json!({
                            "connection_id": connection_id,
                            "request_id": request_id,
                            "endpoint_id": endpoint_id,
                            "end_offset": progress.end_offset,
                        }),
                    ),
                    RequestUpdate::Completed(_) => core.emit_transfer(
                        transfer_id,
                        "send",
                        "transfer",
                        "completed",
                        json!({
                            "connection_id": connection_id,
                            "request_id": request_id,
                            "endpoint_id": endpoint_id,
                        }),
                    ),
                    RequestUpdate::Aborted(_) => core.emit_transfer(
                        transfer_id,
                        "send",
                        "transfer",
                        "aborted",
                        json!({
                            "connection_id": connection_id,
                            "request_id": request_id,
                            "endpoint_id": endpoint_id,
                        }),
                    ),
                }
            }
        });
    }
}
