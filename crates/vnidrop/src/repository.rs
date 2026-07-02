use std::{path::Path, str::FromStr};

use anyhow::Result;
use sqlx::{
    sqlite::{SqliteConnectOptions, SqlitePoolOptions},
    Row, SqlitePool,
};

use crate::api::{CoreEvent, StoredTransfer};
use crate::util::now_ms;

#[derive(Debug, Clone)]
pub(crate) struct Repository {
    pool: SqlitePool,
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
        Ok(())
    }

    pub(crate) async fn upsert_transfer(
        &self,
        transfer_id: u64,
        direction: &str,
        status: &str,
        transfer_name: Option<&str>,
        content_hash: Option<&str>,
        ticket: Option<&str>,
        file_count: u64,
        total_size: u64,
    ) -> Result<()> {
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
        .bind(transfer_id as i64)
        .bind(direction)
        .bind(status)
        .bind(transfer_name)
        .bind(content_hash)
        .bind(ticket)
        .bind(file_count as i64)
        .bind(total_size as i64)
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
