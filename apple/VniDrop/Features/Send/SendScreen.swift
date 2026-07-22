import SwiftUI

/// Send screen, rebuilt on native SwiftUI. A grouped `List` of outgoing transfers,
/// with the composer and detail panels as native sheets and delete as an alert.
struct SendScreen: View {
	@ObservedObject var model: SendModel
	let windowClass: WindowClass

	private var outgoing: [Transfer] {
		model.coreState.transfers.filter { $0.direction == .send }
	}
	private var selectedTransfer: Transfer? {
		guard let id = model.state.selectedTransferId else { return nil }
		return outgoing.first { $0.transferId == id }
	}

	private var detailsBinding: Binding<Bool> {
		Binding(get: { model.state.selectedTransferId != nil }, set: { if !$0 { model.closeTransferDetails() } })
	}

	var body: some View {
		NavigationStack {
			Group {
				if outgoing.isEmpty {
					emptyState
				} else {
					catalog
				}
			}
			.navigationTitle(Text(LocalizedStringKey("send_title")))
			.toolbar {
				ToolbarItem(placement: .primaryAction) {
					Button(action: model.openComposer) {
						Label(String(localized: "button_create_new_transfer"), systemImage: "plus")
					}
				}
			}
			.navigationDestination(isPresented: detailsBinding) {
				if let transfer = selectedTransfer {
					detailView(for: transfer)
				}
			}
		}
		.adaptiveDrawer(
			isPresented: Binding(get: { model.state.isComposerOpen }, set: { _ in }),
			windowClass: windowClass,
			onDismiss: model.dismissComposer
		) {
			TransferComposer(model: model, windowClass: windowClass)
		}
	}

	/// The pushed transfer details view, with its detail-panel sheet and delete
	/// alert attached here so they present from the detail's own context (presenting
	/// modals from the parent stack while a detail is pushed is unreliable on macOS).
	private func detailView(for transfer: Transfer) -> some View {
		TransferDetailsView(model: model, transfer: transfer, events: model.coreState.events)
			.adaptiveDrawer(
				isPresented: Binding(get: { model.state.detailPanel != nil }, set: { _ in }),
				windowClass: windowClass,
				onDismiss: model.closeDetailPanel
			) {
				if let panel = model.state.detailPanel {
					DetailPanelContent(model: model, transfer: transfer, panel: panel)
				}
			}
			.alert(
				Text(LocalizedStringKey("transfer_delete_title")),
				isPresented: Binding(get: { model.state.isDeleteConfirmationOpen }, set: { if !$0 { Task { @MainActor in model.dismissDeleteTransfer() } } })
			) {
				Button(String(localized: "button_cancel"), role: .cancel, action: model.dismissDeleteTransfer)
				Button(String(localized: "button_delete_transfer"), role: .destructive, action: model.confirmDeleteTransfer)
			} message: {
				Text(String(format: String(localized: "transfer_delete_description"),
							 transfer.transferName ?? String(localized: "send_new_transfer_title")))
			}
	}

	private var catalog: some View {
		List {
			Section {
				ForEach(outgoing) { transfer in
					Button {
						model.openTransfer(transfer.transferId)
					} label: {
						TransferListItem(
							transfer: transfer,
							thumbnail: model.state.transferThumbnails[transfer.transferId],
							progress: progress(for: transfer)
						)
					}
					.buttonStyle(.plain)
				}
			} header: {
				Text(LocalizedStringKey("send_transfers_title"))
			} footer: {
				Text(LocalizedStringKey("send_subtitle"))
			}
		}
	}

	private var emptyState: some View {
		ContentUnavailableView {
			Label(String(localized: "send_empty_title"), systemImage: "paperplane")
		} description: {
			Text(LocalizedStringKey("send_empty_body"))
		} actions: {
			Button(action: model.openComposer) {
				Label(String(localized: "button_create_new_transfer"), systemImage: "plus")
			}
			.buttonStyle(.borderedProminent)
			.controlSize(.large)
		}
	}

