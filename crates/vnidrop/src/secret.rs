use std::{
    fs::OpenOptions,
    io::{self, Write},
    path::Path,
    str::FromStr,
};

#[cfg(unix)]
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};

use anyhow::{Context, Result};
use data_encoding::HEXLOWER;
use iroh::SecretKey;

pub(crate) async fn load_or_create_secret(app_data_dir: &Path) -> Result<SecretKey> {
    if let Ok(secret) = std::env::var("IROH_SECRET") {
        return SecretKey::from_str(&secret).context("invalid IROH_SECRET");
    }

    let path = app_data_dir.join("iroh.secret");
    match tokio::fs::read_to_string(&path).await {
        Ok(secret) => {
            let bytes = HEXLOWER
                .decode(secret.trim().as_bytes())
                .context("invalid persisted iroh secret encoding")?;
            let bytes: [u8; 32] = bytes
                .try_into()
                .map_err(|_| anyhow::anyhow!("invalid persisted iroh secret length"))?;
            restrict_permissions(&path).await?;
            Ok(SecretKey::from_bytes(&bytes))
        }
        Err(error) if error.kind() == io::ErrorKind::NotFound => {
            let secret = SecretKey::generate();
            let encoded = HEXLOWER.encode(&secret.to_bytes());
            // Create with owner-only mode on Unix so the key is never briefly 0644.
            write_secret_file(&path, encoded.as_bytes()).await?;
            restrict_permissions(&path).await?;
            Ok(secret)
        }
        Err(error) => Err(error.into()),
    }
}

async fn write_secret_file(path: &Path, bytes: &[u8]) -> Result<()> {
    let path = path.to_path_buf();
    let bytes = bytes.to_vec();
    tokio::task::spawn_blocking(move || {
        let mut options = OpenOptions::new();
        options.write(true).create_new(true);
        #[cfg(unix)]
        options.mode(0o600);
        let mut file = options
            .open(&path)
            .with_context(|| format!("failed to create {}", path.display()))?;
        file.write_all(&bytes)
            .with_context(|| format!("failed to write {}", path.display()))?;
        file.sync_all()
            .with_context(|| format!("failed to sync {}", path.display()))?;
        Ok::<(), anyhow::Error>(())
    })
    .await?
}

async fn restrict_permissions(path: &Path) -> Result<()> {
    #[cfg(unix)]
    tokio::fs::set_permissions(path, std::fs::Permissions::from_mode(0o600)).await?;
    // Windows: file lives under the user profile app-data dir with default ACLs
    // limited to the current user. No portable owner-only API in std.
    let _ = path;
    Ok(())
}
