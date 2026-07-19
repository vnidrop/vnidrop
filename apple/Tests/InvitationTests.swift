import XCTest
@testable import VniDrop

/// Ports `feature/receive/ExternalInvitationControllerTest.kt` + the `.vnd`
/// decode/filename helpers.
@MainActor
final class InvitationTests: XCTestCase {

	func testValidateInvitationAcceptsValid() {
		guard case .success(let raw) = validateInvitation("some-ticket") else { return XCTFail("expected success") }
		XCTAssertEqual(raw, "some-ticket")
	}

	func testValidateRejectsEmpty() {
		guard case .failure(let error) = validateInvitation("   \n ") else { return XCTFail("expected failure") }
		XCTAssertTrue((error as? InvitationError) != nil)
	}

	func testValidateRejectsTooLarge() {
		let big = String(repeating: "a", count: maxVniDropInvitationBytes + 1)
		guard case .failure = validateInvitation(big) else { return XCTFail("expected failure") }
	}

	func testDecodeInvitationBytesRoundTrip() throws {
		let text = "vnidrop://ticket-abc"
		let decoded = try decodeInvitationBytes(Data(text.utf8))
		XCTAssertEqual(decoded, text)
	}

	func testDecodeRejectsEmptyData() {
		XCTAssertThrowsError(try decodeInvitationBytes(Data()))
	}

	func testDecodeRejectsInvalidUtf8() {
		XCTAssertThrowsError(try decodeInvitationBytes(Data([0xFF, 0xFE, 0xFD])))
	}

	func testInvitationFileNameSanitizes() {
		XCTAssertEqual(invitationFileName("My Photos"), "My-Photos.vnd")
		XCTAssertEqual(invitationFileName("  "), "invitation.vnd")
		XCTAssertTrue(invitationFileName("a/b:c*d").hasSuffix(".vnd"))
	}
}
