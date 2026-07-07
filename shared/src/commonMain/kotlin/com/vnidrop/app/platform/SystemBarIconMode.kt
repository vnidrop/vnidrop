package com.vnidrop.app.platform

enum class SystemBarIconMode {
	LightIcons,
	DarkIcons,
}

fun systemBarIconModeForTheme(isDarkTheme: Boolean): SystemBarIconMode =
	if (isDarkTheme) SystemBarIconMode.LightIcons else SystemBarIconMode.DarkIcons
