import XCTest
@testable import VniDrop

/// Ports `feature/send/FilePreviewRepositoryTest.kt` — persisted thumbnails,
/// restore pruned to live transfer ids, and removal.
@MainActor
final class FilePreviewRepositoryTests: XCTestCase {

	/// Minimal bytes that pass the PNG magic-byte check.
	private let png = Data([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])

	private func makeRepo() -> FilePreviewRepository {
		FilePreviewRepository(appDataDir: NSTemporaryDirectory() + "previews-" + UUID().uuidString)
	}

	func testSaveStoresPreview() {
		let repo = makeRepo()
		repo.save(transferId: 1, bytes: png)
		XCTAssertEqual(repo.previews[1], png)
	}

	func testSaveRejectsNonImageBytes() {
		let repo = makeRepo()
		repo.save(transferId: 1, bytes: Data("not an image".utf8))
		XCTAssertNil(repo.previews[1])
	}

	func testRestorePrunesToActiveIds() {
		let repo = makeRepo()
		repo.save(transferId: 1, bytes: png)
		repo.save(transferId: 2, bytes: png)
		repo.restore(activeTransferIds: [1])
		XCTAssertEqual(repo.previews[1], png)
		XCTAssertNil(repo.previews[2])
	}

	func testRemoveDeletesPreview() {
		let repo = makeRepo()
		repo.save(transferId: 1, bytes: png)
		repo.remove(transferId: 1)
		XCTAssertNil(repo.previews[1])
	}
}
