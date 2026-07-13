use std::sync::atomic::Ordering;

use anyhow::Result;
use serde_json::json;

use super::CoreInner;
use crate::{
    access_policy::mode_to_storage,
    api::{RuntimeStatus, TransferAccessMode},
    transfer_state::{ReceiverRequestStatus, TransferDirection, TransferStatus},
};

impl CoreInner {
    pub(super) async fn status(&self) -> RuntimeStatus {
        let active_transfers = self
            .active_transfers
            .lock()
            .expect("active_transfers")
            .len() as u64;
        let active_shares = self.active_shares.lock().await.len() as u64;
        RuntimeStatus {
            endpoint_id: self.endpoint.id().to_string(),
            addr: format!("{:?}", self.endpoint.addr()),
            active_transfers,
            active_shares,
        }
    }

    /// Remove an in-flight transfer and fire its cancel oneshot synchronously.
    pub(super) fn take_active_transfer(&self, transfer_id: u64) -> Option<TransferDirection> {
        let active = self
            .active_transfers
            .lock()
            .expect("active_transfers")
            .remove(&transfer_id)?;
        let direction = active.direction;
        let _ = active.cancel.send(());
        Some(direction)
    }

    /// Stop a live share or report missing when no active transfer was found.
    pub(super) async fn cancel_idle_or_share(&self, transfer_id: u64) -> Result<()> {
        let mut active_shares = self.active_shares.lock().await;
        if active_shares.contains_key(&transfer_id) {
            self.repository
                .transition_transfer_status(
                    transfer_id,
                    TransferStatus::Sharing,
                    TransferStatus::Stopped,
                )
                .await?;
            active_shares.remove(&transfer_id);
            drop(active_shares);
            self.unregister_transfer_hashes(transfer_id).await;
            self.access_policy.remove_transfer(transfer_id).await;
            self.emit_transfer(transfer_id, "send", "lifecycle", "share-stopped", json!({}));
            return Ok(());
        }
        anyhow::bail!("transfer not found")
    }

    pub(super) async fn delete_transfer(&self, transfer_id: u64) -> Result<()> {
        if self
            .active_transfers
            .lock()
            .expect("active_transfers")
            .contains_key(&transfer_id)
        {
            anyhow::bail!("an active transfer must finish or be cancelled before deletion");
        }

        let transfer = self
            .repository
            .list_transfers()
            .await?
            .into_iter()
            .find(|transfer| transfer.transfer_id == transfer_id)
            .ok_or_else(|| anyhow::anyhow!("transfer not found"))?;

        // Revoke a live share durably before removing its history. If the
        // subsequent delete fails, a restart must never expose it again.
        if transfer.status == TransferStatus::Sharing.as_str() {
            self.repository
                .transition_transfer_status(
                    transfer_id,
                    TransferStatus::Sharing,
                    TransferStatus::Stopped,
                )
                .await?;
        }

        for request in self.repository.list_receiver_requests(transfer_id).await? {
            if request.status == ReceiverRequestStatus::Requested.as_str() {
                let _ = self
                    .approval
                    .respond(
                        request.id,
                        false,
                        Some("transfer deleted by sender".to_string()),
                    )
                    .await;
            }
        }

        self.active_shares.lock().await.remove(&transfer_id);
        self.unregister_transfer_hashes(transfer_id).await;
        self.access_policy.remove_transfer(transfer_id).await;
        // Events are persisted asynchronously. Drain events emitted before this
        // request so none can be written back after the transfer is deleted.
        self.event_hub.flush().await;
        self.repository.delete_transfer(transfer_id).await
    }

    pub(super) async fn delete_receive_history(&self) -> Result<u64> {
        // Transfer events are persisted on a background task. Drain everything
        // emitted before this request so cleared history cannot be reinserted
        // after the repository transaction commits.
        self.event_hub.flush().await;
        self.repository.delete_receive_history().await
    }

    pub(super) async fn set_transfer_access_mode(
        &self,
        transfer_id: u64,
        mode: TransferAccessMode,
    ) -> Result<()> {
        self.repository
            .update_active_share_access_mode(transfer_id, mode_to_storage(&mode))
            .await?;
        self.access_policy.set_mode(transfer_id, mode.clone()).await;
        self.emit_transfer(
            transfer_id,
            "send",
            "access",
            "mode-updated",
            json!({ "mode": format!("{mode:?}") }),
        );
        Ok(())
    }

    pub(super) async fn approve_endpoint_for_transfer(
        &self,
        transfer_id: u64,
        endpoint_id: String,
    ) -> Result<()> {
        self.access_policy
            .approve_endpoint(transfer_id, endpoint_id.clone())
            .await;
        self.emit_transfer(
            transfer_id,
            "send",
            "access",
            "endpoint-approved",
            json!({ "endpoint_id": endpoint_id }),
        );
        Ok(())
    }

    pub(super) async fn shutdown(&self) {
        if self.shutdown_started.swap(true, Ordering::SeqCst) {
            return;
        }
        self.emit_endpoint("shutdown", "service-shutdown", json!({}));
        // Flush before stopping the router so the app can show the shutdown
        // event even if the process exits soon after Compose disposes the core.
        self.event_hub.flush().await;
        if let Err(error) = self.router.shutdown().await {
            self.emit_endpoint(
                "shutdown",
                "router-error",
                json!({ "error": error.to_string() }),
            );
        }
        if let Some(task) = self.provider_task.lock().await.take() {
            task.abort();
            let _ = task.await;
        }
        self.event_hub.shutdown().await;
    }
}
