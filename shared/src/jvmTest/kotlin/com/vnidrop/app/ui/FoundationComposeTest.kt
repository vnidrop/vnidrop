package com.vnidrop.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.runtime.mutableStateOf
import com.vnidrop.app.feature.approvals.ApprovalBannerHost
import com.vnidrop.app.feature.approvals.ApprovalState
import com.vnidrop.app.feature.approvals.PendingApproval
import com.vnidrop.app.feature.settings.SettingsScreen
import com.vnidrop.app.feature.settings.SettingsSection
import com.vnidrop.app.feature.settings.SettingsState
import com.vnidrop.app.notifications.NotificationPermission
import com.vnidrop.app.ui.feedback.UiMessage
import com.vnidrop.app.ui.feedback.UiMessageController
import com.vnidrop.app.ui.feedback.UiText
import com.vnidrop.app.ui.feedback.VniDropSnackbarHost
import com.vnidrop.app.ui.state.WindowClass
import com.vnidrop.app.ui.navigation.AppDestination
import com.vnidrop.app.ui.shell.AppShell
import com.vnidrop.app.ui.theme.VniDropTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FoundationComposeTest {
	@Test
	fun approvalBannerInvokesAcceptAction() = runComposeUiTest {
		var accepted: String? = null
		setContent {
			VniDropTheme(isDarkTheme = false) {
				ApprovalBannerHost(
					state = ApprovalState(pending = listOf(approval())),
					onAccept = { accepted = it },
					onRefuse = {},
				)
			}
		}
		onNodeWithText("Approve").performClick()
		runOnIdle { assertEquals("request", accepted) }
	}

	@Test
	fun phoneSettingsNavigatesToNotificationSection() = runComposeUiTest {
		val state = mutableStateOf(SettingsState())
		setContent {
			VniDropTheme(isDarkTheme = false) {
				SettingsScreen(
					state = state.value,
					windowClass = WindowClass.Phone,
					onSectionSelected = { state.value = state.value.copy(selectedSection = it) },
					onUsernameChanged = {},
					onThemeModeChanged = {},
					onChooseFolder = {},
					onResetFolder = {},
					onNotificationsChanged = {},
					onOpenNotificationSettings = {},
				)
			}
		}
		onNodeWithText("Notifications").performClick()
		onNodeWithText("Let VniDrop notify you about new connection requests while the app is running in the background.").assertIsDisplayed()
	}

	@Test
	fun notificationSettingCanBeToggledFromItsRow() = runComposeUiTest {
		var enabled = false
		setContent {
			VniDropTheme(isDarkTheme = false) {
				SettingsScreen(
					state = SettingsState(selectedSection = SettingsSection.Notifications),
					windowClass = WindowClass.Phone,
					onSectionSelected = {},
					onUsernameChanged = {},
					onThemeModeChanged = {},
					onChooseFolder = {},
					onResetFolder = {},
					onNotificationsChanged = { enabled = it },
					onOpenNotificationSettings = {},
				)
			}
		}
		onNodeWithText("Allow notifications").performClick()
		runOnIdle { assertEquals(true, enabled) }
	}

	@Test
	fun deniedNotificationSettingOffersSystemSettingsAction() = runComposeUiTest {
		var opened = false
		setContent {
			VniDropTheme(isDarkTheme = false) {
				SettingsScreen(
					state = SettingsState(
						selectedSection = SettingsSection.Notifications,
						notificationPermission = NotificationPermission.Denied,
					),
					windowClass = WindowClass.Phone,
					onSectionSelected = {},
					onUsernameChanged = {},
					onThemeModeChanged = {},
					onChooseFolder = {},
					onResetFolder = {},
					onNotificationsChanged = {},
					onOpenNotificationSettings = { opened = true },
				)
			}
		}
		onNodeWithText("Open Settings").performClick()
		runOnIdle { assertTrue(opened) }
	}

	@Test
	fun snackbarDisplaysBufferedMessage() = runComposeUiTest {
		val controller = UiMessageController()
		controller.tryShow(UiMessage(UiText.Dynamic("Saved successfully")))
		setContent {
			VniDropTheme(isDarkTheme = false) { VniDropSnackbarHost(controller) }
		}
		onNodeWithText("Saved successfully").assertIsDisplayed()
	}

	@Test
	fun phoneSnackbarOverlayStopsAboveBottomNavigation() = runComposeUiTest {
		setContent {
			VniDropTheme(isDarkTheme = false) {
				AppShell(
					selectedDestination = AppDestination.Send,
					windowClass = WindowClass.Phone,
					onDestinationSelected = {},
					overlay = {
						Box(Modifier.align(Alignment.BottomCenter).size(20.dp).testTag("snackbar-overlay"))
					},
				) {
					Text("Content")
				}
			}
		}

		val overlayBottom = onNodeWithTag("snackbar-overlay").getUnclippedBoundsInRoot().bottom
		val navigationLabelTop = onNodeWithText("Send").getUnclippedBoundsInRoot().top
		assertTrue(overlayBottom <= navigationLabelTop)
	}

	@Test
	fun snackbarActionAndCancellationAreForwarded() = runComposeUiTest {
		val controller = UiMessageController()
		var actionCount = 0
		controller.tryShow(
			UiMessage(
				text = UiText.Dynamic("Undoable action"),
				actionLabel = UiText.Dynamic("Undo"),
				onAction = { actionCount += 1 },
			),
		)
		setContent { VniDropTheme(isDarkTheme = false) { VniDropSnackbarHost(controller) } }
		onNodeWithText("Undo").performClick()
		runOnIdle { assertEquals(1, actionCount) }

		controller.tryShow(UiMessage(UiText.Dynamic("Dismiss me")))
		onNodeWithText("Dismiss me").assertIsDisplayed()
		controller.dismissCurrent()
		onAllNodesWithText("Dismiss me").assertCountEquals(0)
	}

	private fun approval() = PendingApproval(
		id = "request",
		transferId = 1UL,
		transferName = "Photos",
		receiverName = "Alice",
		receiverDeviceName = "Phone",
		requestedAt = 1L,
	)
}
