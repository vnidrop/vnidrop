import SwiftUI

/// Settings section detail views, rebuilt as native `Form` content. Each view is
/// placed inside a parent `Form`, so it returns `Section`s / rows directly.

struct PreferencesSettings: View {
	@ObservedObject var model: SettingsModel

	var body: some View {
		Section(String(localized: L10n.Field.username)) {
			TextField(String(localized: L10n.Field.username),
					  text: Binding(get: { model.state.username }, set: { model.setUsername($0) }))
		}
		if model.state.supportsCustomReceiveFolders {
			Section(String(localized: L10n.Preferences.receiveFolderTitle)) {
				Text(model.state.receiveFolder?.displayName ?? String(localized: L10n.Value.unavailable))
					.foregroundStyle(.secondary)
				Button(String(localized: L10n.Button.chooseFolder), action: model.chooseReceiveFolder)
				Button(String(localized: L10n.Button.resetDefault), action: model.resetReceiveFolder)
			}
		}
	}
}

struct AppearanceSettings: View {
	@ObservedObject var model: SettingsModel

	var body: some View {
		Section {
			Picker(String(localized: L10n.Appearance.title),
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

struct RelaySettingsSection: View {
	@ObservedObject var model: SettingsModel
	@FocusState private var urlsFocused: Bool

	var body: some View {
		Section {
			Picker(String(localized: L10n.Relay.title),
				   selection: Binding(get: { model.state.relay.mode }, set: { model.setRelayMode($0) })) {
				ForEach(RelayModeSetting.allCases, id: \.self) { mode in
					Text(relayModeLabel(mode)).tag(mode)
				}
			}
			.pickerStyle(.inline)
			.labelsHidden()
		} footer: {
			Text(String(localized: relayModeDescriptionKey(model.state.relay.mode)))
		}

		if model.state.relay.mode == .disabled {
			Section {
				Label(String(localized: L10n.Relay.noneWarning), systemImage: "exclamationmark.triangle")
					.foregroundStyle(.orange)
			}
		}

		if model.state.relay.mode == .custom {
			Section {
				TextEditor(text: Binding(
					get: { model.state.relayCustomUrlsDraft },
					set: { model.updateRelayCustomUrlsDraft($0) }
				))
				.frame(minHeight: 88)
				.font(.body.monospaced())
				.autocorrectionDisabled()
				#if os(iOS)
				.textInputAutocapitalization(.never)
				#endif
				.focused($urlsFocused)
				.onChange(of: urlsFocused) { _, focused in
					if !focused { model.commitRelayCustomUrls() }
				}
				Button(String(localized: L10n.Relay.customApply), action: model.commitRelayCustomUrls)
				if let error = model.state.relayValidationError {
					Text(error)
						.font(.footnote)
						.foregroundStyle(.red)
				}
			} header: {
				Text(String(localized: L10n.Relay.customUrlsLabel))
			} footer: {
				Text(String(localized: L10n.Relay.customUrlsHelp))
			}
		}

		if model.state.relayNeedsRestart {
			Section {
				Label(String(localized: L10n.Relay.restartRequired), systemImage: "arrow.clockwise")
			}
		}
	}
}

func relayModeLabel(_ mode: RelayModeSetting) -> String {
	switch mode {
	case .standard: return String(localized: L10n.Relay.modeDefault)
	case .custom: return String(localized: L10n.Relay.modeCustom)
	case .disabled: return String(localized: L10n.Relay.modeNone)
	}
}

private func relayModeDescriptionKey(_ mode: RelayModeSetting) -> String.LocalizationValue {
	switch mode {
	case .standard: return L10n.Relay.modeDefaultDescription
	case .custom: return L10n.Relay.modeCustomDescription
	case .disabled: return L10n.Relay.modeNoneDescription
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
				Text(String(localized: L10n.Notifications.localTitle))
			}
			if model.state.notificationPermission == .denied {
				Button(String(localized: L10n.Button.openSettings), action: model.openNotificationSettings)
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
				LabeledContent(String(localized: L10n.Storage.receivedFiles), value: formatBytes(storage.receivedFiles))
				LabeledContent(String(localized: L10n.Storage.transferData), value: formatBytes(storage.transferCache))
				LabeledContent(String(localized: L10n.Storage.appData), value: formatBytes(storage.appData))
				LabeledContent(String(localized: L10n.Storage.temporary), value: formatBytes(storage.temporary))
				LabeledContent(String(localized: L10n.Storage.total)) {
					Text(formatBytes(storage.total)).fontWeight(.semibold)
				}
			} else {
				HStack {
					Text(String(localized: L10n.Storage.calculating)).foregroundStyle(.secondary)
					Spacer()
					ProgressView()
				}
			}
		} footer: {
			Text(String(localized: L10n.Storage.footer))
		}

		Section {
			Button(role: .destructive) {
				showDeleteConfirmation = true
			} label: {
				HStack {
					Text(model.state.isDeletingTransfers
						 ? String(localized: L10n.Storage.deleting)
						 : String(localized: L10n.Storage.deleteTransfers))
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
			Text(String(localized: L10n.Storage.deleteTransfers)),
			isPresented: $showDeleteConfirmation,
			titleVisibility: .visible
		) {
			Button(String(localized: L10n.Storage.deleteTransfers), role: .destructive) {
				model.deleteAllTransfers()
			}
			Button(String(localized: L10n.Button.cancel), role: .cancel) {}
		} message: {
			Text(String(localized: L10n.Storage.deleteTransfersDescription))
		}
	}
}

struct AboutSettings: View {
	@ObservedObject var model: SettingsModel

	private static let privacyPolicyURL = URL(string: "https://github.com/vnidrop/vnidrop")!

	var body: some View {
		Section {
			Text(String(localized: L10n.About.tagline)).font(.headline)
			Text(String(localized: L10n.About.description)).foregroundStyle(.secondary)
		}

		Section(String(localized: L10n.About.isTitle)) {
			AboutPoint(L10n.About.isDirect, "paperplane")
			AboutPoint(L10n.About.isNoAccount, "person.crop.circle.badge.xmark")
			AboutPoint(L10n.About.isInControl, "checkmark.shield")
			AboutPoint(L10n.About.isEncrypted, "lock")
			AboutPoint(L10n.About.isOpen, "chevron.left.forwardslash.chevron.right")
		}

		Section(String(localized: L10n.About.isntTitle)) {
			AboutPoint(L10n.About.isntCloud, "icloud.slash")
			AboutPoint(L10n.About.isntSync, "arrow.triangle.2.circlepath")
			AboutPoint(L10n.About.isntPublic, "megaphone")
		}

		Section(String(localized: L10n.About.privacyTitle)) {
			AboutPoint(L10n.About.privacyCapability, "qrcode")
			AboutPoint(L10n.About.privacyDeny, "hand.raised")
			AboutPoint(L10n.About.privacyRelay, "antenna.radiowaves.left.and.right")
			AboutPoint(L10n.About.privacyLocal, "internaldrive")
		}

		Section(String(localized: L10n.About.title)) {
			LabeledContent(String(localized: L10n.Version.title), value: model.state.appVersion)
			if let device = model.state.deviceInfo {
				LabeledContent(String(localized: L10n.Device.modelTitle), value: device.deviceModel ?? "—")
				LabeledContent(String(localized: L10n.Os.versionTitle), value: device.operatingSystem)
			}
			LabeledContent(String(localized: L10n.About.licenseLabel), value: "Apache 2.0")
			Link(destination: Self.privacyPolicyURL) {
				Label(String(localized: L10n.About.privacyPolicyLabel), systemImage: "hand.raised")
			}
		}

		if DiagnosticsBuildConfig.included {
			Section {
				Toggle(isOn: Binding(
					get: { model.state.diagnosticsEnabled },
					set: { model.setDiagnosticsEnabled($0) }
				)) {
					Text(String(localized: L10n.Diagnostics.title))
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
			.navigationTitle(Text(String(localized: L10n.About.bugReport)))
			#if os(iOS)
			.navigationBarTitleDisplayMode(.inline)
			#endif
			.toolbar {
				ToolbarItem(placement: .cancellationAction) {
					Button(String(localized: L10n.Button.cancel)) { dismiss() }
				}
			}
		}
		.interactiveDismissDisabled(!isEmpty)
	}
}

/// A bullet-style informational row with an SF Symbol and wrapping localized text.
private struct AboutPoint: View {
	let key: String.LocalizationValue
	let symbol: String

	init(_ key: String.LocalizationValue, _ symbol: String) {
		self.key = key
		self.symbol = symbol
	}

	var body: some View {
		Label {
			Text(String(localized: key))
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
		Section(String(localized: L10n.Bug.reportWhatLabel)) {
			TextField("", text: Binding(get: { model.state.bugWhatHappened }, set: { model.setBugWhatHappened($0) }),
					  prompt: Text(String(localized: L10n.Bug.reportWhatHint)), axis: .vertical)
				.lineLimit(3, reservesSpace: true)
				.labelsHidden()
		}
		Section(String(localized: L10n.Bug.reportExpectedLabel)) {
			TextField("", text: Binding(get: { model.state.bugExpected }, set: { model.setBugExpected($0) }),
					  prompt: Text(String(localized: L10n.Bug.reportExpectedHint)), axis: .vertical)
				.lineLimit(3, reservesSpace: true)
				.labelsHidden()
		}
		Section(String(localized: L10n.Bug.reportStepsLabel)) {
			TextField("", text: Binding(get: { model.state.bugSteps }, set: { model.setBugSteps($0) }),
					  prompt: Text(String(localized: L10n.Bug.reportStepsHint)), axis: .vertical)
				.lineLimit(3, reservesSpace: true)
				.labelsHidden()
		}
		Section(String(localized: L10n.Bug.reportContactLabel)) {
			TextField("", text: Binding(get: { model.state.bugContact }, set: { model.setBugContact($0) }),
					  prompt: Text(String(localized: L10n.Bug.reportContactHint)))
				.labelsHidden()
		}
		Section {
			Toggle(isOn: Binding(get: { model.state.bugIncludeLogs }, set: { model.setBugIncludeLogs($0) })) {
				Text(String(localized: L10n.Bug.reportIncludeLogs))
			}
			Button(action: { model.submitBugReport(onSuccess: onSubmitted) }) {
				Text(model.state.isSubmittingBugReport
					 ? String(localized: L10n.Bug.reportSubmitting) : String(localized: L10n.Bug.reportSubmit))
			}
			.disabled(model.state.isSubmittingBugReport)
		}
	}
}
