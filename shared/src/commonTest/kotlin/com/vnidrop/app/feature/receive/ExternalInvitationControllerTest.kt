package com.vnidrop.app.feature.receive

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalInvitationControllerTest {
	@Test
	fun buffersColdLaunchInvitationsAndDeliversThemInOrder() = runTest {
		val controller = ExternalInvitationController()
		controller.openInvitation("first")
		controller.openInvitation("second")

		val received = async { controller.invitations.take(2).toList() }.await()

		assertEquals(listOf("first", "second"), received.map { it.getOrThrow() })
	}

	@Test
	fun rejectsEmptyAndOversizedDocumentsBeforeInspection() = runTest {
		val controller = ExternalInvitationController()
		controller.openInvitation(" ")
		controller.openInvitation("x".repeat(MaxVniDropInvitationBytes + 1))

		val received = async { controller.invitations.take(2).toList() }.await()

		assertTrue(received.all { it.isFailure })
	}
}
