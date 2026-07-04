use std::{path::Path, str::FromStr};

use anyhow::Result;
use sqlx::{
    sqlite::{SqliteConnectOptions, SqlitePoolOptions},
    Row, SqlitePool,
};

use crate::api::{CoreEvent, ReceiverRequest, StoredTransfer};
use crate::util::now_ms;

const SCHEMA_VERSION: i64 = 1;

#[derive(Debug, Clone)]
pub(crate) struct Repository {
    pool: SqlitePool,
}

pub(crate) struct TransferUpsert<'a> {
    pub(crate) transfer_id: u64,
    pub(crate) direction: &'a str,
    pub(crate) status: &'a str,
    pub(crate) transfer_name: Option<&'a str>,
    pub(crate) content_hash: Option<&'a str>,
    pub(crate) ticket: Option<&'a str>,
    pub(crate) file_count: u64,
    pub(crate) total_size: u64,
}

pub(crate) struct ReceiverRequestInsert<'a> {
    pub(crate) id: &'a str,
    pub(crate) transfer_id: u64,
    pub(crate) remote_endpoint_id: &'a str,
    pub(crate) transfer_name: &'a str,
    pub(crate) receiver_name: Option<&'a str>,
    pub(crate) receiver_device_name: Option<&'a str>,
    pub(crate) app_version: &'a str,
}

impl Repository {
    pub(crate) async fn open(app_data_dir: &Path) -> Result<Self> {
        let db_path = app_data_dir.join("vnidrop.sqlite3");
        let options = SqliteConnectOptions::from_str("sqlite://")?
            .filename(db_path)
            .create_if_missing(true);
        let pool = SqlitePoolOptions::new()
            .max_connections(4)
            .connect_with(options)
            .await?;
        let repository = Self { pool };
        repository.ensure_schema().await?;
        Ok(repository)
    }

