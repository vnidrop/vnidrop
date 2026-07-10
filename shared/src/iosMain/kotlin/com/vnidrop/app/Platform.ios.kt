package com.vnidrop.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vnidrop.app.core.rememberFileSystemService
import com.vnidrop.app.notifications.IosLocalNotificationService
import platform.Foundation.NSBundle
import platform.Foundation.NSTemporaryDirectory
import platform.UIKit.UIDevice

@Composable
fun rememberIosAppDependencies(): AppDependencies {
	val fileSystemService = rememberFileSystemService()
	return remember(fileSystemService) {
		val device = UIDevice.currentDevice
		AppDependencies(
			environment = PlatformEnvironment(
				name = device.systemName() + " " + device.systemVersion,
				appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "0.1.0",
				defaultCoreDataDir = NSTemporaryDirectory() + "vnidrop",
				defaultUsername = device.name.takeIf(String::isNotBlank) ?: "Receiver",
			),
			deviceInfoProvider = IosDeviceInfoProvider(device),
			fileSystemService = fileSystemService,
			localNotificationService = IosLocalNotificationService(),
		)
	}
}

private class IosDeviceInfoProvider(
	private val device: UIDevice,
) : DeviceInfoProvider {
	override suspend fun load(): DeviceInfo = DeviceInfo(
		deviceName = device.name,
		deviceModel = device.model,
		operatingSystem = device.systemName() + " " + device.systemVersion,
		network = null,
		batteryLevel = runCatching {
			val wasMonitoring = device.batteryMonitoringEnabled
			try {
				device.batteryMonitoringEnabled = true
				device.batteryLevel.takeIf { it >= 0.0 }?.let { "${(it * 100).toInt()}%" }
			} finally {
				device.batteryMonitoringEnabled = wasMonitoring
			}
		}.getOrNull(),
	)
}
