package com.vnidrop.app

import platform.UIKit.UIDevice
import platform.Foundation.NSTemporaryDirectory

class IOSPlatform : Platform {
	override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
	override val defaultCoreDataDir: String = NSTemporaryDirectory() + "vnidrop"
	override val defaultReceiveDir: String = NSTemporaryDirectory() + "vnidrop-receive"
}

actual fun getPlatform(): Platform = IOSPlatform()
