package com.vnidrop.app.ui.icons

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.vnidrop.app.UiPlatform
import com.vnidrop.app.ui.platform.LocalUiPlatform
import com.vnidrop.app.ui.theme.VniDropTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class PlatformIconsComposeTest {
	@Test
	fun everyPlatformIconResourceRenders() = runComposeUiTest {
		val platforms = listOf(UiPlatform.Android, UiPlatform.Windows, UiPlatform.Linux)
		setContent {
			VniDropTheme(isDarkTheme = false) {
				Column {
					platforms.forEach { platform ->
						CompositionLocalProvider(LocalUiPlatform provides platform) {
							AppIcon.entries.chunked(10).forEach { icons ->
								Row {
									icons.forEach { icon ->
										PlatformIcon(
											icon = icon,
											contentDescription = null,
											modifier = Modifier.size(24.dp).testTag("${platform.name}-${icon.name}"),
										)
									}
								}
							}
						}
					}
				}
			}
		}

		platforms.forEach { platform ->
			AppIcon.entries.forEach { icon ->
				onNodeWithTag("${platform.name}-${icon.name}").assertIsDisplayed()
			}
		}
	}
}
