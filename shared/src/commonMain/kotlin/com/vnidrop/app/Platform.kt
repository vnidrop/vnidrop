package com.vnidrop.app

interface Platform {
	val name: String
	val defaultCoreDataDir: String
	val defaultReceiveDir: String
}

expect fun getPlatform(): Platform
