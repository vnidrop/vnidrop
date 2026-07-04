package com.vnidrop.app.logging

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLoggerTest {
	@Test
	fun rotationPolicyRotatesOnlyWhenActiveFileWouldExceedLimit() {
		val policy = LogRotationPolicy(maxBytes = 10, maxFiles = 2)

		assertFalse(policy.shouldRotate(currentBytes = 0, incomingBytes = 20))
		assertFalse(policy.shouldRotate(currentBytes = 4, incomingBytes = 6))
		assertTrue(policy.shouldRotate(currentBytes = 5, incomingBytes = 6))
	}
}
