package com.vnidrop.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.compose.resources.StringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.error_invalid_ticket
import vnidrop.shared.generated.resources.nav_receive
import vnidrop.shared.generated.resources.nav_send
import vnidrop.shared.generated.resources.nav_settings
import vnidrop.shared.generated.resources.receive_title
import vnidrop.shared.generated.resources.send_title
import vnidrop.shared.generated.resources.settings_title

class I18nResourceTest {
	@Test
	fun primaryNavigationScreenAndErrorKeysExist() {
		val resources: List<StringResource> = listOf(
			Res.string.nav_send,
			Res.string.nav_receive,
			Res.string.nav_settings,
			Res.string.send_title,
			Res.string.receive_title,
			Res.string.settings_title,
			Res.string.error_invalid_ticket,
		)

		assertEquals(7, resources.size)
	}
}
