package com.vnidrop.app

import platform.Foundation.NSTemporaryDirectory
import platform.UIKit.UIDevice

class IOSPlatform : Platform {
	private val device: UIDevice = UIDevice.currentDevice

	override val name: String = device.systemName() + " " + device.systemVersion
	override val defaultCoreDataDir: String = NSTemporaryDirectory() + "vnidrop"
	override val defaultReceiveDir: String = NSTemporaryDirectory() + "vnidrop-receive"
	override val deviceInfo: DeviceInfo = DeviceInfo(
		deviceName = device.name,
		deviceModel = device.model,
		operatingSystem = device.systemName() + " " + device.systemVersion,
		network = null,
		batteryLevel = batteryLevel(device),
	)
}

actual fun getPlatform(): Platform = IOSPlatform()

private fun batteryLevel(device: UIDevice): String? =
	runCatching {
		device.batteryMonitoringEnabled = true
		val level = device.batteryLevel
		if (level >= 0.0) "${(level * 100).toInt()}%" else null
	}.getOrNull()
