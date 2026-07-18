import Foundation

let vniDropInvitationMimeType = "application/vnd.vnidrop.transfer"
let vniDropInvitationExtension = "vnd"
let maxVniDropInvitationBytes = 64 * 1024

/// Buffered ingress for invitation documents opened by the OS, ported from
/// `ExternalInvitationController.kt`. Hosts can submit before the UI is attached
/// during a cold launch; each document is consumed exactly once.
@MainActor
final class ExternalInvitationController: ObservableObject {
	/// Emits validated (or failed) invitations. The app-level receive workflow
	/// consumes each exactly once.
	private var continuation: AsyncStream<Result<String, Error>>.Continuation?
	lazy var invitations: AsyncStream<Result<String, Error>> = {
		AsyncStream { continuation in
			self.continuation = continuation
		}
	}()

	func openInvitation(raw: String) {
		continuation?.yield(validateInvitation(raw))
	}

	func reportOpenFailure(message: String) {
		continuation?.yield(.failure(InvitationError.message(message)))
	}
}

enum InvitationError: LocalizedError {
	case empty
	case tooLarge
	case invalidEncoding
	case message(String)

	var errorDescription: String? {
		switch self {
		case .empty: return "The invitation is empty"
		case .tooLarge: return "The invitation is too large"
		case .invalidEncoding: return "The invitation is not valid text"
		case .message(let m): return m
		}
	}
}

func validateInvitation(_ raw: String) -> Result<String, Error> {
	if raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
		return .failure(InvitationError.empty)
	}
	if raw.utf8.count > maxVniDropInvitationBytes {
		return .failure(InvitationError.tooLarge)
	}
	return .success(raw)
}

/// Decode invitation document bytes as strict UTF-8, ported from
/// `decodeInvitationBytes`. Rejects payloads that are not lossless UTF-8.
func decodeInvitationBytes(_ bytes: Data) throws -> String {
	guard !bytes.isEmpty else { throw InvitationError.empty }
	guard bytes.count <= maxVniDropInvitationBytes else { throw InvitationError.tooLarge }
	guard let text = String(data: bytes, encoding: .utf8), Data(text.utf8) == bytes else {
		throw InvitationError.invalidEncoding
	}
	guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw InvitationError.empty }
	return text
}