    async fn ensure_schema(&self) -> Result<()> {
        // The app owns this SQLite file.  Keep migrations explicit so future
        // desktop/mobile releases can move user history forward in place.
        sqlx::query(
            r#"
            CREATE TABLE IF NOT EXISTS transfers (
                transfer_id INTEGER PRIMARY KEY,
                direction TEXT NOT NULL,
                status TEXT NOT NULL,
                transfer_name TEXT,
                content_hash TEXT,
                ticket TEXT,
                file_count INTEGER NOT NULL DEFAULT 0,
                total_size INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            "#,
        )
        .execute(&self.pool)
        .await?;

        sqlx::query(
            r#"
            CREATE TABLE IF NOT EXISTS transfer_events (
                id TEXT PRIMARY KEY,
                timestamp INTEGER NOT NULL,
                scope TEXT NOT NULL,
                transfer_id INTEGER,
                direction TEXT,
                phase TEXT NOT NULL,
                kind TEXT NOT NULL,
                data_json TEXT NOT NULL
            );
            "#,
        )
        .execute(&self.pool)
        .await?;

        sqlx::query(
            "CREATE INDEX IF NOT EXISTS idx_transfer_events_transfer_id ON transfer_events(transfer_id, timestamp);",
        )
        .execute(&self.pool)
        .await?;

        sqlx::query(
            r#"
            CREATE TABLE IF NOT EXISTS receiver_requests (
                id TEXT PRIMARY KEY,
                transfer_id INTEGER NOT NULL,
                remote_endpoint_id TEXT NOT NULL,
                transfer_name TEXT NOT NULL,
                receiver_name TEXT,
                receiver_device_name TEXT,
                app_version TEXT NOT NULL,
                status TEXT NOT NULL,
                reason TEXT,
                requested_at INTEGER NOT NULL,
                responded_at INTEGER
            );
            "#,
        )
        .execute(&self.pool)
        .await?;

        sqlx::query(
            "CREATE INDEX IF NOT EXISTS idx_receiver_requests_transfer_id ON receiver_requests(transfer_id, requested_at DESC);",
        )
        .execute(&self.pool)
        .await?;

        sqlx::query(&format!("PRAGMA user_version = {SCHEMA_VERSION}"))
            .execute(&self.pool)
            .await?;
        Ok(())
    }

    #[cfg(test)]
    pub(crate) async fn schema_version(&self) -> Result<i64> {
        let row = sqlx::query("PRAGMA user_version")
            .fetch_one(&self.pool)
            .await?;
        Ok(row.get(0))
    }

    pub(crate) async fn upsert_transfer(&self, transfer: TransferUpsert<'_>) -> Result<()> {
        let now = now_ms();
        sqlx::query(
            r#"
            INSERT INTO transfers (
                transfer_id, direction, status, transfer_name, content_hash, ticket,
                file_count, total_size, created_at, updated_at
            )
            VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?9)
            ON CONFLICT(transfer_id) DO UPDATE SET
                direction = excluded.direction,
                status = excluded.status,
                transfer_name = excluded.transfer_name,
                content_hash = excluded.content_hash,
                ticket = excluded.ticket,
                file_count = excluded.file_count,
                total_size = excluded.total_size,
                updated_at = excluded.updated_at;
            "#,
        )
        .bind(transfer.transfer_id as i64)
        .bind(transfer.direction)
        .bind(transfer.status)
        .bind(transfer.transfer_name)
        .bind(transfer.content_hash)
        .bind(transfer.ticket)
        .bind(transfer.file_count as i64)
        .bind(transfer.total_size as i64)
        .bind(now)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    pub(crate) async fn update_transfer_status(
        &self,
        transfer_id: u64,
        status: &str,
    ) -> Result<()> {
        sqlx::query("UPDATE transfers SET status = ?1, updated_at = ?2 WHERE transfer_id = ?3")
            .bind(status)
            .bind(now_ms())
            .bind(transfer_id as i64)
            .execute(&self.pool)
            .await?;
        Ok(())
    }

    pub(crate) async fn insert_event(&self, event: &CoreEvent) -> Result<()> {
        sqlx::query(
            r#"
            INSERT OR REPLACE INTO transfer_events (
                id, timestamp, scope, transfer_id, direction, phase, kind, data_json
            )
            VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8);
            "#,
        )
        .bind(&event.id)
        .bind(event.timestamp)
        .bind(&event.scope)
        .bind(event.transfer_id.map(|value| value as i64))
        .bind(&event.direction)
        .bind(&event.phase)
        .bind(&event.kind)
        .bind(&event.data_json)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    pub(crate) async fn insert_receiver_request(
        &self,
        request: ReceiverRequestInsert<'_>,
    ) -> Result<()> {
        sqlx::query(
            r#"
            INSERT INTO receiver_requests (
                id, transfer_id, remote_endpoint_id, transfer_name,
                receiver_name, receiver_device_name, app_version, status,
                reason, requested_at, responded_at
            )
            VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, 'requested', NULL, ?8, NULL)
            "#,
        )
        .bind(request.id)
        .bind(request.transfer_id as i64)
        .bind(request.remote_endpoint_id)
        .bind(request.transfer_name)
        .bind(request.receiver_name)
        .bind(request.receiver_device_name)
        .bind(request.app_version)
        .bind(now_ms())
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    pub(crate) async fn update_receiver_request_status(
        &self,
        id: &str,
        status: &str,
        reason: Option<&str>,
    ) -> Result<()> {
        let result = sqlx::query(
            r#"
            UPDATE receiver_requests
            SET status = ?1, reason = ?2, responded_at = ?3
            WHERE id = ?4
              AND status = 'requested'
            "#,
        )
        .bind(status)
        .bind(reason)
        .bind(now_ms())
        .bind(id)
        .execute(&self.pool)
        .await?;
        if result.rows_affected() == 0 {
            anyhow::bail!("receiver request not found or already handled");
        }
        Ok(())
    }

    pub(crate) async fn list_receiver_requests(
        &self,
        transfer_id: u64,
    ) -> Result<Vec<ReceiverRequest>> {
        let rows = sqlx::query(
            r#"
            SELECT id, transfer_id, remote_endpoint_id, transfer_name,
                   receiver_name, receiver_device_name, app_version, status,
                   reason, requested_at, responded_at
            FROM receiver_requests
            WHERE transfer_id = ?1
            ORDER BY requested_at DESC
            "#,
        )
        .bind(transfer_id as i64)
        .fetch_all(&self.pool)
        .await?;
        Ok(rows.into_iter().map(row_to_receiver_request).collect())
    }

    pub(crate) async fn send_exists(&self, transfer_id: u64, content_hash: &str) -> Result<bool> {
        let row = sqlx::query(
            r#"
            SELECT EXISTS(
                SELECT 1 FROM transfers
                WHERE transfer_id = ?1
                  AND content_hash = ?2
                  AND direction = 'send'
                  AND status = 'sharing'
            )
            "#,
        )
        .bind(transfer_id as i64)
        .bind(content_hash)
        .fetch_one(&self.pool)
        .await?;
        Ok(row.get::<i64, _>(0) != 0)
    }

    pub(crate) async fn list_transfers(&self) -> Result<Vec<StoredTransfer>> {
        let rows = sqlx::query(
            r#"
            SELECT transfer_id, direction, status, transfer_name, content_hash, ticket,
                   file_count, total_size, created_at, updated_at
            FROM transfers
            ORDER BY updated_at DESC
            "#,
        )
        .fetch_all(&self.pool)
        .await?;
        Ok(rows.into_iter().map(row_to_transfer).collect())
    }

    pub(crate) async fn list_events(&self, transfer_id: Option<u64>) -> Result<Vec<CoreEvent>> {
        let rows = if let Some(transfer_id) = transfer_id {
            sqlx::query(
                r#"
                SELECT id, timestamp, scope, transfer_id, direction, phase, kind, data_json
                FROM transfer_events
                WHERE transfer_id = ?1
                ORDER BY timestamp ASC
                "#,
            )
            .bind(transfer_id as i64)
            .fetch_all(&self.pool)
            .await?
        } else {
            sqlx::query(
                r#"
                SELECT id, timestamp, scope, transfer_id, direction, phase, kind, data_json
                FROM transfer_events
                ORDER BY timestamp DESC
                LIMIT 500
                "#,
            )
            .fetch_all(&self.pool)
            .await?
        };
        Ok(rows.into_iter().map(row_to_event).collect())
    }
}

fn row_to_transfer(row: sqlx::sqlite::SqliteRow) -> StoredTransfer {
    StoredTransfer {
        transfer_id: row.get::<i64, _>("transfer_id") as u64,
        direction: row.get("direction"),
        status: row.get("status"),
        transfer_name: row.get("transfer_name"),
        content_hash: row.get("content_hash"),
        ticket: row.get("ticket"),
        file_count: row.get::<i64, _>("file_count") as u64,
        total_size: row.get::<i64, _>("total_size") as u64,
        created_at: row.get("created_at"),
        updated_at: row.get("updated_at"),
    }
}

fn row_to_event(row: sqlx::sqlite::SqliteRow) -> CoreEvent {
    CoreEvent {
        id: row.get("id"),
        timestamp: row.get("timestamp"),
        scope: row.get("scope"),
        transfer_id: row
            .get::<Option<i64>, _>("transfer_id")
            .map(|value| value as u64),
        direction: row.get("direction"),
        phase: row.get("phase"),
        kind: row.get("kind"),
        data_json: row.get("data_json"),
    }
}

fn row_to_receiver_request(row: sqlx::sqlite::SqliteRow) -> ReceiverRequest {
    ReceiverRequest {
        id: row.get("id"),
        transfer_id: row.get::<i64, _>("transfer_id") as u64,
        remote_endpoint_id: row.get("remote_endpoint_id"),
        transfer_name: row.get("transfer_name"),
        receiver_name: row.get("receiver_name"),
        receiver_device_name: row.get("receiver_device_name"),
        app_version: row.get("app_version"),
        status: row.get("status"),
        reason: row.get("reason"),
        requested_at: row.get("requested_at"),
        responded_at: row.get("responded_at"),
    }
}
