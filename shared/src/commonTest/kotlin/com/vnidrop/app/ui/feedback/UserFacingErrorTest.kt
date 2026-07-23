package com.vnidrop.app.ui.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uniffi.vnidrop.VnidropException
import vnidrop.shared.generated.resources.Res
import vnidrop.shared.generated.resources.error_filesystem
import vnidrop.shared.generated.resources.error_generic
import vnidrop.shared.generated.resources.error_initialization
import vnidrop.shared.generated.resources.error_invalid_ticket
import vnidrop.shared.generated.resources.error_invitation_empty
import vnidrop.shared.generated.resources.error_missing_native_library
import vnidrop.shared.generated.resources.error_permission
import vnidrop.shared.generated.resources.error_relay_direct_failed
import vnidrop.shared.generated.resources.error_repository
import vnidrop.shared.generated.resources.error_selection_failed
import vnidrop.shared.generated.resources.error_socket_bind
import vnidrop.shared.generated.resources.error_camera
import vnidrop.shared.generated.resources.error_nfc
import vnidrop.shared.generated.resources.error_share_empty
import vnidrop.shared.generated.resources.error_starting_up
import vnidrop.shared.generated.resources.error_transfer

class UserFacingErrorTest {
	@Test
	fun mapsVnidropExceptionVariantsToStableCopy() {
		assertEquals(
			UiText.Resource(Res.string.error_invalid_ticket),
			VnidropException.Ticket("decode failed: bad base32").toUiText(),
		)
		assertEquals(
			UiText.Resource(Res.string.error_permission),
			VnidropException.Permission("receiver refused").toUiText(),
		)
		assertEquals(
			UiText.Resource(Res.string.error_filesystem),
			VnidropException.Filesystem("permission denied opening path").toUiText(),
		)
		assertEquals(
			UiText.Resource(Res.string.error_transfer),
			VnidropException.Transfer("connection reset").toUiText(),
		)
		// Couples to the core's connect-failure annotation phrase; keep in sync.
		assertEquals(
			UiText.Resource(Res.string.error_relay_direct_failed),
			VnidropException.Transfer("could not connect directly with relays disabled").toUiText(),
		)
		assertEquals(
			UiText.Resource(Res.string.error_repository),
			VnidropException.Repository("database locked").toUiText(),
		)
		assertEquals(
			UiText.Resource(Res.string.error_initialization),
			VnidropException.Initialization("endpoint bootstrap failed").toUiText(),
		)
		assertEquals(
			UiText.Resource(Res.string.error_generic),
			VnidropException.Internal("unexpected").toUiText(),
		)
	}

	@Test
	fun neverSurfacesRawReasonPrefixFromUniffi() {
		val text = VnidropException.Transfer("blob fetch failed").toUiText()
		assertTrue(text is UiText.Resource)
		assertFalse(text.toString().contains("reason="))
	}

	@Test
	fun mapsCommonPlatformReasons() {
		assertEquals(
			UiText.Resource(Res.string.error_permission),
			IllegalStateException("sender refused").toUiText(),
		)
		assertEquals(
			UiText.Resource(Res.string.error_invalid_ticket),
			IllegalArgumentException("invalid ticket").toUiText(),
		)
		assertEquals(
			UiText.Resource(Res.string.error_invitation_empty),
			IllegalArgumentException("The invitation is empty").toUiText(),
		)
		assertEquals(
			UiText.Resource(Res.string.error_selection_failed),
			IllegalStateException("Could not open the selected files").toUiText(),
		)
		assertEquals(
			UiText.Resource(Res.string.error_socket_bind),
			IllegalStateException("socket bind failed on port").toUiText(),
		)
		assertEquals(
			UiText.Resource(Res.string.error_missing_native_library),
			IllegalStateException("native library missing from this build").toUiText(),
		)
		assertEquals(
			UiText.Resource(Res.string.error_starting_up),
			IllegalStateException("VniDrop is still starting up").toUiText(),
		)
		assertEquals(
			UiText.Resource(Res.string.error_nfc),
			IllegalStateException("This NFC tag is read-only").toUiText(),
		)
		assertEquals(
			UiText.Resource(Res.string.error_camera),
			IllegalStateException("Camera access is required to scan QR codes").toUiText(),
		)
		assertEquals(
			UiText.Resource(Res.string.error_share_empty),
			IllegalStateException("Select at least one file to share").toUiText(),
		)
	}

	@Test
	fun unknownErrorsUseGenericCopy() {
		assertEquals(
			UiText.Resource(Res.string.error_generic),
			RuntimeException("opaque backend code 0xdead").toUiText(),
		)
	}

	@Test
	fun detectsUserCancellations() {
		assertTrue(IllegalStateException("QR scanning was cancelled").isUserCancellation())
		assertTrue(IllegalStateException("NFC writing was cancelled").isUserCancellation())
		assertTrue(VnidropException.Transfer("transfer cancelled by user").isUserCancellation())
		assertFalse(IllegalStateException("sender refused").isUserCancellation())
	}
}
