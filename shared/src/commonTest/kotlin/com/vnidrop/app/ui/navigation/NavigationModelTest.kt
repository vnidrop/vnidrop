package com.vnidrop.app.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationModelTest {
	@Test
	fun primaryNavigationContainsOnlyProductDestinations() {
		assertEquals(
			listOf(AppDestination.Send, AppDestination.Receive, AppDestination.Settings),
			primaryNavigationItems.map { it.destination },
		)
		assertEquals(3, primaryNavigationItems.map { it.label }.distinct().size)
	}
}
