use std::{io, path::Path, str::FromStr};

#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;

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
            tokio::fs::write(&path, HEXLOWER.encode(&secret.to_bytes())).await?;
            restrict_permissions(&path).await?;
            Ok(secret)
        }
        Err(error) => Err(error.into()),
    }
}

async fn restrict_permissions(path: &Path) -> Result<()> {
    #[cfg(unix)]
    tokio::fs::set_permissions(path, std::fs::Permissions::from_mode(0o600)).await?;
    Ok(())
}
