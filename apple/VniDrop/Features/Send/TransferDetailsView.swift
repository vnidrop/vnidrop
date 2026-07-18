import SwiftUI
import CoreImage.CIFilterBuiltins

/// Transfer details + drawer panels, ported from `feature/send/TransferDetails.kt`.

struct TransferDetailsView: View {
	@ObservedObject var model: SendModel
	let transfer: Transfer
	let events: [CoreEventModel]

	private var pendingReceivers: Int {
		model.state.receiverHistory.filter { $0.status == .requested || $0.status == .accepted }.count
	}
	private var completedReceivers: Int {
		model.state.receiverHistory.filter { $0.status == .completed }.count
	}

	var body: some View {
		Form {
			Section {
				LabeledContent(String(localized: "metadata_status"), value: statusLabel(transfer.status))
				LabeledContent(String(localized: "metadata_size"), value: formatBytes(transfer.totalSize))
				LabeledContent(String(localized: "send_access_title"), value: accessPolicyLabel(transfer.accessPolicy))
			} header: {
				Text(transfer.transferName ?? String(localized: "send_new_transfer_title"))
			}

			Section {
				DetailDestination(
					title: String(localized: "transfer_activity_title"),
					description: String(localized: "transfer_activity_description"),
					count: events.filter { $0.transferId == transfer.transferId && $0.isMeaningfulActivity }.count,
					onTap: model.openActivity
				)
				DetailDestination(
					title: String(localized: "transfer_receivers_title"),
					description: receiversDescription(pendingReceivers, completedReceivers),
					count: pendingReceivers + completedReceivers,
					onTap: model.openReceivers
				)
				DetailDestination(
					title: String(localized: "transfer_share_title"),
					description: String(localized: "transfer_share_description"),
					count: 0,
					onTap: model.openShare
				)
			}
		}
		.formStyle(.grouped)
		.navigationTitle(Text(LocalizedStringKey("send_transfer_details_title")))
		#if os(iOS)
		.navigationBarTitleDisplayMode(.inline)
		#endif
		.toolbar {
			ToolbarItem(placement: .primaryAction) {
				Button(role: .destructive, action: model.requestDeleteTransfer) {
					Image(systemName: "trash")
				}
			}
		}
	}
}

private func receiversDescription(_ pending: Int, _ completed: Int) -> String {
	if pending > 0 && completed > 0 {
		return "\(String(format: String(localized: "transfer_receivers_pending"), pending)) · \(String(format: String(localized: "transfer_receivers_completed_count"), completed))"
	}
	if pending > 0 { return String(format: String(localized: "transfer_receivers_pending"), pending) }
	if completed > 0 { return String(format: String(localized: "transfer_receivers_completed_count"), completed) }
	return String(localized: "transfer_receivers_description")
}

private struct DetailDestination: View {
	let title: String
	let description: String
	let count: Int
	let onTap: () -> Void
	var body: some View {
		Button(action: onTap) {
			HStack {
				VStack(alignment: .leading, spacing: 3) {
					Text(title).foregroundStyle(.primary)
					Text(description).font(.caption).foregroundStyle(.secondary)
				}
				Spacer()
				if count > 0 {
					Text("\(count)")
						.font(.footnote)
						.foregroundStyle(.secondary)
				}
				Image(systemName: "chevron.forward")
					.font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
			}
			.contentShape(Rectangle())
		}
		.buttonStyle(.plain)
	}
}

// MARK: - Detail panels

struct DetailPanelContent: View {
	@ObservedObject var model: SendModel
	let transfer: Transfer
	let panel: TransferDetailPanel

	var body: some View {
		switch panel {
		case .activity:
			TransferActivityPanel(events: model.coreState.events, transferId: transfer.transferId)
		case .receivers:
			ReceiverHistoryPanel(
				receivers: model.state.receiverHistory,
				loading: model.state.isLoadingReceivers,
				events: model.coreState.events,
				transferTotalSize: transfer.totalSize
			)
		case .share:
			TransferSharePanel(model: model, transfer: transfer)
		}
	}
}

