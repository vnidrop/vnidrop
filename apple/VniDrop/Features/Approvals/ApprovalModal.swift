import SwiftUI

/// Non-dismissable receiver-approval modal, presented as a native sheet that can't
/// be swiped away. The endpoint id is the trusted identity; display names are
/// peer-provided.
struct ApprovalModalHost: View {
	let state: ApprovalState
	let onAccept: (String) -> Void
	let onRefuse: (String) -> Void

	var body: some View {
		Color.clear
			.sheet(isPresented: .constant(state.current != nil)) {
				if let request = state.current {
					ApprovalSheet(state: state, request: request, onAccept: onAccept, onRefuse: onRefuse)
						.interactiveDismissDisabled(true)
						.modifier(ApprovalDetents())
				}
			}
	}
}

private struct ApprovalDetents: ViewModifier {
	func body(content: Content) -> some View {
		#if os(iOS)
		content.presentationDetents([.medium])
		#else
		content.frame(minWidth: 420, minHeight: 320)
		#endif
	}
}

private struct ApprovalSheet: View {
	let state: ApprovalState
	let request: PendingApproval
	let onAccept: (String) -> Void
	let onRefuse: (String) -> Void

	var body: some View {
		let busy = state.respondingIds.contains(request.id)
		let receiver = request.receiverName ?? request.receiverDeviceName ?? String(localized: "approval_nearby_device")
		VStack(spacing: 16) {
			Image(systemName: "checkmark.shield.fill")
				.font(.system(size: 44))
				.foregroundStyle(.tint)
				.padding(.top, 12)
			Text(LocalizedStringKey("approval_connection_request"))
				.font(.title2).fontWeight(.semibold)
			Text(String(format: String(localized: "approval_request_body"), receiver, request.transferName))
				.multilineTextAlignment(.center)
			Text(String(format: String(localized: "approval_endpoint_id"), request.remoteEndpointId))
				.font(.caption).foregroundStyle(.secondary)
				.multilineTextAlignment(.center)
			if state.pending.count > 1 {
				Text(String(format: String(localized: "approval_pending_count"), state.pending.count))
					.font(.caption).foregroundStyle(.secondary)
			}
			Spacer(minLength: 0)
			if busy { ProgressView() }
			VStack(spacing: 10) {
				Button(action: { onAccept(request.id) }) {
					Text(LocalizedStringKey("button_approve")).frame(maxWidth: .infinity)
				}
				.buttonStyle(.borderedProminent).controlSize(.large).disabled(busy)
				Button(role: .destructive, action: { onRefuse(request.id) }) {
					Text(LocalizedStringKey("button_refuse")).frame(maxWidth: .infinity)
				}
				.buttonStyle(.bordered).controlSize(.large).disabled(busy)
			}
		}
		.padding(24)
	}
}
