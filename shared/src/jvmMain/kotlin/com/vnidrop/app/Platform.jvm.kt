package com.vnidrop.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vnidrop.app.core.rememberFileSystemService
import com.vnidrop.app.notifications.JvmLocalNotificationService
import com.vnidrop.app.feature.receive.ExternalInvitationController
import java.net.NetworkInterface

@Composable
fun rememberJvmAppDependencies(externalInvitations: ExternalInvitationController): AppDependencies {
	val fileSystemService = rememberFileSystemService()
	return remember(fileSystemService) {
		AppDependencies(
			environment = PlatformEnvironment(
				name = "Java ${System.getProperty("java.version")}",
				appVersion = AppDependencies::class.java.`package`.implementationVersion ?: "0.1.0",
				defaultCoreDataDir = System.getProperty("user.home") + "/.vnidrop",
				defaultUsername = System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME") ?: System.getProperty("user.name") ?: "Receiver",
			),
			deviceInfoProvider = JvmDeviceInfoProvider,
			fileSystemService = fileSystemService,
			localNotificationService = JvmLocalNotificationService(),
			externalInvitations = externalInvitations,
		)
	}
}

private object JvmDeviceInfoProvider : DeviceInfoProvider {
	override suspend fun load(): DeviceInfo = DeviceInfo(
		deviceName = System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME") ?: System.getProperty("user.name"),
		deviceModel = listOfNotNull(System.getProperty("os.arch"), System.getProperty("java.vm.name"))
			.joinToString(" | ").ifBlank { null },
		operatingSystem = listOfNotNull(System.getProperty("os.name"), System.getProperty("os.version"))
			.joinToString(" ").ifBlank { "Java ${System.getProperty("java.version")}" },
		network = runCatching {
			NetworkInterface.getNetworkInterfaces().asSequence()
				.filter { it.isUp && !it.isLoopback }
				.map { it.displayName }
				.firstOrNull()
		}.getOrNull(),
		batteryLevel = null,
	)
}
