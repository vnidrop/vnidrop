mod access_policy;
mod api;
mod error;
mod filesystem;
mod logging;
mod repository;
mod runtime;
mod secret;
mod ticket;
mod util;

pub use api::{
    CoreEvent, CoreEventSink, RuntimeStatus, ShareMetadataInput, ShareResult, ShareSource,
    SourceKind, StoredTransfer, TicketInspection, TransferAccessMode, TransferMetadata,
};
pub use error::VnidropError;
pub use runtime::VnidropCore;

uniffi::setup_scaffolding!();

#[cfg(test)]
mod tests;
