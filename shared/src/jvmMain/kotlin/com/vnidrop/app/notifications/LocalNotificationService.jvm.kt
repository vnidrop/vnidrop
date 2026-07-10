package com.vnidrop.app.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.Color
import java.awt.Frame
import java.awt.Graphics2D
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

class JvmLocalNotificationService : LocalNotificationService {
	private val _permission = MutableStateFlow(
		if (SystemTray.isSupported()) NotificationPermission.Granted else NotificationPermission.Unsupported,
	)
	override val permission: StateFlow<NotificationPermission> = _permission.asStateFlow()
	private var trayIcon: TrayIcon? = null

	override suspend fun refreshPermission(): NotificationPermission = permission.value
	override suspend fun requestPermission(): NotificationPermission = permission.value
	override suspend fun openSettings(): Result<Unit> = Result.failure(
		UnsupportedOperationException("Notification settings are not available on this platform"),
	)

	override suspend fun publish(notification: LocalNotification): Result<Unit> = runCatching {
		check(permission.value == NotificationPermission.Granted) { "System notifications are not supported" }
		val icon = trayIcon ?: createTrayIcon().also {
			SystemTray.getSystemTray().add(it)
			trayIcon = it
		}
		icon.displayMessage(notification.title, notification.body, TrayIcon.MessageType.INFO)
	}

	override suspend fun cancel(id: String) = Unit

	override suspend fun cancelAll() {
		trayIcon?.let { SystemTray.getSystemTray().remove(it) }
		trayIcon = null
	}

	private fun createTrayIcon(): TrayIcon {
		val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
		val graphics = image.graphics as Graphics2D
		try {
			graphics.color = Color(83, 82, 237)
			graphics.fillOval(2, 2, 28, 28)
			graphics.color = Color.WHITE
			graphics.fillRect(14, 8, 4, 16)
		} finally {
			graphics.dispose()
		}
		return TrayIcon(image, "VniDrop").apply {
			isImageAutoSize = true
			addActionListener {
				Frame.getFrames().firstOrNull { it.isDisplayable }?.let { frame ->
					frame.isVisible = true
					frame.toFront()
				}
			}
		}
	}
}
