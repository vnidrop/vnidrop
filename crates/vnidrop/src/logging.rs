use std::{
    fs::{self, OpenOptions},
    io::{self, Write},
    path::{Path, PathBuf},
    sync::OnceLock,
};

use anyhow::Result;
use tracing_subscriber::{fmt, layer::SubscriberExt, EnvFilter};

const LOG_FILE: &str = "vnidrop.log";
const MAX_LOG_BYTES: u64 = 1_048_576;
const MAX_LOG_FILES: usize = 5;

static LOG_GUARD: OnceLock<tracing_appender::non_blocking::WorkerGuard> = OnceLock::new();

pub(crate) fn init_logging(app_data_dir: &Path) -> Result<()> {
    if LOG_GUARD.get().is_some() {
        return Ok(());
    }

    let log_dir = app_data_dir.join("logs");
    fs::create_dir_all(&log_dir)?;
    let writer = SizeRotatingWriter::new(log_dir, MAX_LOG_BYTES, MAX_LOG_FILES);
    let (writer, guard) = tracing_appender::non_blocking(writer);
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

struct SizeRotatingWriter {
    log_dir: PathBuf,
    max_bytes: u64,
    max_files: usize,
}

impl SizeRotatingWriter {
    fn new(log_dir: PathBuf, max_bytes: u64, max_files: usize) -> Self {
        Self {
            log_dir,
            max_bytes,
            max_files,
        }
    }

    fn active_path(&self) -> PathBuf {
        self.log_dir.join(LOG_FILE)
    }

    fn rotated_path(&self, index: usize) -> PathBuf {
        self.log_dir.join(format!("vnidrop.{index}.log"))
    }

    fn rotate_if_needed(&self, incoming_bytes: usize) -> io::Result<()> {
        fs::create_dir_all(&self.log_dir)?;
        let active = self.active_path();
        let current_size = active
            .metadata()
            .map(|metadata| metadata.len())
            .unwrap_or(0);
        if current_size == 0 || current_size + incoming_bytes as u64 <= self.max_bytes {
            return Ok(());
        }

        if self.max_files == 0 {
            let _ = fs::remove_file(active);
            return Ok(());
        }

        let _ = fs::remove_file(self.rotated_path(self.max_files));
        for index in (1..self.max_files).rev() {
            let source = self.rotated_path(index);
            if source.exists() {
                let _ = fs::rename(source, self.rotated_path(index + 1));
            }
        }
        if active.exists() {
            let _ = fs::rename(active, self.rotated_path(1));
        }
        Ok(())
    }
}

impl Write for SizeRotatingWriter {
    fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
        self.rotate_if_needed(buf.len())?;
        let mut file = OpenOptions::new()
            .create(true)
            .append(true)
            .open(self.active_path())?;
        file.write(buf)
    }

    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}
