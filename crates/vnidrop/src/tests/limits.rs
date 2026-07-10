use crate::api::CoreLimits;

#[test]
fn default_limits_are_valid() {
    CoreLimits::default().validate().unwrap();
}

#[test]
fn zero_limit_is_rejected() {
    let limits = CoreLimits {
        max_sources: 0,
        ..CoreLimits::default()
    };
    assert!(limits.validate().is_err());
}