private struct PanelContainer<Content: View>: View {
	let title: String
	@ViewBuilder let content: () -> Content
	var body: some View {
		VStack(alignment: .leading, spacing: 14) {
			Text(title).font(VniType.titleLarge)
			content()
		}
		.padding(.horizontal, 20).padding(.vertical, 14)
		.frame(maxWidth: .infinity, alignment: .leading)
	}
}

struct TransferActivityPanel: View {
	@Environment(\.vniColors) private var colors
	let events: [CoreEventModel]
	let transferId: UInt64

	var body: some View {
		let visible = events
			.filter { $0.transferId == transferId && $0.isMeaningfulActivity }
			.sorted { $0.timestamp > $1.timestamp }
		PanelContainer(title: String(localized: "transfer_activity_title")) {
			if visible.isEmpty {
				Text(LocalizedStringKey("transfer_no_activity")).foregroundStyle(colors.foregroundLighter)
			} else {
				ForEach(Array(visible.enumerated()), id: \.offset) { index, event in
					if index > 0 { Divider().overlay(colors.borderDefault) }
					Text(LocalizedStringKey(event.activityTitleKey))
						.fontWeight(.medium).padding(.vertical, 14)
				}
			}
		}
	}
}

struct ReceiverHistoryPanel: View {
	@Environment(\.vniColors) private var colors
	let receivers: [ReceiverRequestModel]
	let loading: Bool
	let events: [CoreEventModel]
	let transferTotalSize: UInt64

	var body: some View {
		PanelContainer(title: String(localized: "transfer_receivers_title")) {
			if loading {
				ProgressView().frame(maxWidth: .infinity).padding(40)
			} else if receivers.isEmpty {
				Text(LocalizedStringKey("transfer_no_receivers")).foregroundStyle(colors.foregroundLighter)
			} else {
				ForEach(Array(receivers.enumerated()), id: \.element.id) { index, receiver in
					if index > 0 { Divider().overlay(colors.borderDefault) }
					ReceiverRow(receiver: receiver, sendProgress: sendProgress(for: receiver))
				}
			}
		}
	}

	private func sendProgress(for receiver: ReceiverRequestModel) -> TransferProgress? {
		switch receiver.status {
		case .accepted, .requested:
			return progressForReceiver(events: events, transferId: receiver.transferId,
									   remoteEndpointId: receiver.remoteEndpointId, totalSizeHint: transferTotalSize)
		default: return nil
		}
	}
}

private struct ReceiverRow: View {
	@Environment(\.vniColors) private var colors
	let receiver: ReceiverRequestModel
	let sendProgress: TransferProgress?

	var body: some View {
		let name = receiver.receiverName ?? receiver.receiverDeviceName ?? String(localized: "transfer_nearby_device")
		let showLive = sendProgress != nil && receiver.status != .completed
			&& receiver.status != .refused && receiver.status != .expired
		VStack(alignment: .leading, spacing: 6) {
			Text(name).font(VniType.bodyLarge).lineLimit(1)
			if let deviceName = receiver.receiverDeviceName, deviceName != name {
				Text(deviceName).font(VniType.bodySmall).foregroundStyle(colors.foregroundLighter)
			}
			if showLive, let sendProgress {
				ProgressRow(labelKey: sendProgress.labelKey, progress: sendProgress.progress, detail: sendProgress.detail)
			} else {
				Text(LocalizedStringKey(receiver.status.statusTextKey))
					.font(VniType.bodySmall).fontWeight(.medium)
					.foregroundStyle(receiver.status.statusColor(colors))
			}
			if let reason = receiver.reason, !reason.isEmpty {
				Text(reason).font(VniType.bodySmall).foregroundStyle(colors.foregroundLighter)
			}
		}
		.frame(maxWidth: .infinity, alignment: .leading)
		.padding(.vertical, 13)
	}
}

