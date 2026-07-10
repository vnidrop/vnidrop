package com.vnidrop.app.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

class IosLocalNotificationService : LocalNotificationService {
	private val center = UNUserNotificationCenter.currentNotificationCenter()
	private val _permission = MutableStateFlow(NotificationPermission.NotDetermined)
	override val permission: StateFlow<NotificationPermission> = _permission.asStateFlow()

	override suspend fun refreshPermission(): NotificationPermission = suspendCancellableCoroutine { continuation ->
		center.getNotificationSettingsWithCompletionHandler { settings ->
			val mapped = when (settings?.authorizationStatus) {
				UNAuthorizationStatusAuthorized,
				UNAuthorizationStatusProvisional,
				UNAuthorizationStatusEphemeral -> NotificationPermission.Granted
				UNAuthorizationStatusDenied -> NotificationPermission.Denied
				UNAuthorizationStatusNotDetermined -> NotificationPermission.NotDetermined
				else -> NotificationPermission.Unsupported
			}
			_permission.value = mapped
			if (continuation.isActive) continuation.resume(mapped)
		}
	}

	override suspend fun requestPermission(): NotificationPermission {
		val current = refreshPermission()
		if (current != NotificationPermission.NotDetermined) return current
		return suspendCancellableCoroutine { continuation ->
			center.requestAuthorizationWithOptions(
				options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
				completionHandler = { granted, _ ->
					val result = if (granted) NotificationPermission.Granted else NotificationPermission.Denied
					_permission.value = result
					if (continuation.isActive) continuation.resume(result)
				},
			)
		}
	}

	override suspend fun openSettings(): Result<Unit> {
		val url = NSURL.URLWithString(UIApplicationOpenNotificationSettingsURLString)
			?: return Result.failure(IllegalStateException("Notification settings URL is unavailable"))
		val opened = suspendCancellableCoroutine { continuation ->
			UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>()) { success ->
				if (continuation.isActive) continuation.resume(success)
			}
		}
		return if (opened) Result.success(Unit) else Result.failure(IllegalStateException("Could not open notification settings"))
	}

	override suspend fun publish(notification: LocalNotification): Result<Unit> = runCatching {
		check(refreshPermission() == NotificationPermission.Granted) { "Notification permission is not granted" }
		val content = UNMutableNotificationContent().apply {
			setTitle(notification.title)
			setBody(notification.body)
			setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
		}
		val request = UNNotificationRequest.requestWithIdentifier(notification.id, content, null)
		suspendCancellableCoroutine { continuation ->
			center.addNotificationRequest(request) { error ->
				if (!continuation.isActive) return@addNotificationRequest
				if (error == null) {
					continuation.resume(Unit)
				} else {
					continuation.resumeWith(
						Result.failure(IllegalStateException(error.localizedDescription)),
					)
				}
			}
		}
	}

	override suspend fun cancel(id: String) {
		center.removePendingNotificationRequestsWithIdentifiers(listOf(id))
		center.removeDeliveredNotificationsWithIdentifiers(listOf(id))
	}

	override suspend fun cancelAll() {
		center.removeAllPendingNotificationRequests()
		center.removeAllDeliveredNotifications()
	}
}
