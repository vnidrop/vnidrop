import SwiftUI

/// Settings section detail views, rebuilt as native `Form` content. Each view is
/// placed inside a parent `Form`, so it returns `Section`s / rows directly.

struct PreferencesSettings: View {
	@ObservedObject var model: SettingsModel

	var body: some View {
		Section(String(localized: "field_username")) {
			TextField(String(localized: "field_username"),
					  text: Binding(get: { model.state.username }, set: { model.setUsername($0) }))
		}
		if model.state.supportsCustomReceiveFolders {
			Section(String(localized: "preferences_receive_folder_title")) {
				Text(model.state.receiveFolder?.displayName ?? String(localized: "value_unavailable"))
					.foregroundStyle(.secondary)
				Button(String(localized: "button_choose_folder"), action: model.chooseReceiveFolder)
				Button(String(localized: "button_reset_default"), action: model.resetReceiveFolder)
			}
		}
	}
}

struct AppearanceSettings: View {
	@ObservedObject var model: SettingsModel

	var body: some View {
		Section {
			Picker(String(localized: "appearance_title"),
				   selection: Binding(get: { model.state.themeMode }, set: { model.setThemeMode($0) })) {
				ForEach(ThemeMode.allCases, id: \.self) { mode in
					Text(themeModeLabel(mode)).tag(mode)
				}
			}
			.pickerStyle(.inline)
			.labelsHidden()
		}
	}
}

struct NotificationSettings: View {
	@ObservedObject var model: SettingsModel

	var body: some View {
		Section {
			Toggle(isOn: Binding(
				get: { model.state.notificationsEnabled },
				set: { model.setNotificationsEnabled($0) }
			)) {
				Text(LocalizedStringKey("notifications_local_title"))
			}
			if model.state.notificationPermission == .denied {
				Button(String(localized: "button_open_settings"), action: model.openNotificationSettings)
			}
		}
	}
}

struct StorageSettings: View {
	@ObservedObject var model: SettingsModel
	@State private var showDeleteConfirmation = false

	var body: some View {
		Section {
			if let storage = model.state.storage {
				LabeledContent(String(localized: "storage_received_files"), value: formatBytes(storage.receivedFiles))
				LabeledContent(String(localized: "storage_transfer_data"), value: formatBytes(storage.transferData))
				LabeledContent(String(localized: "storage_temporary"), value: formatBytes(storage.temporary))
				LabeledContent(String(localized: "storage_total")) {
					Text(formatBytes(storage.total)).fontWeight(.semibold)
				}
			} else {
				HStack {
					Text(LocalizedStringKey("storage_calculating")).foregroundStyle(.secondary)
					Spacer()
					ProgressView()
				}
			}
		} footer: {
			Text(LocalizedStringKey("storage_footer"))
		}

		Section {
			Button(role: .destructive) {
				showDeleteConfirmation = true
			} label: {
				HStack {
					Text(model.state.isDeletingTransfers
						 ? String(localized: "storage_deleting")
						 : String(localized: "storage_delete_transfers"))
					if model.state.isDeletingTransfers {
						Spacer()
						ProgressView()
					}
				}
			}
			.disabled(model.state.isDeletingTransfers)
		}
		.onAppear { model.loadStorageUsage() }
		.confirmationDialog(
			Text(LocalizedStringKey("storage_delete_transfers")),
			isPresented: $showDeleteConfirmation,
			titleVisibility: .visible
		) {
			Button(String(localized: "storage_delete_transfers"), role: .destructive) {
				model.deleteAllTransfers()
			}
			Button(String(localized: "button_cancel"), role: .cancel) {}
		} message: {
			Text(LocalizedStringKey("storage_delete_transfers_description"))
		}
	}
}

struct AboutSettings: View {
	@ObservedObject var model: SettingsModel

	private static let sourceURL = URL(string: "https://github.com/vnidrop/vnidrop")!

