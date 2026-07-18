import Foundation
import Combine

/// A pending receiver approval, ported from `feature/approvals/ApprovalCoordinator.kt`.
struct PendingApproval: Equatable, Identifiable {
	let id: String
	let transferId: UInt64
	let transferName: String
	let receiverName: String?
	let receiverDeviceName: String?
	/// Cryptographic peer identity — not display-name spoofable.
	let remoteEndpointId: String
	let requestedAt: Int64
}

struct ApprovalState: Equatable {
	var pending: [PendingApproval] = []
	var respondingIds: Set<String> = []

	var current: PendingApproval? { pending.first }
}

/// Drives receiver-approval prompts and their notifications, ported from
/// `ApprovalCoordinator.kt`.
@MainActor
final class ApprovalCoordinator: ObservableObject {
	@Published private(set) var state = ApprovalState()

	private let repository: CoreRepository
	private let preferences: AppPreferencesRepository
	private let notifications: LocalNotificationService
	private let visibility: AppVisibility
	private let messages: UiMessageController

	private var publishedNotificationIds = Set<String>()
	private var cancellables = Set<AnyCancellable>()

	init(
		repository: CoreRepository,
		preferences: AppPreferencesRepository,
		notifications: LocalNotificationService,
		visibility: AppVisibility,
		messages: UiMessageController
	) {
		self.repository = repository
		self.preferences = preferences
		self.notifications = notifications
		self.visibility = visibility
		self.messages = messages

		repository.signals
			.sink { [weak self] signal in
				guard let self else { return }
				if case .approvalChanged(let transferId) = signal {
					Task { await self.refresh(transferId: transferId) }
				}
			}
			.store(in: &cancellables)

		repository.$state
			.sink { [weak self] core in
				guard let self, core.isInitialized else { return }
				let sharing = core.transfers.filter { $0.direction == .send && $0.status == .sharing }
				for transfer in sharing {
					Task { await self.refresh(transferId: transfer.transferId) }
				}
			}
			.store(in: &cancellables)

		// Recompute notifications when any input changes.
		Publishers.CombineLatest4(
			preferences.$preferences,
			visibility.$isForeground,
			$state,
			notifications.$permission
		)
		.sink { [weak self] preferences, foreground, approvalState, permission in
			guard let self else { return }
			Task {
				await self.synchronizeNotifications(
					enabled: preferences.notificationsEnabled,
					foreground: foreground,
					pending: approvalState.pending,
					permission: permission
				)
			}
		}
		.store(in: &cancellables)
	}

	func accept(_ requestId: String) { respond(requestId, accepted: true) }
	func refuse(_ requestId: String) { respond(requestId, accepted: false) }

	private func respond(_ requestId: String, accepted: Bool) {
		guard !state.respondingIds.contains(requestId) else { return }
		state.respondingIds.insert(requestId)
		Task {
			let request = state.pending.first { $0.id == requestId }
			let result = await repository.respondReceiverRequest(
				requestId: requestId,
				accepted: accepted,
				reason: accepted ? nil : "sender-refused"
			)
			state.respondingIds.remove(requestId)
			switch result {
			case .success:
				if let request { await refresh(transferId: request.transferId) }
			case .failure(let error):
				messages.error(error)
			}
		}
	}

	private func refresh(transferId: UInt64) async {
		let result = await repository.receiverRequests(transferId: transferId)
		switch result {
		case .success(let requests):
			let refreshed = requests
				.filter { $0.status == .requested }
				.map { $0.toPending() }
			let refreshedIds = Set(refreshed.map { $0.id })
			let removed = Set(state.pending.filter { $0.transferId == transferId }.map { $0.id })
				.subtracting(refreshedIds)
			for id in removed {
				notifications.cancel(id: Self.notificationId(id))
				publishedNotificationIds.remove(id)
			}
			var pending = state.pending.filter { $0.transferId != transferId } + refreshed
			// distinctBy id, sorted by requestedAt
			var seen = Set<String>()
			pending = pending.filter { seen.insert($0.id).inserted }
				.sorted { $0.requestedAt < $1.requestedAt }
			state.pending = pending
		case .failure(let error):
			messages.error(error)
		}
	}

	private func synchronizeNotifications(
		enabled: Bool,
		foreground: Bool,
		pending: [PendingApproval],
		permission: NotificationPermission
	) async {
		if foreground || !enabled || permission != .granted {
			notifications.cancelAll()
			return
		}
		for request in pending where !publishedNotificationIds.contains(request.id) {
			let receiver = request.receiverName
				?? request.receiverDeviceName
				?? String(localized: "approval_nearby_device")
			let title = String(localized: "approval_connection_request")
			let body = String(
				format: String(localized: "approval_request_body"),
				receiver, request.transferName
			)
			let result = await notifications.publish(
				LocalNotification(id: Self.notificationId(request.id), title: title, body: body)
			)
			switch result {
			case .success: publishedNotificationIds.insert(request.id)
			case .failure(let error): messages.error(error)
			}
		}
	}

	private static func notificationId(_ requestId: String) -> String { "approval-\(requestId)" }
}

private extension ReceiverRequestModel {
	func toPending() -> PendingApproval {
		PendingApproval(
			id: id, transferId: transferId, transferName: transferName,
			receiverName: receiverName, receiverDeviceName: receiverDeviceName,
			remoteEndpointId: remoteEndpointId, requestedAt: requestedAt
		)
	}
}
