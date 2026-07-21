import XCTest
import VnidropCore
@testable import VniDrop

/// Relay preference persistence, the mapping into the core's `RelayMode`, and the
/// settings-model validation/restart-notice behaviour.
@MainActor
final class RelaySettingsTests: XCTestCase {

	private func makeModel(
		_ core: FakeCoreGateway = FakeCoreGateway(),
		preferences: AppPreferencesRepository
	) -> SettingsModel {
		SettingsModel(
			environment: PlatformEnvironment(name: "Test", appVersion: "0.1.0", defaultCoreDataDir: NSTemporaryDirectory()),
			deviceInfoProvider: FakeDeviceInfoProvider(),
			fileSystemService: FakeFileSystemService(),
			repository: core,
			preferences: preferences,
			notifications: LocalNotificationService(),
			messages: UiMessageController(),
			bugReports: NoopBugReportService(),
			diagnosticsIncluded: false
		)
	}

	// MARK: - Persistence

	func testDefaultsToStandardRelays() {
		let prefs = Fixtures.preferences()
		XCTAssertEqual(prefs.preferences.relay.mode, .standard)
		XCTAssertTrue(prefs.preferences.relay.customUrls.isEmpty)
	}

	func testRelaySettingsPersistAndReload() {
		let prefs = Fixtures.preferences()
		prefs.setRelay(RelaySettings(mode: .custom, customUrls: ["https://relay.example.org"]))

		XCTAssertEqual(prefs.preferences.relay.mode, .custom)
		XCTAssertEqual(prefs.preferences.relay.customUrls, ["https://relay.example.org"])
	}

	/// Switching away from custom keeps the URLs so they need not be retyped.
	func testSwitchingModeRetainsCustomUrls() {
		let prefs = Fixtures.preferences()
		prefs.setRelay(RelaySettings(mode: .custom, customUrls: ["https://relay.example.org"]))
		prefs.setRelay(RelaySettings(mode: .disabled, customUrls: prefs.preferences.relay.customUrls))

		XCTAssertEqual(prefs.preferences.relay.mode, .disabled)
		XCTAssertEqual(prefs.preferences.relay.customUrls, ["https://relay.example.org"])
	}

	// MARK: - Mapping into the core

	func testMapsStandardAndDisabled() {
		XCTAssertEqual(RelaySettings(mode: .standard).coreRelayMode, .default)
		XCTAssertEqual(RelaySettings(mode: .disabled).coreRelayMode, .disabled)
	}

	func testMapsCustomUrls() {
		let mode = RelaySettings(mode: .custom, customUrls: ["https://a.example.org", "https://b.example.org"])
			.coreRelayMode
		XCTAssertEqual(mode, .custom(urls: ["https://a.example.org", "https://b.example.org"]))
	}

	/// Custom with an empty relay map would silently be local-network only, which
	/// is not what the user asked for — fall back to the default relays instead.
	func testCustomWithoutUsableUrlsFallsBackToDefault() {
		XCTAssertEqual(RelaySettings(mode: .custom, customUrls: []).coreRelayMode, .default)
		XCTAssertEqual(RelaySettings(mode: .custom, customUrls: ["  ", "\n"]).coreRelayMode, .default)
	}

	// MARK: - Settings model

	func testSelectingLocalOnlyPersistsAndRequestsRestart() {
		let prefs = Fixtures.preferences()
		let model = makeModel(preferences: prefs)

		model.setRelayMode(.disabled)

		XCTAssertEqual(prefs.preferences.relay.mode, .disabled)
		XCTAssertTrue(model.state.relayNeedsRestart)
	}

	/// Returning to the mode the running core was built with clears the notice.
	func testRevertingToLaunchModeClearsRestartNotice() {
		let prefs = Fixtures.preferences()
		let model = makeModel(preferences: prefs)

		model.setRelayMode(.disabled)
		XCTAssertTrue(model.state.relayNeedsRestart)
		model.setRelayMode(.standard)
		XCTAssertFalse(model.state.relayNeedsRestart)
	}

	func testCommittingValidCustomUrlsPersistsThem() {
		let prefs = Fixtures.preferences()
		let model = makeModel(preferences: prefs)

		model.updateRelayCustomUrlsDraft("https://relay-1.example.org\n  https://relay-2.example.org  \n")
		model.commitRelayCustomUrls()

		XCTAssertNil(model.state.relayValidationError)
		XCTAssertEqual(
			prefs.preferences.relay.customUrls,
			["https://relay-1.example.org", "https://relay-2.example.org"]
		)
	}

	/// Validation runs through the core, so the CLI and both apps agree.
	func testInvalidSchemeIsRejectedAndNotPersisted() {
		let prefs = Fixtures.preferences()
		let model = makeModel(preferences: prefs)

		model.updateRelayCustomUrlsDraft("ftp://relay.example.org")
		model.commitRelayCustomUrls()

		XCTAssertNotNil(model.state.relayValidationError)
		XCTAssertEqual(prefs.preferences.relay.mode, .standard)
		XCTAssertTrue(prefs.preferences.relay.customUrls.isEmpty)
	}

	func testEmptyCustomUrlsAreRejected() {
		let prefs = Fixtures.preferences()
		let model = makeModel(preferences: prefs)

		model.updateRelayCustomUrlsDraft("   \n  ")
		model.commitRelayCustomUrls()

		XCTAssertNotNil(model.state.relayValidationError)
		XCTAssertEqual(prefs.preferences.relay.mode, .standard)
	}
}