	var body: some View {
		Section {
			Text(LocalizedStringKey("about_tagline")).font(.headline)
			Text(LocalizedStringKey("about_description")).foregroundStyle(.secondary)
		}

		Section(String(localized: "about_is_title")) {
			AboutPoint("about_is_direct", "paperplane")
			AboutPoint("about_is_no_account", "person.crop.circle.badge.xmark")
			AboutPoint("about_is_in_control", "checkmark.shield")
			AboutPoint("about_is_encrypted", "lock")
			AboutPoint("about_is_open", "chevron.left.forwardslash.chevron.right")
		}

		Section(String(localized: "about_isnt_title")) {
			AboutPoint("about_isnt_cloud", "icloud.slash")
			AboutPoint("about_isnt_sync", "arrow.triangle.2.circlepath")
			AboutPoint("about_isnt_public", "megaphone")
		}

		Section(String(localized: "about_privacy_title")) {
			AboutPoint("about_privacy_capability", "qrcode")
			AboutPoint("about_privacy_deny", "hand.raised")
			AboutPoint("about_privacy_relay", "antenna.radiowaves.left.and.right")
			AboutPoint("about_privacy_local", "internaldrive")
		}

		Section(String(localized: "about_title")) {
			LabeledContent(String(localized: "version_title"), value: model.state.appVersion)
			if let device = model.state.deviceInfo {
				LabeledContent(String(localized: "device_model_title"), value: device.deviceModel ?? "—")
				LabeledContent(String(localized: "os_version_title"), value: device.operatingSystem)
			}
			LabeledContent(String(localized: "about_license_label"), value: "Apache 2.0")
			Link(destination: Self.sourceURL) {
				Label(String(localized: "about_source_label"), systemImage: "link")
			}
		}

		if DiagnosticsBuildConfig.included {
			Section {
				Toggle(isOn: Binding(
					get: { model.state.diagnosticsEnabled },
					set: { model.setDiagnosticsEnabled($0) }
				)) {
					Text(LocalizedStringKey("diagnostics_title"))
				}
			}
		}
	}
}

/// Bug report presented as a sheet from About. Can be dismissed by swipe only
/// when empty; otherwise the Cancel button is required.
struct BugReportSheet: View {
	@ObservedObject var model: SettingsModel
	@Environment(\.dismiss) private var dismiss

	private var isEmpty: Bool {
		model.state.bugWhatHappened.isEmpty && model.state.bugExpected.isEmpty
			&& model.state.bugSteps.isEmpty && model.state.bugContact.isEmpty
	}

	var body: some View {
		NavigationStack {
			Form {
				BugReportSettings(model: model, onSubmitted: { dismiss() })
			}
			.formStyle(.grouped)
			.navigationTitle(Text(LocalizedStringKey("about_bug_report")))
			#if os(iOS)
			.navigationBarTitleDisplayMode(.inline)
			#endif
			.toolbar {
				ToolbarItem(placement: .cancellationAction) {
					Button(String(localized: "button_cancel")) { dismiss() }
				}
			}
		}
		.interactiveDismissDisabled(!isEmpty)
	}
}

/// A bullet-style informational row with an SF Symbol and wrapping localized text.
private struct AboutPoint: View {
	let key: String
	let symbol: String

	init(_ key: String, _ symbol: String) {
		self.key = key
		self.symbol = symbol
	}

	var body: some View {
		Label {
			Text(LocalizedStringKey(key))
				.font(.subheadline)
				.fixedSize(horizontal: false, vertical: true)
		} icon: {
			Image(systemName: symbol).foregroundStyle(.tint)
		}
	}
}

struct BugReportSettings: View {
	@ObservedObject var model: SettingsModel
	var onSubmitted: () -> Void = {}

	var body: some View {
		Section(String(localized: "bug_report_what_label")) {
			TextField("", text: Binding(get: { model.state.bugWhatHappened }, set: { model.setBugWhatHappened($0) }),
					  prompt: Text(LocalizedStringKey("bug_report_what_hint")), axis: .vertical)
				.lineLimit(3, reservesSpace: true)
				.labelsHidden()
		}
		Section(String(localized: "bug_report_expected_label")) {
			TextField("", text: Binding(get: { model.state.bugExpected }, set: { model.setBugExpected($0) }),
					  prompt: Text(LocalizedStringKey("bug_report_expected_hint")), axis: .vertical)
				.lineLimit(3, reservesSpace: true)
				.labelsHidden()
		}
		Section(String(localized: "bug_report_steps_label")) {
			TextField("", text: Binding(get: { model.state.bugSteps }, set: { model.setBugSteps($0) }),
					  prompt: Text(LocalizedStringKey("bug_report_steps_hint")), axis: .vertical)
				.lineLimit(3, reservesSpace: true)
				.labelsHidden()
		}
		Section(String(localized: "bug_report_contact_label")) {
			TextField("", text: Binding(get: { model.state.bugContact }, set: { model.setBugContact($0) }),
					  prompt: Text(LocalizedStringKey("bug_report_contact_hint")))
				.labelsHidden()
		}
		Section {
			Toggle(isOn: Binding(get: { model.state.bugIncludeLogs }, set: { model.setBugIncludeLogs($0) })) {
				Text(LocalizedStringKey("bug_report_include_logs"))
			}
			Button(action: { model.submitBugReport(onSuccess: onSubmitted) }) {
				Text(model.state.isSubmittingBugReport
					 ? String(localized: "bug_report_submitting") : String(localized: "bug_report_submit"))
			}
			.disabled(model.state.isSubmittingBugReport)
		}
	}
}
