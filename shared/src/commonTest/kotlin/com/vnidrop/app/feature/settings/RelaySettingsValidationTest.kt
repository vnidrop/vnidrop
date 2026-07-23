package com.vnidrop.app.feature.settings

import com.vnidrop.app.core.RelayMode
import com.vnidrop.app.core.RelaySettings
import kotlin.test.Test
import kotlin.test.assertEquals

class RelaySettingsValidationTest {
	@Test
	fun customRelayUrlsAreNormalized() {
		val result = validateRelaySettings(
			mode = RelayMode.Custom,
			urlsText = " HTTPS://Relay.Example.com/ \nhttps://[2001:DB8::1]:443",
		)

		assertEquals(
			RelaySettings(
				RelayMode.Custom,
				listOf("https://relay.example.com", "https://[2001:db8::1]"),
			),
			result.settings,
		)
		assertEquals(null, result.error)
	}

	@Test
	fun customRelayUrlsRequireHttpsAndRootPath() {
		assertEquals(
			RelaySettingsInputError.HttpsRequired(1),
			validateRelaySettings(RelayMode.Custom, "http://relay.example.com").error,
		)
		assertEquals(
			RelaySettingsInputError.InvalidUrl(1),
			validateRelaySettings(RelayMode.Custom, "https://relay.example.com/custom").error,
		)
	}

	@Test
	fun duplicateNormalizedRelayUrlsAreRejected() {
		val result = validateRelaySettings(
			RelayMode.Custom,
			"https://relay.example.com\nHTTPS://RELAY.EXAMPLE.COM:443/",
		)

		assertEquals(RelaySettingsInputError.DuplicateUrl(2), result.error)
	}

	@Test
	fun structurallyValidIpv6RelayUrlsAreAccepted() {
		val result = validateRelaySettings(
			RelayMode.Custom,
			"https://[::1]:443\nhttps://[2001:db8::1]\nhttps://[::ffff:192.0.2.1]",
		)

		assertEquals(
			RelaySettings(
				RelayMode.Custom,
				listOf(
					"https://[::1]",
					"https://[2001:db8::1]",
					"https://[::ffff:192.0.2.1]",
				),
			),
			result.settings,
		)
	}

	@Test
	fun malformedIpv6RelayUrlsAreRejected() {
		listOf(
			"https://[::::]",
			"https://[1::2::3]",
			"https://[1:2:3:4:5:6:7]",
			"https://[1:2:3:4:5:6:7:8::]",
		).forEach { url ->
			assertEquals(
				RelaySettingsInputError.InvalidUrl(1),
				validateRelaySettings(RelayMode.Custom, url).error,
				url,
			)
		}
	}
}
