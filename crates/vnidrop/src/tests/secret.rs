#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;

use crate::secret::load_or_create_secret;

#[tokio::test]
async fn persists_with_restricted_permissions() {
    let temp = tempfile::tempdir().unwrap();
    let first = load_or_create_secret(temp.path()).await.unwrap();
    let second = load_or_create_secret(temp.path()).await.unwrap();
    assert_eq!(first.to_bytes(), second.to_bytes());

    #[cfg(unix)]
    assert_eq!(
        std::fs::metadata(temp.path().join("iroh.secret"))
            .unwrap()
            .permissions()
            .mode()
            & 0o777,
        0o600
    );
}
