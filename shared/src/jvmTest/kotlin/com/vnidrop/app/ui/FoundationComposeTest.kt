package com.vnidrop.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.runtime.mutableStateOf
import com.vnidrop.app.feature.approvals.ApprovalModalHost
import com.vnidrop.app.feature.approvals.ApprovalState
import com.vnidrop.app.feature.approvals.PendingApproval
import com.vnidrop.app.feature.settings.SettingsScreen
import com.vnidrop.app.feature.settings.SettingsSection
import com.vnidrop.app.feature.settings.SettingsState
import com.vnidrop.app.feature.send.SendScreen
import com.vnidrop.app.feature.send.SendState
import com.vnidrop.app.core.CoreState
import com.vnidrop.app.core.PickedShareFile
import com.vnidrop.app.core.ShareAccessPolicy
import com.vnidrop.app.core.Transfer
import com.vnidrop.app.core.TransferDirection
import com.vnidrop.app.core.TransferStatus
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
				ApprovalModalHost(
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
		onNodeWithContentDescription("Dismiss").assertIsDisplayed()
	}

	@Test
	fun compactSnackbarMovesActionBelowMessageAndClose() = runComposeUiTest {
		val controller = UiMessageController()
		controller.tryShow(
			UiMessage(
				text = UiText.Dynamic("Notifications are turned off for VniDrop. You can enable them in Settings."),
				actionLabel = UiText.Dynamic("Open Settings"),
			),
		)
		setContent {
			VniDropTheme(isDarkTheme = false) {
				Box(Modifier.width(320.dp)) { VniDropSnackbarHost(controller) }
			}
		}

		val messageBottom = onNodeWithText("Notifications are turned off for VniDrop. You can enable them in Settings.")
			.getUnclippedBoundsInRoot().bottom
		val closeBottom = onNodeWithContentDescription("Dismiss").getUnclippedBoundsInRoot().bottom
		val actionTop = onNodeWithText("Open Settings").getUnclippedBoundsInRoot().top
		assertTrue(messageBottom <= actionTop)
		assertTrue(closeBottom <= actionTop)
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
					floatingAction = {
						Box(Modifier.align(Alignment.BottomEnd).size(56.dp).testTag("floating-action"))
					},
				) {
					Text("Content")
				}
			}
		}

		val overlayBottom = onNodeWithTag("snackbar-overlay").getUnclippedBoundsInRoot().bottom
		val floatingActionTop = onNodeWithTag("floating-action").getUnclippedBoundsInRoot().top
		val navigationLabelTop = onNodeWithText("Send").getUnclippedBoundsInRoot().top
		assertTrue(overlayBottom <= floatingActionTop)
		assertTrue(overlayBottom <= navigationLabelTop)
	}

	@Test
	fun phoneSendEmptyStateOpensCreationDrawer() = runComposeUiTest {
		val state = mutableStateOf(SendState())
		setContent {
			VniDropTheme(isDarkTheme = false) {
				SendScreen(
					coreState = CoreState(isInitialized = true),
					state = state.value,
					windowClass = WindowClass.Phone,
					onOpenComposer = { state.value = state.value.copy(isComposerOpen = true) },
					onDismissComposer = {},
					onSelectFile = {},
					onClearFile = {},
					onTransferNameChanged = {},
					onSenderNameChanged = {},
					onAccessPolicyChanged = {},
					onCreateShare = {},
					onTransferSelected = {},
					onCloseTransferDetails = {},
					onCopyTicket = {},
				)
			}
		}

		onNodeWithText("New transfer").performClick()
		onNodeWithText("Choose what to share").assertIsDisplayed()
		onNodeWithText("Choose file").assertIsDisplayed()
	}

	@Test
	fun desktopTransferComposerReviewsFileAndAccessPolicy() = runComposeUiTest {
		var selectedPolicy: ShareAccessPolicy? = null
		setContent {
			VniDropTheme(isDarkTheme = false) {
				SendScreen(
					coreState = CoreState(isInitialized = true),
					state = SendState(
						isComposerOpen = true,
						selectedFile = PickedShareFile("/tmp/photos.zip", "photos.zip", 1536UL),
						transferName = "photos.zip",
						senderName = "Sender",
					),
					windowClass = WindowClass.Desktop,
					onOpenComposer = {},
					onDismissComposer = {},
					onSelectFile = {},
					onClearFile = {},
					onTransferNameChanged = {},
					onSenderNameChanged = {},
					onAccessPolicyChanged = { selectedPolicy = it },
					onCreateShare = {},
					onTransferSelected = {},
					onCloseTransferDetails = {},
					onCopyTicket = {},
				)
			}
		}

		onNodeWithText("1.5 KB").assertIsDisplayed()
		onNodeWithText("Anyone with this transfer").performClick()
		runOnIdle { assertEquals(ShareAccessPolicy.AnyoneWithTransfer, selectedPolicy) }
	}

	@Test
	fun transferCatalogOpensSelectedTransfer() = runComposeUiTest {
		var selectedId: ULong? = null
		setContent {
			VniDropTheme(isDarkTheme = false) {
				SendScreen(
					coreState = CoreState(isInitialized = true, transfers = listOf(outgoingTransfer())),
					state = SendState(),
					windowClass = WindowClass.Phone,
					onOpenComposer = {},
					onDismissComposer = {},
					onSelectFile = {},
					onClearFile = {},
					onTransferNameChanged = {},
					onSenderNameChanged = {},
					onAccessPolicyChanged = {},
					onCreateShare = {},
					onTransferSelected = { selectedId = it },
					onCloseTransferDetails = {},
					onCopyTicket = {},
				)
			}
		}

		onNodeWithText("Photos").performClick()
		runOnIdle { assertEquals(9UL, selectedId) }
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

	private fun outgoingTransfer() = Transfer(
		localId = "local-9",
		transferId = 9UL,
		direction = TransferDirection.Send,
		status = TransferStatus.Sharing,
		peerId = null,
		transferName = "Photos",
		contentHash = "hash",
		fileCount = 1UL,
		totalSize = 1536UL,
		ticket = "ticket",
		accessPolicy = ShareAccessPolicy.RequireApproval,
		createdAt = 1L,
		updatedAt = 1L,
	)
}
