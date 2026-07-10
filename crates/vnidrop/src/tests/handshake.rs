use crate::handshake::HandshakeResponse;

#[test]
fn malformed_handshake_response_is_rejected() {
    for payload in [
        r#"{"Approved":{"token":7,"expires_at":"later"}}"#,
        r#"{"Denied":{}}"#,
        r#"{"Unknown":{"reason":"no"}}"#,
        "not-json",
    ] {
        assert!(serde_json::from_str::<HandshakeResponse>(payload).is_err());
    }
}
