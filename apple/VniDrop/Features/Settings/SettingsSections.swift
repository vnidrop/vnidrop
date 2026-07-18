import SwiftUI

/// Settings section detail views, rebuilt as native `Form` content. Each view is
/// placed inside a parent `Form`, so it returns `Section`s / rows directly.

struct PreferencesSettings: View {
	@ObservedObject var model: SettingsModel

	var body: some View {
		Section(String(localized: "field_username")) {
			TextField(String(localized: "field_username"),
					  text: Binding(get: { model.state.username }, set: model.setUsername))
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

struct AboutSettings: View {
	@ObservedObject var model: SettingsModel

	var body: some View {
		Section(String(localized: "about_title")) {
			LabeledContent(String(localized: "version_title"), value: model.state.appVersion)
			if let device = model.state.deviceInfo {
				LabeledContent(String(localized: "device_model_title"), value: device.deviceModel ?? "—")
				LabeledContent(String(localized: "os_version_title"), value: device.operatingSystem)
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
		Section {
			NavigationLink(value: SettingsSection.bugReport) {
				Text(LocalizedStringKey("about_bug_report"))
			}
		}
	}
}

struct BugReportSettings: View {
	@ObservedObject var model: SettingsModel

	var body: some View {
		Section(String(localized: "bug_report_what_label")) {
			TextField(String(localized: "bug_report_what_label"),
					  text: Binding(get: { model.state.bugWhatHappened }, set: model.setBugWhatHappened), axis: .vertical)
				.lineLimit(3, reservesSpace: true)
		}
		Section(String(localized: "bug_report_expected_label")) {
			TextField(String(localized: "bug_report_expected_label"),
					  text: Binding(get: { model.state.bugExpected }, set: model.setBugExpected), axis: .vertical)
				.lineLimit(3, reservesSpace: true)
		}
		Section(String(localized: "bug_report_steps_label")) {
			TextField(String(localized: "bug_report_steps_label"),
					  text: Binding(get: { model.state.bugSteps }, set: model.setBugSteps), axis: .vertical)
				.lineLimit(3, reservesSpace: true)
		}
		Section(String(localized: "bug_report_contact_label")) {
			TextField(String(localized: "bug_report_contact_label"),
					  text: Binding(get: { model.state.bugContact }, set: model.setBugContact))
		}
		Section {
			Toggle(isOn: Binding(get: { model.state.bugIncludeLogs }, set: { model.setBugIncludeLogs($0) })) {
				Text(LocalizedStringKey("bug_report_include_logs"))
			}
			Button(action: model.submitBugReport) {
				Text(model.state.isSubmittingBugReport
					 ? String(localized: "bug_report_submitting") : String(localized: "bug_report_submit"))
			}
			.disabled(model.state.isSubmittingBugReport)
		}
	}
}
