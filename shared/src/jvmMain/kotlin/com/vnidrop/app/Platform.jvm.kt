package com.vnidrop.app

class JVMPlatform : Platform {
	override val name: String = "Java ${System.getProperty("java.version")}"
	override val defaultCoreDataDir: String =
		System.getProperty("user.home") + "/.vnidrop"
	override val defaultReceiveDir: String =
		System.getProperty("user.home") + "/Downloads"
}

actual fun getPlatform(): Platform = JVMPlatform()
