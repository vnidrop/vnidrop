use std::time::Duration;

use crate::{
    api::RelayMode,
    runtime::{relay_mode_label, relay_wait_timeout},
};

fn custom(urls: &[&str]) -> RelayMode {
    RelayMode::Custom {
        urls: urls.iter().map(|url| url.to_string()).collect(),
    }
}

#[test]
fn default_mode_keeps_existing_behaviour() {
    assert_eq!(RelayMode::default(), RelayMode::Default);
    RelayMode::default().validate().unwrap();
}

#[test]
fn disabled_mode_needs_no_urls() {
    RelayMode::Disabled.validate().unwrap();
}

#[test]
fn custom_mode_accepts_https_urls() {
    let mode = custom(&[
        "https://relay-1.example.org",
        "https://relay-2.example.org:8443",
    ]);
    let parsed = mode.parsed_urls().unwrap();
    assert_eq!(parsed.len(), 2);
}

#[test]
fn custom_mode_trims_surrounding_whitespace() {
    // Pasted URLs routinely arrive with a trailing newline or space.
    let parsed = custom(&["  https://relay.example.org \n"])
        .parsed_urls()
        .unwrap();
    assert_eq!(parsed.len(), 1);
}

#[test]
fn custom_mode_rejects_empty_url_list() {
    let error = custom(&[]).validate().unwrap_err().to_string();
    assert!(error.contains("at least one relay URL"), "{error}");
}

#[test]
fn custom_mode_rejects_unsupported_scheme() {
    // iroh's RelayUrl parser accepts this; the endpoint would then never
    // connect, so validation has to reject it here.
    let error = custom(&["nonsense://relay.example.org"])
        .validate()
        .unwrap_err()
        .to_string();
    assert!(error.contains("unsupported scheme"), "{error}");
}

#[test]
fn custom_mode_rejects_url_without_host() {
    // `https` is a "special" URL scheme, so a missing authority is a parse
    // error rather than an empty host.
    let error = custom(&["https://"]).validate().unwrap_err().to_string();
    assert!(error.contains("is not a valid URL"), "{error}");
}

#[test]
fn custom_mode_rejects_bare_hostname() {
    let error = custom(&["relay.example.org"])
        .validate()
        .unwrap_err()
        .to_string();
    assert!(error.contains("is not a valid URL"), "{error}");
}

#[test]
fn custom_mode_rejects_blank_url() {
    let error = custom(&["   "]).validate().unwrap_err().to_string();
    assert!(error.contains("must not be empty"), "{error}");
}

#[test]
fn custom_mode_reports_the_offending_url() {
    let error = custom(&["https://good.example.org", "ftp://bad.example.org"])
        .validate()
        .unwrap_err()
        .to_string();
    assert!(error.contains("ftp://bad.example.org"), "{error}");
}

/// Regression: `Endpoint::online()` never returns when no relay is configured,
/// so startup must not await it at all under `Disabled`.
#[test]
fn disabled_mode_never_waits_for_a_relay() {
    assert_eq!(relay_wait_timeout(&RelayMode::Disabled), None);
}

/// Regression: a configured-but-unreachable relay hangs `online()` the same
/// way, so relay-using modes must be bounded rather than awaited outright.
#[test]
fn relay_using_modes_are_bounded() {
    let default_wait = relay_wait_timeout(&RelayMode::Default).expect("default mode waits");
    let custom_wait =
        relay_wait_timeout(&custom(&["https://relay.example.org"])).expect("custom mode waits");
    assert_eq!(default_wait, custom_wait);
    assert!(
        default_wait > Duration::ZERO && default_wait <= Duration::from_secs(30),
        "relay wait should be bounded and short, got {default_wait:?}"
    );
}

#[test]
fn mode_labels_are_stable() {
    assert_eq!(relay_mode_label(&RelayMode::Default), "default");
    assert_eq!(relay_mode_label(&RelayMode::Disabled), "disabled");
    assert_eq!(
        relay_mode_label(&custom(&["https://relay.example.org"])),
        "custom"
    );
}
