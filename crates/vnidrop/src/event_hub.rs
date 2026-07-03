use std::sync::{Arc, Mutex};

use serde_json::Value;
use tokio::{
    sync::{mpsc, oneshot, Mutex as TokioMutex},
    task::JoinHandle,
};

use crate::{
    api::{CoreEvent, CoreEventSink},
    repository::Repository,
    util::now_ms,
};

enum EventCommand {
    Persist(CoreEvent),
    Flush(oneshot::Sender<()>),
    Shutdown(oneshot::Sender<()>),
}

pub(crate) struct EventHub {
    sink: Arc<dyn CoreEventSink>,
    tx: mpsc::UnboundedSender<EventCommand>,
    join: TokioMutex<Option<JoinHandle<()>>>,
    sequence: Mutex<u64>,
}

impl EventHub {
    pub(crate) fn start(repository: Repository, sink: Arc<dyn CoreEventSink>) -> Self {
        let (tx, mut rx) = mpsc::unbounded_channel();
        let join = tokio::spawn(async move {
            while let Some(command) = rx.recv().await {
                match command {
                    EventCommand::Persist(event) => {
                        if let Err(error) = repository.insert_event(&event).await {
                            tracing::warn!(%error, event_id = %event.id, "failed to persist core event");
                        }
                    }
                    EventCommand::Flush(done) => {
                        let _ = done.send(());
                    }
                    EventCommand::Shutdown(done) => {
                        let _ = done.send(());
                        break;
                    }
                }
            }
        });

        Self {
            sink,
            tx,
            join: TokioMutex::new(Some(join)),
            sequence: Mutex::new(1),
        }
    }

    pub(crate) fn emit_endpoint(&self, phase: &str, kind: &str, data: Value) {
        self.emit("endpoint", None, None, phase, kind, data);
    }

    pub(crate) fn emit_transfer(
        &self,
        transfer_id: u64,
        direction: &str,
        phase: &str,
        kind: &str,
        data: Value,
    ) {
        self.emit(
            "transfer",
            Some(transfer_id),
            Some(direction.to_string()),
            phase,
            kind,
            data,
        );
    }

    pub(crate) async fn flush(&self) {
        let (tx, rx) = oneshot::channel();
        if self.tx.send(EventCommand::Flush(tx)).is_ok() {
            let _ = rx.await;
        }
    }

    pub(crate) async fn shutdown(&self) {
        let (tx, rx) = oneshot::channel();
        if self.tx.send(EventCommand::Shutdown(tx)).is_ok() {
            let _ = rx.await;
        }
        if let Some(join) = self.join.lock().await.take() {
            let _ = join.await;
        }
    }

    fn emit(
        &self,
        scope: &str,
        transfer_id: Option<u64>,
        direction: Option<String>,
        phase: &str,
        kind: &str,
        data: Value,
    ) {
        let timestamp = now_ms();
        let mut sequence = self.sequence.lock().expect("event sequence lock poisoned");
        let id = format!("{timestamp}-{}", *sequence);
        *sequence += 1;
        drop(sequence);

        // Compose observes this event synchronously, while SQLite persistence is
        // serialized through the queue.  That keeps the UI responsive without
        // losing the ability to flush persisted history during shutdown/tests.
        let event = CoreEvent {
            id,
            timestamp,
            scope: scope.to_string(),
            transfer_id,
            direction,
            phase: phase.to_string(),
            kind: kind.to_string(),
            data_json: data.to_string(),
        };
        if self.tx.send(EventCommand::Persist(event.clone())).is_err() {
            tracing::warn!(event_id = %event.id, "event persistence queue is closed");
        }
        self.sink.on_event(event);
    }
}
