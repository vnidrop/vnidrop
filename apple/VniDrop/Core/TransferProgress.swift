import Foundation

/// Window size classes, ported from `AppUiModels.kt`. Thresholds are in points,
/// matching the Compose dp thresholds.
enum WindowClass {
	case phone
	case tablet
	case desktop
}

func windowClassFor(width: Double) -> WindowClass {
	if width >= 920 { return .desktop }
	if width >= 600 { return .tablet }
	return .phone
}

/// A progress snapshot derived from core events. `label` is a localization key
/// resolved at the view layer.
struct TransferProgress: Equatable {
	let transferId: UInt64?
	let phase: String
	let kind: String
	let labelKey: String
	let progress: Double?
	var detail: String? = nil
}

func statusLabelKey(_ status: TransferStatus) -> String {
	switch status {
	case .importing: return "status_preparing"
	case .sharing: return "status_available"
	case .receiving: return "status_receiving"
	case .done: return "status_completed"
	case .cancelled: return "status_cancelled"
	case .stopped: return "status_stopped"
	case .failed: return "status_failed"
	}
}

private let progressPhases: Set<String> = [
	"import", "ticket", "access", "transfer", "download", "export",
	"lifecycle", "network", "handshake", "error",
]

private let progressKinds: Set<String> = [
	"started", "copy-progress", "copy-done", "outboard-progress", "done",
	"created", "progress", "completed", "aborted", "failed",
	"connecting", "connected", "found-collection",
	"cancelled", "share-stopped",
]

/// Latest progress snapshot for a transfer. Events are newest-first.
func progressForTransfer(events: [CoreEventModel], transferId: UInt64) -> TransferProgress? {
	let relevant = events.filter { event in
		event.transferId == transferId
			&& progressPhases.contains(event.phase)
			&& progressKinds.contains(event.kind)
	}
	guard let latest = relevant.first else { return nil }
	let sizeHint = findKnownSize(events: events, transferId: transferId)
	return TransferProgress(
		transferId: transferId,
		phase: latest.phase,
		kind: latest.kind,
		labelKey: humanProgressLabel(latest),
		progress: parseProgress(latest.dataJson, sizeHint: sizeHint),
		detail: progressDetail(latest)
	)
}

/// Live byte progress for one receiver on an outgoing share.
func progressForReceiver(
	events: [CoreEventModel],
	transferId: UInt64,
	remoteEndpointId: String,
	totalSizeHint: UInt64? = nil
) -> TransferProgress? {
	if remoteEndpointId.isEmpty { return nil }
	let connectionIds = connectionIdsForEndpoint(events: events, remoteEndpointId: remoteEndpointId)
	let transferEvents = events.filter { event in
		event.transferId == transferId
			&& event.direction == "send"
			&& event.phase == "transfer"
			&& ["started", "progress", "completed", "aborted"].contains(event.kind)
			&& eventBelongsToReceiver(event, remoteEndpointId: remoteEndpointId, connectionIds: connectionIds)
	}
	if transferEvents.isEmpty { return nil }

	let latest = transferEvents[0]
	if latest.kind == "aborted" {
		return TransferProgress(
			transferId: transferId, phase: "transfer", kind: "aborted",
			labelKey: "progress_interrupted", progress: nil, detail: nil
		)
	}
	if latest.kind == "completed" && !transferEvents.contains(where: { $0.kind == "progress" || $0.kind == "started" }) {
		return TransferProgress(
			transferId: transferId, phase: "transfer", kind: "completed",
			labelKey: "progress_completed", progress: 1, detail: nil
		)
	}

	let progress = aggregateReceiverProgress(events: transferEvents, totalSizeHint: totalSizeHint)
	return TransferProgress(
		transferId: transferId, phase: "transfer", kind: latest.kind,
		labelKey: "progress_sending", progress: progress, detail: progressDetail(latest)
	)
}

