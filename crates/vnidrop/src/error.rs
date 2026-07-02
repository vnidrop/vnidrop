use std::io;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum VnidropError {
    #[error("{reason}")]
    Generic { reason: String },
}

impl From<anyhow::Error> for VnidropError {
    fn from(error: anyhow::Error) -> Self {
        Self::Generic {
            reason: error.to_string(),
        }
    }
}

impl From<io::Error> for VnidropError {
    fn from(error: io::Error) -> Self {
        Self::Generic {
            reason: error.to_string(),
        }
    }
}
