import XCTest
@testable import VniDrop

/// Ports settings assertions from `feature/ViewModelsTest.kt` — username debounce
/// persistence and the Storage "delete all transfers" flow.
@MainActor
final class SettingsModelTests: XCTestCase {

	private func makeModel(_ core: FakeCoreGateway, preferences: AppPreferencesRepository) -> SettingsModel {
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

	func testUsernameChangeDebouncesAndPersists() async {
		let prefs = Fixtures.preferences(username: "Original")
		let model = makeModel(FakeCoreGateway(), preferences: prefs)

		model.setUsername("Alice")
		XCTAssertEqual(model.state.username, "Alice") // immediate local echo
		await waitUntil { prefs.preferences.username == "Alice" } // persisted after debounce
		XCTAssertEqual(prefs.preferences.username, "Alice")
	}

	func testDeleteAllTransfersDeletesEveryTransfer() async {
		let core = FakeCoreGateway()
		let model = makeModel(core, preferences: Fixtures.preferences())
		core.setState(CoreState(isInitialized: true, transfers: [
			Fixtures.transfer(id: 2, direction: .send, status: .sharing),
			Fixtures.transfer(id: 3, direction: .receive, status: .done),
		]))

		model.deleteAllTransfers()
		await waitUntil { core.deletedTransfers.count == 2 }
		XCTAssertEqual(Set(core.deletedTransfers), [2, 3])
	}
}
