package com.vnidrop.app.ui.feedback

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiMessageControllerTest {
	@Test
	fun messagesBufferAndPreserveFifoOrder() = runTest {
		val controller = UiMessageController()
		assertTrue(controller.tryShow(UiMessage(UiText.Dynamic("first"))))
		assertTrue(controller.tryShow(UiMessage(UiText.Dynamic("second"))))
		val collected = async { controller.messages.take(2).toList() }.await()
		assertEquals(listOf("first", "second"), collected.map { (it.text as UiText.Dynamic).value })
	}
}
