package com.vnidrop.app.feature.send

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TransferShareActionsTest {
	@Test
	fun invitation_names_are_safe_and_have_the_vnd_extension() {
		val name = invitationFileName("../Summer/photos: 2026")
		assertEquals("_Summer_photos_ 2026.vnd", name)
		assertFalse('/' in name)
	}

	@Test
	fun an_existing_extension_is_not_duplicated() {
		assertEquals("Transfer.VND", invitationFileName("Transfer.VND"))
	}
}
