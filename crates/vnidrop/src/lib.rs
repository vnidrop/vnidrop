mod access_policy;
mod api;
mod approval;
mod error;
mod event_hub;
mod filesystem;
mod handshake;
mod logging;
mod repository;
mod runtime;
mod secret;
mod ticket;
mod transfer_state;
mod util;

pub use api::{
    default_core_limits, CoreEvent, CoreEventSink, CoreLimits, CoreStorageUsage, PublishedOutput,
    ReceiveOutputSink, ReceiveOutputSinkV2, ReceivedArtifact, ReceivedLocatorKind, ReceiverRequest,
    RuntimeStatus, ShareMetadataInput, ShareResult, ShareSource, SourceKind, StoredTransfer,
    TicketInspection, TransferAccessMode, TransferMetadata,
};
pub use error::VnidropError;
pub use runtime::VnidropCore;

uniffi::setup_scaffolding!();

#[cfg(test)]
#[path = "tests.rs"]
mod core_tests;
