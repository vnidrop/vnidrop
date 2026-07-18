import Foundation
import VnidropCore

/// Maps technical failures to stable, user-facing catalog keys. Ported from
/// `ui/feedback/UserFacingError.kt`. Never exposes raw `reason=` blobs.
extension Error {
	func toUiText() -> UiText {
		if let vni = self as? VnidropError {
			switch vni {
			case .Ticket:
				return .resource("error_invalid_ticket")
			case .Permission:
				return .resource("error_permission")
			case .Filesystem:
				return .resource("error_filesystem")
			case .Transfer(let reason):
				return transferUiText(reason)
			case .Repository:
				return .resource("error_repository")
			case .Initialization(let reason):
				return initializationUiText(reason)
			case .Internal(let reason):
				return reasonHints(reason) ?? .resource("error_generic")
			}
		}
		return reasonHints(technicalDetail) ?? .resource("error_generic")
	}

	/// True when the user intentionally backed out of a flow.
	var isUserCancellation: Bool {
		let haystack = technicalDetail.lowercased()
		if haystack.isEmpty {
			// URLError / CocoaError cancellation without a message.
			if let urlError = self as? URLError, urlError.code == .cancelled { return true }
			return (self as NSError).code == NSUserCancelledError
		}
		return haystack.contains("cancelled")
			|| haystack.contains("canceled")
			|| haystack.contains("user cancelled")
			|| haystack.contains("user canceled")
	}

	/// Prefers a `VnidropError` reason; else the localized description.
	var technicalDetail: String {
		if let vni = self as? VnidropError {
			switch vni {
			case .Initialization(let r), .Ticket(let r), .Filesystem(let r),
				 .Transfer(let r), .Permission(let r), .Repository(let r), .Internal(let r):
				return r
			}
		}
		return (self as? LocalizedError)?.errorDescription ?? (self as NSError).localizedDescription
	}
}

private func transferUiText(_ reason: String) -> UiText {
	let detail = reason.lowercased()
	if detail.contains("refused") || detail.contains("denied") || detail.contains("not approved") {
		return .resource("error_permission")
	}
	return .resource("error_transfer")
}

private func initializationUiText(_ reason: String) -> UiText {
	let detail = reason.lowercased()
	if detail.contains("native") && detail.contains("library") {
		return .resource("error_missing_native_library")
	}
	if detail.contains("socket") || detail.contains("bind") {
		return .resource("error_socket_bind")
	}
	return .resource("error_initialization")
}

private func reasonHints(_ detailRaw: String) -> UiText? {
	let detail = detailRaw.lowercased()
	if detail.isEmpty { return nil }

	if detail.contains("still starting") || detail.contains("starting up") {
		return .resource("error_starting_up")
	}
	if detail.contains("empty") && (detail.contains("invitation") || detail.contains("ticket") || detail.contains("qr")) {
		return .resource("error_invitation_empty")
	}
	if detail.contains("select at least one") || detail.contains("no files found") {
		return .resource("error_share_empty")
	}
	if detail.contains("camera") {
		return .resource("error_camera")
	}
	if detail.contains("nfc") || detail.contains("ndef")
		|| (detail.contains("read-only") && detail.contains("tag"))
		|| detail.contains("tag is too small") || detail.contains("no nfc tag") {
		return .resource("error_nfc")
	}
	if detail.contains("native") && detail.contains("library") {
		return .resource("error_missing_native_library")
	}
	if detail.contains("socket") || detail.contains("bind") {
		return .resource("error_socket_bind")
	}
	if detail.contains("device information") || detail.contains("device info") {
		return .resource("error_device_info")
	}
	if detail.contains("refused") || detail.contains("denied") || detail.contains("permission")
		|| detail.contains("not approved") || detail.contains("waiting for approval") {
		return .resource("error_permission")
	}
	if detail.contains("invalid ticket") || detail.contains("ticket error")
		|| detail.contains("could not be read") || detail.contains("malformed")
		|| detail.contains("invitation could not be opened") {
		return .resource("error_invalid_ticket")
	}
	if detail.contains("selected")
		&& (detail.contains("file") || detail.contains("folder") || detail.contains("document") || detail.contains("open")) {
		return .resource("error_selection_failed")
	}
	if detail.contains("could not open the selected") || detail.contains("could not open selected") {
		return .resource("error_selection_failed")
	}
	if detail.contains("document picker") || detail.contains("folder picker") || detail.contains("file descriptor")
		|| detail.contains("view controller") {
		return .resource("error_selection_failed")
	}
	return nil
}
