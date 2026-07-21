import SwiftUI

/// Native app entry point for iOS, iPadOS, and macOS.
/// Opens `.vnd` invitations via `onOpenURL` and routes them to the receive flow.
@main
struct VniDropApp: App {
	@StateObject private var externalInvitations = ExternalInvitationController()

	var body: some Scene {
		WindowGroup {
			RootView(dependencies: makeAppDependencies(externalInvitations: externalInvitations))
				.ignoresSafeArea()
				.onOpenURL(perform: openInvitation)
		}
	}

	/// Reads a `.vnd` invitation document under a security scope, enforcing the
	/// 64 KiB / strict-UTF-8 rules from `ContentView.swift`.
	private func openInvitation(_ url: URL) {
		guard url.pathExtension.caseInsensitiveCompare(vniDropInvitationExtension) == .orderedSame else {
			externalInvitations.reportOpenFailure(message: "This is not a VniDrop invitation")
			return
		}
		let started = url.startAccessingSecurityScopedResource()
		defer { if started { url.stopAccessingSecurityScopedResource() } }
		do {
			let values = try url.resourceValues(forKeys: [.fileSizeKey])
			if let size = values.fileSize, size > maxVniDropInvitationBytes {
				throw InvitationError.tooLarge
			}
			let data = try Data(contentsOf: url, options: .mappedIfSafe)
			let raw = try decodeInvitationBytes(data)
			externalInvitations.openInvitation(raw: raw)
		} catch {
			externalInvitations.reportOpenFailure(
				message: (error as? LocalizedError)?.errorDescription ?? "The invitation could not be opened"
			)
		}
	}
}
