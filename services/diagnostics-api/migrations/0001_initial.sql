-- This migration also baselines databases previously initialized by schema.sql.
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS event_batches (
  id TEXT PRIMARY KEY NOT NULL,
  received_at INTEGER NOT NULL,
  install_id TEXT NOT NULL,
  app_version TEXT,
  platform TEXT,
  event_count INTEGER NOT NULL,
  payload_json TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_event_batches_received ON event_batches (received_at);
CREATE INDEX IF NOT EXISTS idx_event_batches_install ON event_batches (install_id);

CREATE TABLE IF NOT EXISTS crashes (
  id TEXT PRIMARY KEY NOT NULL,
  received_at INTEGER NOT NULL,
  occurred_at INTEGER NOT NULL,
  install_id TEXT NOT NULL,
  app_version TEXT,
  platform TEXT,
  exception_type TEXT,
  exception_message TEXT,
  fingerprint TEXT NOT NULL,
  diagnostics_enabled INTEGER NOT NULL DEFAULT 0,
  stack_r2_key TEXT,
  breadcrumbs_json TEXT,
  schema_version INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_crashes_received ON crashes (received_at);
CREATE INDEX IF NOT EXISTS idx_crashes_fingerprint ON crashes (fingerprint);
CREATE INDEX IF NOT EXISTS idx_crashes_install ON crashes (install_id);

CREATE TABLE IF NOT EXISTS bugs (
  id TEXT PRIMARY KEY NOT NULL,
  received_at INTEGER NOT NULL,
  install_id TEXT NOT NULL,
  app_version TEXT,
  platform TEXT,
  what_happened TEXT NOT NULL,
  expected TEXT NOT NULL,
  steps TEXT,
  contact TEXT,
  logs_r2_key TEXT,
  device_json TEXT,
  breadcrumbs_json TEXT,
  status TEXT NOT NULL DEFAULT 'open',
  schema_version INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_bugs_received ON bugs (received_at);
CREATE INDEX IF NOT EXISTS idx_bugs_status ON bugs (status);
