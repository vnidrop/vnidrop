package com.vnidrop.app.notifications

import kotlinx.coroutines.flow.StateFlow

enum class NotificationPermission {
	NotDetermined,
	Granted,
	Denied,
	Unsupported,
}

data class LocalNotification(
	val id: String,
	val title: String,
	val body: String,
)

interface LocalNotificationService {
	val permission: StateFlow<NotificationPermission>

	suspend fun refreshPermission(): NotificationPermission
	suspend fun requestPermission(): NotificationPermission
	suspend fun openSettings(): Result<Unit>
	suspend fun publish(notification: LocalNotification): Result<Unit>
	suspend fun cancel(id: String)
	suspend fun cancelAll()
}
