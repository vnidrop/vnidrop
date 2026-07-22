package com.vnidrop.app.ui.navigation

import com.vnidrop.app.ui.icons.AppIcon
import org.jetbrains.compose.resources.StringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.nav_receive
import vnidrop.shared.generated.resources.nav_send
import vnidrop.shared.generated.resources.nav_settings

enum class AppDestination {
	Send,
	Receive,
	Settings,
}

internal data class NavigationItem(
	val destination: AppDestination,
	val label: StringResource,
	val icon: AppIcon,
)

// The route list is intentionally tiny for this phase. Activity, receiver
// requests, and diagnostics remain available inside screens instead of being
// promoted to top-level navigation.
internal val primaryNavigationItems = listOf(
	NavigationItem(AppDestination.Send, Res.string.nav_send, AppIcon.Send),
	NavigationItem(AppDestination.Receive, Res.string.nav_receive, AppIcon.Download),
	NavigationItem(AppDestination.Settings, Res.string.nav_settings, AppIcon.Settings),
)
