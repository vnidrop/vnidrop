package com.vnidrop.app.feature.settings

import com.vnidrop.app.core.RelayMode
import com.vnidrop.app.core.RelaySettings
import kotlin.test.Test
import kotlin.test.assertEquals

class RelaySettingsValidationTest {
	@Test
	fun customFallbackUsesTheSameValidatedRelayList() {
		val result = validateRelaySettings(
			mode = RelayMode.CustomWithDirectFallback,
			urlsText = "https://relay.example.com/",
		)

		assertEquals(
			RelaySettings(
				RelayMode.CustomWithDirectFallback,
				listOf("https://relay.example.com"),
			),
			result.settings,
		)
	}

	@Test
	fun modesWithoutCustomRelaysRetainTheLastRelayList() {
		val retained = listOf("https://relay.example.com")

		assertEquals(
			RelaySettings(RelayMode.Automatic, retained),
			validateRelaySettings(RelayMode.Automatic, "invalid", retained).settings,
		)
		assertEquals(
			RelaySettings(RelayMode.LocalOnly, retained),
			validateRelaySettings(RelayMode.LocalOnly, "invalid", retained).settings,
		)
	}

	@Test
	fun customRelayUrlsAreNormalized() {
		val result = validateRelaySettings(
			mode = RelayMode.StrictCustom,
			urlsText = " HTTPS://Relay.Example.com/ \nhttps://[2001:DB8::1]:443",
		)

		assertEquals(
			RelaySettings(
				RelayMode.StrictCustom,
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
			validateRelaySettings(RelayMode.StrictCustom, "http://relay.example.com").error,
		)
		assertEquals(
			RelaySettingsInputError.InvalidUrl(1),
			validateRelaySettings(RelayMode.StrictCustom, "https://relay.example.com/custom").error,
		)
	}

	@Test
	fun duplicateNormalizedRelayUrlsAreRejected() {
		val result = validateRelaySettings(
			RelayMode.StrictCustom,
			"https://relay.example.com\nHTTPS://RELAY.EXAMPLE.COM:443/",
		)

		assertEquals(RelaySettingsInputError.DuplicateUrl(2), result.error)
	}

	@Test
	fun structurallyValidIpv6RelayUrlsAreAccepted() {
		val result = validateRelaySettings(
			RelayMode.StrictCustom,
			"https://[::1]:443\nhttps://[2001:db8::1]\nhttps://[::ffff:192.0.2.1]",
		)

		assertEquals(
			RelaySettings(
				RelayMode.StrictCustom,
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
				validateRelaySettings(RelayMode.StrictCustom, url).error,
				url,
			)
		}
	}
}
