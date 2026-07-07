package com.vnidrop.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.compose.resources.StringResource
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.about_title
import vnidrop.shared.generated.resources.appearance_dark_mode
import vnidrop.shared.generated.resources.appearance_light_mode
import vnidrop.shared.generated.resources.appearance_system_mode
import vnidrop.shared.generated.resources.battery_level_title
import vnidrop.shared.generated.resources.device_model_title
import vnidrop.shared.generated.resources.device_name_title
import vnidrop.shared.generated.resources.error_invalid_ticket
import vnidrop.shared.generated.resources.nav_receive
import vnidrop.shared.generated.resources.nav_send
import vnidrop.shared.generated.resources.nav_settings
import vnidrop.shared.generated.resources.network_title
import vnidrop.shared.generated.resources.os_version_title
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
			Res.string.appearance_system_mode,
			Res.string.appearance_dark_mode,
			Res.string.appearance_light_mode,
			Res.string.about_title,
			Res.string.device_name_title,
			Res.string.device_model_title,
			Res.string.os_version_title,
			Res.string.network_title,
			Res.string.battery_level_title,
		)

		assertEquals(16, resources.size)
	}
}
