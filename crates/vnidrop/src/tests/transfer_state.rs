use crate::transfer_state::{TransferDirection, TransferStatus};

#[test]
fn parses_only_known_persisted_values() {
    assert_eq!(
        TransferDirection::try_from("send").unwrap(),
        TransferDirection::Send
    );
    assert_eq!(
        TransferStatus::try_from("receiving").unwrap(),
        TransferStatus::Receiving
    );
    assert!(TransferDirection::try_from("sideways").is_err());
    assert!(TransferStatus::try_from("pending-ish").is_err());
}

#[test]
fn permits_only_defined_lifecycle_transitions() {
    assert!(TransferStatus::Importing.can_transition_to(TransferStatus::Sharing));
    assert!(TransferStatus::Importing.can_transition_to(TransferStatus::Failed));
    assert!(TransferStatus::Importing.can_transition_to(TransferStatus::Cancelled));
    assert!(TransferStatus::Sharing.can_transition_to(TransferStatus::Stopped));
    assert!(TransferStatus::Sharing.can_transition_to(TransferStatus::Failed));
    assert!(TransferStatus::Receiving.can_transition_to(TransferStatus::Done));
    assert!(TransferStatus::Receiving.can_transition_to(TransferStatus::Failed));
    assert!(TransferStatus::Receiving.can_transition_to(TransferStatus::Cancelled));

    assert!(!TransferStatus::Sharing.can_transition_to(TransferStatus::Done));
    assert!(!TransferStatus::Done.can_transition_to(TransferStatus::Receiving));
    assert!(!TransferStatus::Failed.can_transition_to(TransferStatus::Sharing));
}
