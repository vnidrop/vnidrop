import SwiftUI

/// Receive screen, rebuilt on native SwiftUI. A grouped `List` of received
/// transfers with swipe-to-delete, and the acquisition flow as a native sheet.
struct ReceiveScreen: View {
	@ObservedObject var model: ReceiveModel
	let windowClass: WindowClass

	private var transfers: [Transfer] {
		model.coreState.transfers.filter { $0.direction == .receive }
	}
	private var deletable: [Transfer] {
		transfers.filter { $0.status.isTerminalReceiveHistory }
	}

	var body: some View {
		NavigationStack {
			Group {
				if transfers.isEmpty {
					emptyState
				} else {
					history
				}
			}
			.navigationTitle(Text(LocalizedStringKey("receive_title")))
			.toolbar {
				ToolbarItem(placement: .primaryAction) {
					Button(action: model.openAcquisition) {
						Label(String(localized: "button_receive_files"), systemImage: "plus")
					}
				}
				if !deletable.isEmpty {
					ToolbarItem(placement: .primaryAction) {
						Button(role: .destructive, action: model.requestClearHistory) {
							Label(String(localized: "receive_clear_history"), systemImage: "trash")
						}
					}
				}
			}
		}
		.adaptiveDrawer(
			isPresented: Binding(get: { model.state.isAcquisitionOpen }, set: { _ in }),
			windowClass: windowClass,
			onDismiss: model.dismissAcquisition
		) {
			if model.state.ticket.isEmpty {
				ReceiveMethodPanel(model: model)
			} else {
				InvitationReviewPanel(model: model)
			}
		}
		.alert(
			Text(LocalizedStringKey(clearAllPending ? "receive_clear_history_title" : "receive_delete_history_title")),
			isPresented: Binding(get: { model.state.historyDeleteTarget != nil }, set: { if !$0 { Task { @MainActor in model.dismissHistoryDelete() } } })
		) {
			Button(String(localized: "button_cancel"), role: .cancel, action: model.dismissHistoryDelete)
			Button(String(localized: clearAllPending ? "receive_clear_history" : "button_delete_transfer"),
				   role: .destructive, action: model.confirmHistoryDelete)
		} message: {
			historyDeleteMessage
		}
	}

	private var history: some View {
		List {
			Section {
				ForEach(transfers) { transfer in
					ReceiveTransferRow(
						transfer: transfer,
						progress: progressForTransfer(events: model.coreState.events, transferId: transfer.transferId)
					)
					.swipeActions(edge: .trailing, allowsFullSwipe: true) {
						if transfer.status.isTerminalReceiveHistory {
							Button(role: .destructive) {
								model.requestDeleteHistoryItem(transfer.transferId)
							} label: {
								Label(String(localized: "button_delete_transfer"), systemImage: "trash")
							}
						}
					}
				}
			} header: {
				Text(LocalizedStringKey("receive_history_title"))
			} footer: {
				Text(LocalizedStringKey("receive_new_subtitle"))
			}
		}
	}

	private var emptyState: some View {
		ContentUnavailableView {
			Label(String(localized: "receive_empty_title"), systemImage: "tray.and.arrow.down")
		} description: {
			Text(LocalizedStringKey("receive_empty_body"))
		} actions: {
			Button(action: model.openAcquisition) {
				Label(String(localized: "button_receive_files"), systemImage: "plus")
			}
			.buttonStyle(.borderedProminent)
			.controlSize(.large)
		}
	}

	private var clearAllPending: Bool { model.state.historyDeleteTarget == .all }

	@ViewBuilder
	private var historyDeleteMessage: some View {
		if let target = model.state.historyDeleteTarget {
			if target == .all {
				Text(LocalizedStringKey("receive_clear_history_description"))
			} else {
				Text(String(format: String(localized: "receive_delete_history_description"),
							 transferName(for: target) ?? String(localized: "receive_unknown_transfer")))
			}
		}
	}

	private func transferName(for target: ReceiveHistoryDeleteTarget) -> String? {
		if case .transfer(let id) = target {
			return transfers.first { $0.transferId == id }?.transferName
		}
		return nil
	}
}

private struct ReceiveTransferRow: View {
	let transfer: Transfer
	let progress: TransferProgress?

	var body: some View {
		HStack(spacing: 12) {
			Image(systemName: "doc")
				.foregroundStyle(.secondary)
				.frame(width: 40, height: 40)
				.background(.quaternary, in: RoundedRectangle(cornerRadius: 9))
			VStack(alignment: .leading, spacing: 3) {
				Text(transfer.transferName ?? String(localized: "receive_unknown_transfer"))
					.font(.body).lineLimit(1)
				Text("\(formatBytes(transfer.totalSize)) · \(statusLabel(transfer.status))")
					.font(.caption).foregroundStyle(.secondary)
				if transfer.status == .receiving, let progress {
					ProgressRow(labelKey: progress.labelKey, progress: progress.progress, detail: progress.detail)
						.padding(.top, 2)
				}
			}
			Spacer(minLength: 0)
		}
	}
}