/// Best send-side progress for a transfer (any receiver).
func activeSendProgress(
	events: [CoreEventModel],
	transferId: UInt64,
	totalSizeHint: UInt64? = nil
) -> TransferProgress? {
	let endpointIds = events
		.filter { $0.transferId == transferId && $0.direction == "send" && $0.phase == "transfer" }
		.compactMap { findString($0.dataJson, key: "endpoint_id") }
		.reduce(into: [String]()) { acc, id in if !acc.contains(id) { acc.append(id) } }

	if endpointIds.isEmpty {
		let relevant = events.filter {
			$0.transferId == transferId && $0.direction == "send" && $0.phase == "transfer"
				&& ["started", "progress"].contains($0.kind)
		}
		guard let first = relevant.first else { return nil }
		return TransferProgress(
			transferId: transferId, phase: "transfer", kind: first.kind,
			labelKey: "progress_sending",
			progress: aggregateReceiverProgress(events: relevant, totalSizeHint: totalSizeHint),
			detail: progressDetail(first)
		)
	}
	let all = endpointIds.compactMap {
		progressForReceiver(events: events, transferId: transferId, remoteEndpointId: $0, totalSizeHint: totalSizeHint)
	}
	return all.first { $0.kind == "progress" || $0.kind == "started" } ?? all.first
}

func formatBytes(_ size: UInt64) -> String {
	var scaled = Double(size)
	let units = ["B", "KB", "MB", "GB", "TB"]
	var unitIndex = 0
	while scaled >= 1024 && unitIndex < units.count - 1 {
		scaled /= 1024
		unitIndex += 1
	}
	if unitIndex == 0 {
		return "\(size) \(units[unitIndex])"
	}
	let rounded = (scaled * 10).rounded() / 10
	return "\(rounded) \(units[unitIndex])"
}

// MARK: - Internals (ported literally from AppUiModels.kt)

private func humanProgressLabel(_ event: CoreEventModel) -> String {
	switch (event.phase, event.kind) {
	case ("import", "copy-progress"), ("import", "outboard-progress"), ("import", "started"):
		return "progress_preparing"
	case ("import", "done"): return "progress_ready"
	case ("ticket", "created"): return "progress_share_ready"
	case ("network", "connecting"): return "progress_connecting"
	case ("network", "connected"): return "progress_connected"
	case ("download", "found-collection"): return "progress_getting_ready"
	case ("download", "progress"): return "progress_downloading"
	case ("export", "progress"): return "progress_saving"
	case ("transfer", "progress"): return "progress_sending"
	case ("transfer", "started"): return "progress_connected"
	case ("transfer", "completed"): return "progress_completed"
	case ("lifecycle", "done"): return "progress_completed"
	case ("lifecycle", "cancelled"): return "progress_cancelled"
	default:
		if event.phase == "handshake" { return "progress_requesting_access" }
		if event.kind == "failed" { return "progress_failed" }
		return "progress_working"
	}
}

private func progressDetail(_ event: CoreEventModel) -> String? {
	let fileName = findString(event.dataJson, key: "file_name")
	let current = findNumber(event.dataJson, key: "current_file_index").map { Int64($0) }
	let totalFiles = findNumber(event.dataJson, key: "total_files").map { Int64($0) }
	if let fileName, let current, let totalFiles, totalFiles > 0 {
		return "\(fileName) (\(current + 1)/\(totalFiles))"
	}
	return fileName
}

func parseProgress(_ json: String, sizeHint: Double? = nil) -> Double? {
	let transferred = findNumber(json, key: "exported")
		?? findNumber(json, key: "downloaded")
		?? findNumber(json, key: "offset")
		?? findNumber(json, key: "end_offset")
		?? findNumber(json, key: "transferred")
		?? findNumber(json, key: "written")
	let total = findNumber(json, key: "file_size")
		?? findNumber(json, key: "total_size")
		?? findNumber(json, key: "size")
		?? findNumber(json, key: "total")
		?? sizeHint
	guard let transferred, let total, total > 0 else { return nil }
	return min(1, max(0, transferred / total))
}

private func findKnownSize(events: [CoreEventModel], transferId: UInt64) -> Double? {
	for event in events where event.transferId == transferId {
		if let s = findNumber(event.dataJson, key: "size"), s > 0 { return s }
		if let s = findNumber(event.dataJson, key: "total_size"), s > 0 { return s }
		if let s = findNumber(event.dataJson, key: "file_size"), s > 0 { return s }
	}
	return nil
}

private final class BlobState {
	var size: Double?
	var offset: Double = 0
	var completed = false
	var aborted = false
}

