import Foundation

/// App-facing domain models, ported from `core/CoreModels.kt`. The repository maps
/// the generated UniFFI records/enums into these so the UI never depends on the
/// binding surface directly.

struct CoreStatus: Equatable {
	let endpointId: String
	let activeTransfers: UInt64
	let activeShares: UInt64
}

struct CoreEventModel: Equatable, Identifiable {
	let id: String
	let timestamp: Int64
	let scope: String
	let transferId: UInt64?
	let direction: String?
	let phase: String
	let kind: String
	let dataJson: String
}

enum ShareAccessPolicy: Equatable {
	case requireApproval
	case anyoneWithTransfer
}

enum TransferDirection: Equatable {
	case send
	case receive
}

enum TransferStatus: Equatable {
	case importing
	case sharing
	case receiving
	case done
	case failed
	case cancelled
	case stopped
}

struct Transfer: Equatable, Identifiable {
	let localId: String
	let transferId: UInt64
	let direction: TransferDirection
	let status: TransferStatus
	let peerId: String?
	let transferName: String?
	let contentHash: String?
	let fileCount: UInt64
	let totalSize: UInt64
	let ticket: String?
	let accessPolicy: ShareAccessPolicy
	let createdAt: Int64
	let updatedAt: Int64

	var id: String { localId }
}

struct Share: Equatable {
	let transferId: UInt64
	let ticket: String
	let transferName: String
	let contentHash: String
	let fileCount: UInt64
	let totalSize: UInt64
}

struct TransferMetadataModel: Equatable {
	let transferId: UInt64
	let transferName: String
	let senderName: String?
	let contentHash: String
	let fileCount: UInt64
	let totalSize: UInt64
}

struct TicketInspectionModel: Equatable {
	let kind: String
	let metadata: TransferMetadataModel
}

enum ReceiverDeliveryStatus: Equatable {
	case requested
	case accepted
	case refused
	case expired
	case completed
	case unknown
}

struct ReceiverRequestModel: Equatable, Identifiable {
	let id: String
	let transferId: UInt64
	let remoteEndpointId: String
	let transferName: String
	let receiverName: String?
	let receiverDeviceName: String?
	let appVersion: String
	let status: ReceiverDeliveryStatus
	let reason: String?
	let requestedAt: Int64
	let respondedAt: Int64?
	let completedAt: Int64?
}

struct CoreState: Equatable {
	var isInitialized: Bool = false
	var status: CoreStatus?
	var events: [CoreEventModel] = []
	var transfers: [Transfer] = []
	var lastShare: Share?
	var lastInspection: TicketInspectionModel?
}

/// Coalesced change hints emitted from the event sink, ported from `CoreSignal`.
enum CoreSignal: Equatable {
	case approvalChanged(transferId: UInt64)
	case receiverHistoryChanged(transferId: UInt64)
	/// Transfer status/history changed enough to re-read the durable snapshot.
	case transfersChanged(transferId: UInt64)
}

// MARK: - Transfer helpers (ported from AppUiModels.kt)

extension TransferStatus {
	var isActiveTransfer: Bool {
		self == .importing || self == .sharing || self == .receiving
	}

	var canCancelTransfer: Bool {
		self == .importing || self == .sharing || self == .receiving
	}

	/// Terminal receive-history states eligible for deletion.
	var isTerminalReceiveHistory: Bool {
		self == .done || self == .failed || self == .cancelled
	}
}
