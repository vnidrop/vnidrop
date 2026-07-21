import Foundation
import Combine

/// Native Apple preview cache and thumbnail loader.
/// Only small PNG/JPEG/WEBP previews are retained, under a total quota.
struct PreviewStoragePolicy {
	var maxEntryBytes: Int = 512 * 1024
	var maxTotalBytes: Int64 = 20 * 1024 * 1024
}

private struct PreviewFileInfo {
	let transferId: UInt64
	let byteSize: Int64
	let modifiedAtMillis: Int64
}

@MainActor
final class FilePreviewRepository: ObservableObject {
	@Published private(set) var previews: [UInt64: Data] = [:]

	private let directory: String
	private let policy: PreviewStoragePolicy
	private let fm = FileManager.default

	init(appDataDir: String, policy: PreviewStoragePolicy = PreviewStoragePolicy()) {
		self.directory = (appDataDir as NSString).appendingPathComponent("ui/previews")
		self.policy = policy
	}

	func restore(activeTransferIds: Set<UInt64>) {
		let files = listFiles()
		for file in files where !(activeTransferIds.contains(file.transferId)
			&& (1...Int64(policy.maxEntryBytes)).contains(file.byteSize)) {
			deleteFile(file.transferId)
		}
		enforceQuota()
		var result: [UInt64: Data] = [:]
		for file in listFiles() where activeTransferIds.contains(file.transferId) {
			if let data = readFile(file.transferId), data.isSupportedPreview, data.count <= policy.maxEntryBytes {
				result[file.transferId] = data
			} else {
				deleteFile(file.transferId)
			}
		}
		previews = result
	}

	func save(transferId: UInt64, bytes: Data) {
		guard (1...policy.maxEntryBytes).contains(bytes.count), bytes.isSupportedPreview else { return }
		guard writeAtomically(transferId: transferId, bytes: bytes) else { return }
		enforceQuota(protectedTransferId: transferId)
		if readFile(transferId) != nil {
			previews[transferId] = bytes
		}
	}

	func remove(transferId: UInt64) {
		deleteFile(transferId)
		previews.removeValue(forKey: transferId)
	}

	// MARK: - Store (ported from IosPreviewStore)

	private func enforceQuota(protectedTransferId: UInt64? = nil) {
		let files = listFiles().sorted { $0.modifiedAtMillis < $1.modifiedAtMillis }
		var total = files.reduce(Int64(0)) { $0 + $1.byteSize }
		for file in files {
			if total <= policy.maxTotalBytes { break }
			if file.transferId == protectedTransferId { continue }
			deleteFile(file.transferId)
			total -= file.byteSize
			previews.removeValue(forKey: file.transferId)
		}
	}

	private func ensureDirectory() {
		try? fm.createDirectory(atPath: directory, withIntermediateDirectories: true)
	}

	private func path(_ transferId: UInt64) -> String {
		(directory as NSString).appendingPathComponent("\(transferId).preview")
	}

	private func listFiles() -> [PreviewFileInfo] {
		ensureDirectory()
		guard let names = try? fm.contentsOfDirectory(atPath: directory) else { return [] }
		return names.compactMap { name in
			guard name.hasSuffix(".preview"),
				let id = UInt64(name.replacingOccurrences(of: ".preview", with: "")) else { return nil }
			let full = (directory as NSString).appendingPathComponent(name)
			guard let attrs = try? fm.attributesOfItem(atPath: full) else { return nil }
			let size = (attrs[.size] as? NSNumber)?.int64Value ?? 0
			let modified = ((attrs[.modificationDate] as? Date)?.timeIntervalSince1970 ?? 0) * 1000
			return PreviewFileInfo(transferId: id, byteSize: size, modifiedAtMillis: Int64(modified))
		}
	}

	private func readFile(_ transferId: UInt64) -> Data? {
		try? Data(contentsOf: URL(fileURLWithPath: path(transferId)))
	}

	private func writeAtomically(transferId: UInt64, bytes: Data) -> Bool {
		ensureDirectory()
		if fm.fileExists(atPath: path(transferId)) { return true }
		let temporary = (directory as NSString).appendingPathComponent(".\(transferId).tmp")
		do {
			try bytes.write(to: URL(fileURLWithPath: temporary), options: .atomic)
		} catch {
			return false
		}
		do {
			if fm.fileExists(atPath: path(transferId)) {
				try? fm.removeItem(atPath: temporary)
				return true
			}
			try fm.moveItem(atPath: temporary, toPath: path(transferId))
			return true
		} catch {
			try? fm.removeItem(atPath: temporary)
			return false
		}
	}

	private func deleteFile(_ transferId: UInt64) {
		try? fm.removeItem(atPath: path(transferId))
	}
}

extension Data {
	/// Matches `isSupportedPreview()`: PNG / JPEG / WEBP magic bytes.
	var isSupportedPreview: Bool {
		let bytes = [UInt8](self)
		let png = bytes.count >= 8 && bytes[0] == 0x89
			&& bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47   // "PNG"
		let jpeg = bytes.count >= 3 && bytes[0] == 0xFF && bytes[1] == 0xD8 && bytes[2] == 0xFF
		let webp = bytes.count >= 12
			&& bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46   // "RIFF"
			&& bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50 // "WEBP"
		return png || jpeg || webp
	}
}