private func aggregateReceiverProgress(events: [CoreEventModel], totalSizeHint: UInt64?) -> Double? {
	let chronological = events.reversed()
	var byRequest = [String: BlobState]()
	var order = [String]()
	var connectionScopedOffset: Double?
	var connectionScopedSize: Double?

	for event in chronological {
		let requestKey = findNumber(event.dataJson, key: "request_id").map { String(Int64($0)) }
			?? findString(event.dataJson, key: "request_id")
		let size = findNumber(event.dataJson, key: "size")
		let endOffset = findNumber(event.dataJson, key: "end_offset")
			?? findNumber(event.dataJson, key: "offset")
			?? findNumber(event.dataJson, key: "transferred")

		if let requestKey {
			let state: BlobState
			if let existing = byRequest[requestKey] {
				state = existing
			} else {
				state = BlobState()
				byRequest[requestKey] = state
				order.append(requestKey)
			}
			if let size, size > 0 { state.size = size }
			switch event.kind {
			case "progress", "started":
				if let endOffset { state.offset = max(state.offset, endOffset) }
				state.aborted = false
			case "completed":
				state.completed = true
				if let s = state.size { state.offset = s }
			case "aborted":
				state.aborted = true
			default:
				break
			}
		} else {
			if let size, size > 0 { connectionScopedSize = size }
			if let endOffset { connectionScopedOffset = endOffset }
		}
	}

	if !byRequest.isEmpty {
		let active = order.compactMap { byRequest[$0] }.filter { !$0.aborted }
		if active.isEmpty { return nil }
		let transferred = active.reduce(0.0) { acc, state in
			acc + (state.completed ? (state.size ?? state.offset) : state.offset)
		}
		let observedSize = active.compactMap { $0.size }.reduce(0, +)
		let total = totalSizeHint.map { Double($0) }.flatMap { $0 > 0 ? $0 : nil }
			?? (observedSize > 0 ? observedSize : nil)
		guard let total, total > 0 else { return nil }
		return min(1, max(0, transferred / total))
	}

	let total = totalSizeHint.map { Double($0) }.flatMap { $0 > 0 ? $0 : nil }
		?? connectionScopedSize.flatMap { $0 > 0 ? $0 : nil }
	guard let transferred = connectionScopedOffset, let total, total > 0 else { return nil }
	return min(1, max(0, transferred / total))
}

private func connectionIdsForEndpoint(events: [CoreEventModel], remoteEndpointId: String) -> Set<String> {
	var ids = Set<String>()
	for event in events {
		guard let endpoint = findString(event.dataJson, key: "endpoint_id"), endpoint == remoteEndpointId else { continue }
		if let n = findNumber(event.dataJson, key: "connection_id") { ids.insert(String(Int64(n))) }
		if let s = findString(event.dataJson, key: "connection_id") { ids.insert(s) }
	}
	return ids
}

private func eventBelongsToReceiver(_ event: CoreEventModel, remoteEndpointId: String, connectionIds: Set<String>) -> Bool {
	if let endpoint = findString(event.dataJson, key: "endpoint_id") {
		return endpoint == remoteEndpointId
	}
	let connectionId = findNumber(event.dataJson, key: "connection_id").map { String(Int64($0)) }
		?? findString(event.dataJson, key: "connection_id")
	guard let connectionId else { return false }
	return connectionIds.contains(connectionId)
}

// Lightweight JSON scraping, ported from AppUiModels.kt (matches core event shapes).
func findNumber(_ json: String, key: String) -> Double? {
	let marker = "\"\(key)\":"
	guard let range = json.range(of: marker) else { return nil }
	let after = json[range.upperBound...].drop { $0 == " " }
	if after.hasPrefix("null") { return nil }
	let terminators: Set<Character> = [",", "}", "]"]
	var value = ""
	for ch in json[range.upperBound...] {
		if terminators.contains(ch) { break }
		value.append(ch)
	}
	let trimmed = value.trimmingCharacters(in: .whitespaces).trimmingCharacters(in: CharacterSet(charactersIn: "\""))
	return Double(trimmed)
}

func findString(_ json: String, key: String) -> String? {
	let marker = "\"\(key)\":"
	guard let range = json.range(of: marker) else { return nil }
	let after = json[range.upperBound...].drop { $0 == " " }
	if after.hasPrefix("null") { return nil }
	guard after.hasPrefix("\"") else { return nil }
	let content = after.dropFirst()
	guard let endIdx = content.firstIndex(of: "\"") else { return nil }
	if content.startIndex == endIdx { return nil }
	return String(content[content.startIndex..<endIdx])
}
