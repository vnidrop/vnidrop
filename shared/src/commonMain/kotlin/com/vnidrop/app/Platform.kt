package com.vnidrop.app

interface Platform {
	val name: String
	val defaultCoreDataDir: String
	val defaultReceiveDir: String
	val deviceInfo: DeviceInfo
}

data class DeviceInfo(
	val deviceName: String?,
	val deviceModel: String?,
	val operatingSystem: String,
	val network: String?,
	val batteryLevel: String?,
)

expect fun getPlatform(): Platform
