package com.vnidrop.app

import com.vnidrop.app.core.FileSystemService
import com.vnidrop.app.notifications.LocalNotificationService
import com.vnidrop.app.feature.receive.ExternalInvitationController

data class PlatformEnvironment(
	val name: String,
	val appVersion: String,
	val defaultCoreDataDir: String,
	val defaultUsername: String = "Receiver",
)

data class DeviceInfo(
	val deviceName: String?,
	val deviceModel: String?,
	val operatingSystem: String,
	val network: String?,
	val batteryLevel: String?,
)

fun interface DeviceInfoProvider {
	suspend fun load(): DeviceInfo
}

data class AppDependencies(
	val environment: PlatformEnvironment,
	val deviceInfoProvider: DeviceInfoProvider,
	val fileSystemService: FileSystemService,
	val localNotificationService: LocalNotificationService,
	val externalInvitations: ExternalInvitationController,
)
