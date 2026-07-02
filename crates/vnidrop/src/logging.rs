use std::{path::Path, sync::OnceLock};

use anyhow::Result;
use tracing_subscriber::{fmt, layer::SubscriberExt, EnvFilter};

static LOG_GUARD: OnceLock<tracing_appender::non_blocking::WorkerGuard> = OnceLock::new();

pub(crate) fn init_logging(app_data_dir: &Path) -> Result<()> {
    if LOG_GUARD.get().is_some() {
        return Ok(());
    }

    let log_dir = app_data_dir.join("logs");
    std::fs::create_dir_all(&log_dir)?;
    let file_appender = tracing_appender::rolling::daily(log_dir, "vnidrop.log");
    let (writer, guard) = tracing_appender::non_blocking(file_appender);
    let filter = EnvFilter::try_from_default_env()
        .unwrap_or_else(|_| EnvFilter::new("vnidrop=debug,iroh=info,iroh_blobs=info,warn"));

    let subscriber = tracing_subscriber::registry()
        .with(filter)
        .with(fmt::layer().with_writer(writer).with_ansi(false));

    if tracing::subscriber::set_global_default(subscriber).is_ok() {
        let _ = LOG_GUARD.set(guard);
    }

    Ok(())
}
