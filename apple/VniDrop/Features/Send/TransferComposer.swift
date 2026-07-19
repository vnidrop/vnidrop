import SwiftUI

/// Transfer composer drawer, ported from `feature/send/TransferComposer.kt`.
/// Two steps: choose files/folder, then review + name + access policy + share.
struct TransferComposer: View {
	@ObservedObject var model: SendModel
	let windowClass: WindowClass

	private var state: SendState { model.state }

	var body: some View {
		VStack(alignment: .leading, spacing: 16) {
			if state.selectedFiles.isEmpty {
				chooseStep
			} else {
				reviewStep
			}
		}
		.padding(.horizontal, 20).padding(.vertical, 12)
		.frame(maxWidth: .infinity, alignment: .leading)
		.sendPickers(model: model)
	}

	private var chooseStep: some View {
		VStack(alignment: .leading, spacing: 16) {
			Text(LocalizedStringKey("send_choose_file_title")).font(.title2).fontWeight(.semibold)
			Text(LocalizedStringKey("send_choose_file_body"))
				.font(.subheadline).foregroundStyle(.secondary)
			VStack(spacing: 14) {
				Image(systemName: "doc").font(.system(size: 30)).foregroundStyle(.tint)
				PrimaryButton(title: String(localized: "button_choose_files"), action: model.selectFile).fixedSize()
				QuietButton(title: String(localized: "button_choose_folder"), action: model.selectFolder)
			}
			.frame(maxWidth: .infinity)
			.padding(28)
			.background(.quaternary.opacity(0.5), in: RoundedRectangle(cornerRadius: 16))
		}
	}

	private var reviewStep: some View {
		VStack(alignment: .leading, spacing: 16) {
			Text(LocalizedStringKey("send_review_title")).font(.title2).fontWeight(.semibold)
			if state.selectedFiles.count > 1 {
				Text(String(format: String(localized: "send_selected_files_count"), state.selectedFiles.count))
					.font(.subheadline).foregroundStyle(.secondary)
			}
			ForEach(state.selectedFiles) { file in
				SelectedFileCard(
					file: file,
					canRemove: state.selectedFiles.count > 1 && !state.isSharing,
					onRemove: { model.removeSelectedFile(file.value) }
				)
			}
			Field(label: String(localized: "field_transfer_name"),
				  value: Binding(get: { state.transferName }, set: { model.setTransferName($0) }))
			Field(label: String(localized: "field_sender_name"),
				  value: Binding(get: { state.senderName }, set: { model.setSenderName($0) }))
			Text(LocalizedStringKey("send_access_title")).font(.headline)
			PolicyOption(
				icon: "checkmark.shield", titleKey: "send_access_approval", descKey: "send_access_approval_description",
				selected: state.accessPolicy == .requireApproval,
				onTap: { model.setAccessPolicy(.requireApproval) }
			)
			PolicyOption(
				icon: "globe", titleKey: "send_access_anyone", descKey: "send_access_anyone_description",
				selected: state.accessPolicy == .anyoneWithTransfer,
				onTap: { model.setAccessPolicy(.anyoneWithTransfer) }
			)
			if state.accessPolicy == .anyoneWithTransfer {
				Label(String(localized: "send_access_anyone_warning"), systemImage: "exclamationmark.triangle.fill")
					.font(.caption).foregroundStyle(.orange)
			}
			actions
		}
	}

	@ViewBuilder
	private var actions: some View {
		let shareTitle = state.isSharing
			? String(localized: "button_sharing_file") : String(localized: "button_share_file")
		let shareButton = PrimaryButton(
			title: shareTitle, action: model.createShare,
			enabled: state.canCreateShare(coreInitialized: model.coreState.isInitialized)
		)
		if windowClass == .phone {
			VStack(spacing: 8) {
				shareButton
				QuietButton(title: String(localized: "button_change_files"), action: model.selectFile, enabled: !state.isSharing)
				QuietButton(title: String(localized: "button_choose_folder"), action: model.selectFolder, enabled: !state.isSharing)
			}
		} else {
			HStack(spacing: 8) {
				shareButton.fixedSize()
				QuietButton(title: String(localized: "button_change_files"), action: model.selectFile, enabled: !state.isSharing)
				QuietButton(title: String(localized: "button_choose_folder"), action: model.selectFolder, enabled: !state.isSharing)
				QuietButton(title: String(localized: "button_clear"), action: model.clearSelectedSource, enabled: !state.isSharing)
			}
		}
	}
}

private struct SelectedFileCard: View {
	let file: PickedShareFile
	let canRemove: Bool
	let onRemove: () -> Void

	var body: some View {
		HStack(spacing: 12) {
			FileArtwork(thumbnail: file.thumbnailData)
				.frame(width: 44, height: 44)
				.background(.quaternary, in: RoundedRectangle(cornerRadius: 10))
			VStack(alignment: .leading, spacing: 3) {
				Text(file.displayName).lineLimit(1)
				Text(subtitle).font(.caption).foregroundStyle(.secondary)
			}
			Spacer()
			if canRemove {
				Button(role: .destructive, action: onRemove) {
					Image(systemName: "trash")
				}
				.buttonStyle(.borderless)
				.tint(.red)
			}
		}
		.padding(14)
		.frame(maxWidth: .infinity)
		.background(.quaternary.opacity(0.5), in: RoundedRectangle(cornerRadius: 14))
	}

	private var subtitle: String {
		if file.isDirectory { return String(localized: "send_folder_label") }
		if let size = file.sizeBytes { return formatBytes(size) }
		return String(localized: "send_file_size_unknown")
	}
}

private struct PolicyOption: View {
	let icon: String
	let titleKey: String
	let descKey: String
	let selected: Bool
	let onTap: () -> Void

	var body: some View {
		Button(action: onTap) {
			HStack(spacing: 12) {
				Image(systemName: icon)
					.font(.system(size: 20))
					.foregroundStyle(selected ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary))
					.frame(width: 22)
				VStack(alignment: .leading, spacing: 3) {
					Text(LocalizedStringKey(titleKey))
					Text(LocalizedStringKey(descKey)).font(.caption).foregroundStyle(.secondary)
				}
				Spacer()
				Image(systemName: selected ? "checkmark.circle.fill" : "circle")
					.foregroundStyle(selected ? AnyShapeStyle(.tint) : AnyShapeStyle(.tertiary))
			}
			.padding(14)
			.frame(maxWidth: .infinity)
			.background(.quaternary.opacity(selected ? 0.8 : 0.4), in: RoundedRectangle(cornerRadius: 14))
			.overlay(
				RoundedRectangle(cornerRadius: 14)
					.stroke(selected ? AnyShapeStyle(.tint) : AnyShapeStyle(.clear), lineWidth: 1.5)
			)
		}
		.buttonStyle(.plain)
	}
}
