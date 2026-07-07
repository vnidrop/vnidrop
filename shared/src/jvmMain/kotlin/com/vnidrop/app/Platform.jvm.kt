package com.vnidrop.app

import java.net.NetworkInterface

class JVMPlatform : Platform {
	override val name: String = "Java ${System.getProperty("java.version")}"
	override val defaultCoreDataDir: String =
		System.getProperty("user.home") + "/.vnidrop"
	override val defaultReceiveDir: String =
		System.getProperty("user.home") + "/Downloads"
	override val deviceInfo: DeviceInfo = DeviceInfo(
		deviceName = System.getenv("COMPUTERNAME")
			?: System.getenv("HOSTNAME")
			?: System.getProperty("user.name"),
		deviceModel = listOfNotNull(
			System.getProperty("os.arch"),
			System.getProperty("java.vm.name"),
		).joinToString(" | ").ifBlank { null },
		operatingSystem = listOfNotNull(
			System.getProperty("os.name"),
			System.getProperty("os.version"),
		).joinToString(" ").ifBlank { name },
		network = activeNetworkSummary(),
		batteryLevel = null,
	)
}

actual fun getPlatform(): Platform = JVMPlatform()

private fun activeNetworkSummary(): String? =
	runCatching {
		NetworkInterface.getNetworkInterfaces()
			.asSequence()
			.filter { it.isUp && !it.isLoopback }
			.map { it.displayName }
			.firstOrNull()
	}.getOrNull()
