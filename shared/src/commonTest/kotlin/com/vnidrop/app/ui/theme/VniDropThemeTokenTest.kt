package com.vnidrop.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class VniDropThemeTokenTest {
	@Test
	fun lightPaletteUsesTauriPurpleBrandAndSurfaceTokens() {
		assertEquals(hslColorForTest(271f, 91f, 65f), VniDropThemeTokens.light.brandLink)
		assertEquals(hslColorForTest(0f, 0f, 97.3f), VniDropThemeTokens.light.backgroundDashCanvas)
		assertEquals(hslColorForTest(0f, 0f, 87.5f), VniDropThemeTokens.light.borderDefault)
	}

	@Test
	fun darkPaletteUsesTauriPurpleBrandAndSurfaceTokens() {
		assertEquals(hslColorForTest(270f, 95f, 75f), VniDropThemeTokens.dark.brandLink)
		assertEquals(hslColorForTest(0f, 0f, 7.1f), VniDropThemeTokens.dark.backgroundDashCanvas)
		assertEquals(hslColorForTest(0f, 0f, 18f), VniDropThemeTokens.dark.borderDefault)
	}
}