	private func progress(for transfer: Transfer) -> TransferProgress? {
		switch transfer.status {
		case .importing: return progressForTransfer(events: model.coreState.events, transferId: transfer.transferId)
		case .sharing: return sharingProgress(for: transfer)
		default: return nil
		}
	}

	/// Progress for an active share, driven by receivers whose delivery is still
	/// in flight (`.accepted`). Returns nil when none are downloading, so the bar
	/// clears once every receiver has completed even if byte events lag.
	private func sharingProgress(for transfer: Transfer) -> TransferProgress? {
		let active = (model.receiversByTransfer[transfer.transferId] ?? []).filter { $0.status == .accepted }
		if active.isEmpty { return nil }
		let fractions: [Double] = active.compactMap { receiver -> Double? in
			let progress = progressForReceiver(events: model.coreState.events, transferId: transfer.transferId,
									remoteEndpointId: receiver.remoteEndpointId, totalSizeHint: transfer.totalSize)
			guard progress?.kind == "started" || progress?.kind == "progress" else { return nil }
			return progress?.progress
		}
		guard !fractions.isEmpty else { return nil }
		let combined = fractions.reduce(0, +) / Double(fractions.count)
		if active.count == 1 {
			return TransferProgress(transferId: transfer.transferId, phase: "transfer", kind: "progress",
									labelKey: "progress_sending", progress: combined)
		}
		return TransferProgress(transferId: transfer.transferId, phase: "transfer", kind: "progress",
								labelKey: "progress_sending", progress: combined,
								label: String(format: String(localized: "progress_sending_to_count"), active.count))
	}
}

private struct TransferListItem: View {
	let transfer: Transfer
	let thumbnail: Data?
	let progress: TransferProgress?

	var body: some View {
		HStack(spacing: 12) {
			FileArtwork(thumbnail: thumbnail)
				.frame(width: 40, height: 40)
				.background(.quaternary, in: RoundedRectangle(cornerRadius: 9))
			VStack(alignment: .leading, spacing: 3) {
				HStack {
					Text(transfer.transferName ?? String(localized: "send_new_transfer_title"))
						.font(.body).lineLimit(1)
					Spacer()
					StatusPill(label: statusLabel(transfer.status), tone: transfer.status.pillTone)
				}
				Text("\(formatBytes(transfer.totalSize)) · \(accessPolicyLabel(transfer.accessPolicy))")
					.font(.caption).foregroundStyle(.secondary).lineLimit(1)
				if let progress, transfer.status == .importing || transfer.status == .sharing {
					ProgressRow(labelKey: progress.labelKey, progress: progress.progress, detail: progress.detail, labelText: progress.label)
						.padding(.top, 2)
				}
			}
			Image(systemName: "chevron.forward")
				.font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
		}
		.contentShape(Rectangle())
	}
}

struct FileArtwork: View {
	let thumbnail: Data?

	var body: some View {
		if let thumbnail, let image = PlatformImage.from(data: thumbnail) {
			image.resizable().aspectRatio(contentMode: .fill)
				.clipShape(RoundedRectangle(cornerRadius: 8))
		} else {
			Image(systemName: "doc")
				.font(.system(size: 18))
				.foregroundStyle(.secondary)
		}
	}
}

func statusLabel(_ status: TransferStatus) -> String {
	String(localized: String.LocalizationValue(statusLabelKey(status)))
}

func accessPolicyLabel(_ policy: ShareAccessPolicy) -> String {
	switch policy {
	case .requireApproval: return String(localized: "send_access_approval")
	case .anyoneWithTransfer: return String(localized: "send_access_anyone")
	}
}

extension TransferStatus {
	var pillTone: PillTone {
		switch self {
		case .sharing, .done: return .brand
		case .importing, .receiving: return .warning
		case .failed, .cancelled: return .destructive
		case .stopped: return .neutral
		}
	}
}
