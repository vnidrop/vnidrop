import XCTest
@testable import VniDrop

/// Ports `ui/feedback/UiMessageControllerTest.kt` and `UserFacingErrorTest.kt`.
@MainActor
final class UiMessageControllerTests: XCTestCase {

	func testQueuesAndAdvances() {
		let c = UiMessageController()
		c.show(UiMessage(text: .dynamic("first")))
		c.show(UiMessage(text: .dynamic("second")))
		XCTAssertEqual(c.current?.text, .dynamic("first"))

		c.advance()
		XCTAssertEqual(c.current?.text, .dynamic("second"))

		c.advance()
		XCTAssertNil(c.current)
	}

	func testErrorSuppressesUserCancellation() {
		let c = UiMessageController()
		c.error(InvitationError.message("QR scanning was cancelled"))
		XCTAssertNil(c.current) // cancellations are swallowed
	}

	func testErrorShowsNonCancellation() {
		let c = UiMessageController()
		c.error(InvitationError.message("The transfer was refused"))
		XCTAssertEqual(c.current?.tone, .error)
	}
}

@MainActor
final class UserFacingErrorTests: XCTestCase {

	func testIsUserCancellation() {
		XCTAssertTrue(InvitationError.message("NFC reading was cancelled").isUserCancellation)
		XCTAssertTrue(InvitationError.message("User canceled the picker").isUserCancellation)
		XCTAssertFalse(InvitationError.message("A database error occurred").isUserCancellation)
	}

	func testToUiTextMapsKnownReasons() {
		XCTAssertEqual(InvitationError.message("The transfer was refused").toUiText(), .resource("error_permission"))
		XCTAssertEqual(InvitationError.message("invalid ticket").toUiText(), .resource("error_invalid_ticket"))
		XCTAssertEqual(InvitationError.message("Select at least one file to share").toUiText(), .resource("error_share_empty"))
		XCTAssertEqual(InvitationError.message("Camera access is required").toUiText(), .resource("error_camera"))
		// Couples to the core's connect-failure annotation phrase; keep in sync.
		XCTAssertEqual(
			InvitationError.message("could not connect directly with relays disabled").toUiText(),
			.resource("error_relay_direct_failed")
		)
	}

	func testToUiTextFallsBackToGeneric() {
		XCTAssertEqual(InvitationError.message("something entirely unexpected").toUiText(), .resource("error_generic"))
	}
}
