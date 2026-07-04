package com.vnidrop.app.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
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

data class NavigationItem(
	val destination: AppDestination,
	val label: StringResource,
	val icon: ImageVector,
)

// The route list is intentionally tiny for this phase. Activity, receiver
// requests, and diagnostics remain available inside screens instead of being
// promoted to top-level navigation.
val primaryNavigationItems = listOf(
	NavigationItem(AppDestination.Send, Res.string.nav_send, VniDropIcons.Send),
	NavigationItem(AppDestination.Receive, Res.string.nav_receive, VniDropIcons.Receive),
	NavigationItem(AppDestination.Settings, Res.string.nav_settings, VniDropIcons.Settings),
)
