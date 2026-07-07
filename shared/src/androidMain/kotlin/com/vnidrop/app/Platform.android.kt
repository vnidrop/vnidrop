package com.vnidrop.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import java.net.NetworkInterface

class AndroidPlatform : Platform {
	override val name: String = "Android ${Build.VERSION.SDK_INT}"
	override val defaultCoreDataDir: String =
		AndroidPlatformContextHolder.context?.filesDir?.resolve("vnidrop")?.absolutePath
			?: (System.getProperty("java.io.tmpdir") ?: "/data/local/tmp/vnidrop")
	override val defaultReceiveDir: String =
		AndroidPlatformContextHolder.context
			?.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
			?.absolutePath
			?: (System.getProperty("java.io.tmpdir") ?: "/data/local/tmp/vnidrop-receive")
	override val deviceInfo: DeviceInfo = DeviceInfo(
		deviceName = Build.DEVICE,
		deviceModel = listOf(Build.MANUFACTURER, Build.MODEL)
			.filter { it.isNotBlank() }
			.joinToString(" ")
			.ifBlank { null },
		operatingSystem = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
		network = activeNetworkSummary(),
		batteryLevel = batteryLevel(),
	)
}

actual fun getPlatform(): Platform = AndroidPlatform()

fun attachAndroidPlatformContext(context: Context) {
	AndroidPlatformContextHolder.context = context.applicationContext
}

private object AndroidPlatformContextHolder {
	var context: Context? = null
}

private fun activeNetworkSummary(): String? =
	runCatching {
		val context = AndroidPlatformContextHolder.context ?: return@runCatching networkInterfaceName()
		val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
			?: return@runCatching networkInterfaceName()
		val activeNetwork = manager.activeNetwork ?: return@runCatching networkInterfaceName()
		val capabilities = manager.getNetworkCapabilities(activeNetwork) ?: return@runCatching networkInterfaceName()

		when {
			capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
			capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
			capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
			capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
			else -> networkInterfaceName()
		}
	}.getOrNull()

private fun batteryLevel(): String? =
	runCatching {
		val context = AndroidPlatformContextHolder.context ?: return@runCatching null
		val manager = context.getSystemService(BatteryManager::class.java)
		val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
		level.takeIf { it >= 0 }?.let { "$it%" }
	}.getOrNull()

private fun networkInterfaceName(): String? =
	NetworkInterface.getNetworkInterfaces()
		.asSequence()
		.filter { it.isUp && !it.isLoopback }
		.map { it.displayName }
		.firstOrNull()
