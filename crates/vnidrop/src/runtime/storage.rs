use std::path::{Path, PathBuf};

use anyhow::Result;

use super::CoreInner;
use crate::api::CoreStorageUsage;

impl CoreInner {
    pub(super) async fn storage_usage(&self) -> Result<CoreStorageUsage> {
        let app_data_dir = self.app_data_dir.clone();
        tokio::task::spawn_blocking(move || scan_storage(&app_data_dir)).await?
    }
}

fn scan_storage(app_data_dir: &Path) -> Result<CoreStorageUsage> {
    let blob_store_bytes = directory_size(&app_data_dir.join("blobs"))?;
    let logs_bytes = directory_size(&app_data_dir.join("logs"))?;
    let previews_bytes = directory_size(&app_data_dir.join("ui").join("previews"))?;
    let database_bytes = [
        "vnidrop.sqlite3",
        "vnidrop.sqlite3-wal",
        "vnidrop.sqlite3-shm",
    ]
    .into_iter()
    .try_fold(0u64, |total, name| {
        Ok::<_, std::io::Error>(total.saturating_add(file_size(&app_data_dir.join(name))?))
    })?;
    let total = directory_size(app_data_dir)?;
    let classified = blob_store_bytes
        .saturating_add(logs_bytes)
        .saturating_add(previews_bytes)
        .saturating_add(database_bytes);
    Ok(CoreStorageUsage {
        blob_store_bytes,
        database_bytes,
        logs_bytes,
        previews_bytes,
        other_core_bytes: total.saturating_sub(classified),
    })
}

fn directory_size(path: &Path) -> Result<u64> {
    if !path.exists() {
        return Ok(0);
    }
    let mut total = 0u64;
    let mut pending = vec![PathBuf::from(path)];
    while let Some(directory) = pending.pop() {
        for entry in std::fs::read_dir(directory)? {
            let entry = entry?;
            let metadata = entry.metadata()?;
            if metadata.is_dir() {
                pending.push(entry.path());
            } else if metadata.is_file() {
                total = total.saturating_add(metadata.len());
            }
        }
    }
    Ok(total)
}

fn file_size(path: &Path) -> std::io::Result<u64> {
    match std::fs::metadata(path) {
        Ok(metadata) if metadata.is_file() => Ok(metadata.len()),
        Ok(_) => Ok(0),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(0),
        Err(error) => Err(error),
    }
}
