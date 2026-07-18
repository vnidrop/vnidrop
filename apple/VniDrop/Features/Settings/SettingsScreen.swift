import SwiftUI

/// Settings screen, rebuilt on a native `Form` with `NavigationStack` push
/// navigation. The model stays the source of truth via a derived path binding.
struct SettingsScreen: View {
	@ObservedObject var model: SettingsModel
	let windowClass: WindowClass

	private var path: Binding<[SettingsSection]> {
		Binding(
			get: {
				switch model.state.selectedSection {
				case .overview: return []
				case .bugReport: return [.about, .bugReport]
				case let section: return [section]
				}
			},
			set: { newPath in model.selectSection(newPath.last ?? .overview) }
		)
	}

	var body: some View {
		NavigationStack(path: path) {
			Form {
				Section {
					NavigationLink(value: SettingsSection.preferences) {
						SettingsRow(icon: "person.crop.circle", title: String(localized: "preferences_title"), value: model.state.username)
					}
					NavigationLink(value: SettingsSection.appearance) {
						SettingsRow(icon: "sun.max", title: String(localized: "appearance_title"), value: themeModeLabel(model.state.themeMode))
					}
				}
				Section {
					NavigationLink(value: SettingsSection.notifications) {
						SettingsRow(icon: "bell", title: String(localized: "notifications_title"), value: nil)
					}
					NavigationLink(value: SettingsSection.about) {
						SettingsRow(icon: "info.circle", title: String(localized: "about_title"), value: nil)
					}
				}
			}
			.formStyle(.grouped)
			.navigationTitle(Text(LocalizedStringKey("settings_title")))
			.navigationDestination(for: SettingsSection.self) { section in
				Form {
					SettingsSectionContent(model: model, section: section)
				}
				.formStyle(.grouped)
				.navigationTitle(Text(LocalizedStringKey(section.titleKey)))
				#if os(iOS)
				.navigationBarTitleDisplayMode(.inline)
				#endif
			}
		}
	}
}

private struct SettingsSectionContent: View {
	@ObservedObject var model: SettingsModel
	let section: SettingsSection

	var body: some View {
		switch section {
		case .overview:
			EmptyView()
		case .preferences:
			PreferencesSettings(model: model)
		case .appearance:
			AppearanceSettings(model: model)
		case .notifications:
			NotificationSettings(model: model)
		case .about:
			AboutSettings(model: model)
		case .bugReport:
			BugReportSettings(model: model)
		}
	}
}

struct SettingsRow: View {
	let icon: String
	let title: String
	let value: String?

	var body: some View {
		HStack(spacing: 12) {
			Image(systemName: icon)
				.foregroundStyle(.tint)
				.frame(width: 26)
			Text(title).foregroundStyle(.primary)
			Spacer()
			if let value {
				Text(value).foregroundStyle(.secondary).lineLimit(1)
			}
		}
	}
}

func themeModeLabel(_ mode: ThemeMode) -> String {
	switch mode {
	case .system: return String(localized: "appearance_system_mode")
	case .light: return String(localized: "appearance_light_mode")
	case .dark: return String(localized: "appearance_dark_mode")
	}
}
