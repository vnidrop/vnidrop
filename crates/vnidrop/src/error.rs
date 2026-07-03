use std::io;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum VnidropError {
    #[error("initialization failed: {reason}")]
    Initialization { reason: String },
    #[error("ticket error: {reason}")]
    Ticket { reason: String },
    #[error("filesystem error: {reason}")]
    Filesystem { reason: String },
    #[error("transfer error: {reason}")]
    Transfer { reason: String },
    #[error("permission error: {reason}")]
    Permission { reason: String },
    #[error("repository error: {reason}")]
    Repository { reason: String },
    #[error("internal error: {reason}")]
    Internal { reason: String },
}

impl VnidropError {
    pub(crate) fn initialization(error: impl Into<anyhow::Error>) -> Self {
        Self::Initialization {
            reason: error.into().to_string(),
        }
    }

    pub(crate) fn ticket(error: impl Into<anyhow::Error>) -> Self {
        Self::Ticket {
            reason: error.into().to_string(),
        }
    }

    pub(crate) fn filesystem(error: impl Into<anyhow::Error>) -> Self {
        Self::Filesystem {
            reason: error.into().to_string(),
        }
    }

    pub(crate) fn transfer(error: impl Into<anyhow::Error>) -> Self {
        Self::Transfer {
            reason: error.into().to_string(),
        }
    }

    pub(crate) fn permission(error: impl Into<anyhow::Error>) -> Self {
        Self::Permission {
            reason: error.into().to_string(),
        }
    }

    pub(crate) fn repository(error: impl Into<anyhow::Error>) -> Self {
        Self::Repository {
            reason: error.into().to_string(),
        }
    }
}

impl From<anyhow::Error> for VnidropError {
    fn from(error: anyhow::Error) -> Self {
        Self::Internal {
            reason: error.to_string(),
        }
    }
}

impl From<io::Error> for VnidropError {
    fn from(error: io::Error) -> Self {
        Self::Filesystem {
            reason: error.to_string(),
        }
    }
}