struct TransferSharePanel: View {
	@Environment(\.vniColors) private var colors
	@ObservedObject var model: SendModel
	let transfer: Transfer

	var body: some View {
		PanelContainer(title: String(localized: "transfer_share_title")) {
			if let ticket = transfer.ticket {
				qrCard(ticket: ticket)
				Text(LocalizedStringKey("transfer_scan_qr"))
					.font(VniType.bodySmall).foregroundStyle(colors.foregroundLighter)
					.frame(maxWidth: .infinity)
				ShareActionsView(model: model, transfer: transfer, ticket: ticket)
			} else {
				Text(LocalizedStringKey("transfer_event_preparing")).foregroundStyle(colors.foregroundLighter)
			}
		}
	}

	private func qrCard(ticket: String) -> some View {
		ZStack {
			if let qr = QRCode.generate(from: ticket) {
				qr.interpolation(.none).resizable().scaledToFit().padding(14)
			} else {
				ProgressView()
			}
		}
		.frame(width: 268, height: 268)
		.background(Color.white, in: RoundedRectangle(cornerRadius: 18))
		.frame(maxWidth: .infinity)
	}
}

// MARK: - QR generation (CoreImage)

enum QRCode {
	static func generate(from string: String) -> Image? {
		let context = CIContext()
		let filter = CIFilter.qrCodeGenerator()
		filter.message = Data(string.utf8)
		filter.correctionLevel = "M"
		guard let output = filter.outputImage else { return nil }
		let scaled = output.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
		guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
		#if os(iOS)
		return Image(uiImage: UIImage(cgImage: cgImage))
		#else
		return Image(nsImage: NSImage(cgImage: cgImage, size: .zero))
		#endif
	}
}

// MARK: - Event helpers (ported from TransferDetails.kt)

extension CoreEventModel {
	var isMeaningfulActivity: Bool {
		(phase == "import" && kind == "started")
			|| (phase == "ticket" && kind == "created")
			|| (phase == "network" && (kind == "connecting" || kind == "connected"))
			|| (phase == "download" && kind == "found-collection")
			|| (phase == "lifecycle" && ["done", "cancelled", "share-stopped"].contains(kind))
			|| ["receiver-requested", "receiver-accepted", "receiver-auto-approved",
				"receiver-refused", "receiver-completed", "share-stopped", "failed"].contains(kind)
	}

	var activityTitleKey: String {
		if phase == "import" && kind == "started" { return "transfer_event_preparing" }
		if phase == "ticket" && kind == "created" { return "transfer_event_ready" }
		if phase == "network" { return "transfer_event_connecting" }
		if phase == "download" { return "transfer_event_downloading" }
		if phase == "export" { return "transfer_event_saving" }
		if kind == "receiver-requested" { return "transfer_event_requested" }
		if kind == "receiver-accepted" || kind == "receiver-auto-approved" { return "transfer_event_approved" }
		if kind == "receiver-refused" { return "transfer_event_refused" }
		if kind == "receiver-completed" { return "transfer_event_completed" }
		if kind == "share-stopped" || (phase == "lifecycle" && kind == "cancelled") { return "transfer_event_stopped" }
		if kind == "failed" { return "transfer_event_failed" }
		return "transfer_event_updated"
	}
}

extension ReceiverDeliveryStatus {
	var statusTextKey: String {
		switch self {
		case .requested: return "transfer_receiver_requested"
		case .accepted: return "transfer_receiver_accepted"
		case .refused: return "transfer_receiver_refused"
		case .expired: return "transfer_receiver_expired"
		case .completed: return "transfer_receiver_completed"
		case .unknown: return "transfer_receiver_unknown"
		}
	}

	func statusColor(_ colors: VniDropColors) -> Color {
		switch self {
		case .completed: return colors.brandDefault
		case .refused, .expired: return colors.destructiveDefault
		default: return colors.foregroundLighter
		}
	}
}

#if os(iOS)
import UIKit
#else
import AppKit
#endif
