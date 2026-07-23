package com.vnidrop.app.ui.icons

import com.vnidrop.app.UiPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformIconsTest {
	@Test
	fun iconFamiliesFollowTheRuntimePlatform() {
		assertEquals(IconFamily.Material, iconFamilyFor(UiPlatform.Android))
		assertEquals(IconFamily.Fluent, iconFamilyFor(UiPlatform.Windows))
		assertEquals(IconFamily.Lucide, iconFamilyFor(UiPlatform.Linux))
		assertEquals(IconFamily.Lucide, iconFamilyFor(UiPlatform.Desktop))
	}

	@Test
	fun everySemanticIconResolvesToItsPlatformResource() {
		AppIcon.entries.forEach { icon ->
			assertEquals(icon.material, icon.resourceFor(UiPlatform.Android), icon.name)
			assertEquals(icon.fluent, icon.resourceFor(UiPlatform.Windows), icon.name)
			assertEquals(icon.lucide, icon.resourceFor(UiPlatform.Linux), icon.name)
		}
	}
}
