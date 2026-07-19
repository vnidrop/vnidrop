import Foundation
import Combine
import UserNotifications

/// Notification permission state, ported from `NotificationPermission`.
enum NotificationPermission {
	case notDetermined
	case granted
	case denied
	case unsupported
}

struct LocalNotification {
	let id: String
	let title: String
	let body: String
}

/// Local notification service, ported from `LocalNotificationService.kt` /
/// `.ios.kt`, backed by `UNUserNotificationCenter`.
@MainActor
final class LocalNotificationService: ObservableObject {
	@Published private(set) var permission: NotificationPermission = .notDetermined

	private let center = UNUserNotificationCenter.current()

	func refreshPermission() async -> NotificationPermission {
		let settings = await center.notificationSettings()
		let mapped = Self.map(settings.authorizationStatus)
		permission = mapped
		return mapped
	}

	func requestPermission() async -> NotificationPermission {
		do {
			_ = try await center.requestAuthorization(options: [.alert, .sound, .badge])
		} catch {
			AppLogger.error("notifications", "authorization request failed", error)
		}
		return await refreshPermission()
	}

	func openSettings() async -> Result<Void, Error> {
		#if os(iOS)
		guard let url = URL(string: UIApplication.openSettingsURLString) else {
			return .failure(NotificationError.settingsUnavailable)
		}
		let opened = await UIApplication.shared.open(url)
		return opened ? .success(()) : .failure(NotificationError.settingsUnavailable)
		#else
		guard let url = URL(string: "x-apple.systempreferences:com.apple.preference.notifications") else {
			return .failure(NotificationError.settingsUnavailable)
		}
		NSWorkspace.shared.open(url)
		return .success(())
		#endif
	}

	@discardableResult
	func publish(_ notification: LocalNotification) async -> Result<Void, Error> {
		let content = UNMutableNotificationContent()
		content.title = notification.title
		content.body = notification.body
		content.sound = .default
		let request = UNNotificationRequest(identifier: notification.id, content: content, trigger: nil)
		do {
			try await center.add(request)
			return .success(())
		} catch {
			return .failure(error)
		}
	}

	func cancel(id: String) {
		center.removePendingNotificationRequests(withIdentifiers: [id])
		center.removeDeliveredNotifications(withIdentifiers: [id])
	}

	func cancelAll() {
		center.removeAllPendingNotificationRequests()
		center.removeAllDeliveredNotifications()
	}

	private static func map(_ status: UNAuthorizationStatus) -> NotificationPermission {
		switch status {
		case .authorized, .provisional, .ephemeral: return .granted
		case .denied: return .denied
		case .notDetermined: return .notDetermined
		@unknown default: return .notDetermined
		}
	}
}

private enum NotificationError: LocalizedError {
	case settingsUnavailable
	var errorDescription: String? { "Could not open notification settings" }
}

#if os(iOS)
import UIKit
#else
import AppKit
#endif
