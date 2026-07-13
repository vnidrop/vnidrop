package com.vnidrop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.application
import com.vnidrop.app.platform.DesktopAppearanceBridge
import com.vnidrop.app.feature.send.DesktopShareBridge
import com.vnidrop.app.feature.receive.ExternalInvitationController
import com.vnidrop.app.feature.receive.MaxVniDropInvitationBytes
import com.vnidrop.app.feature.receive.VniDropInvitationExtension
import com.vnidrop.app.feature.receive.decodeInvitationBytes
import com.vnidrop.app.ui.theme.LocalVniDropColors
import java.awt.Desktop
import java.io.File

fun main(args: Array<String>) {
	val externalInvitations = ExternalInvitationController()
	val macOs = DesktopAppearanceBridge.isMacOs()
	configureMacOsNativeAppearance()
	configureInvitationOpenHandler(externalInvitations)
	args.asSequence()
		.map(::File)
		.filter { it.extension.equals(VniDropInvitationExtension, ignoreCase = true) }
		.forEach { externalInvitations.openFile(it) }
	DesktopAppearanceBridge.applyNativeAppearance = MacOsAppKitAppearance::apply
	if (macOs) {
		DesktopShareBridge.shareFile = MacOsShareSheet::share
	}
	application {
		Window(
			onCloseRequest = ::exitApplication,
			title = "vnidrop",
		) {
			App(
				dependencies = rememberJvmAppDependencies(externalInvitations),
				windowChromeTopInset = if (macOs) MacOsTitleBarHeight else 0.dp,
				windowContentTopStartRadius = if (macOs) MacOsContentCornerRadius else 0.dp,
				windowChrome = if (macOs) {
					{ MacOsTitleBar() }
				} else {
					null
				},
			)
		}
	}
}

private fun configureInvitationOpenHandler(controller: ExternalInvitationController) {
	if (!Desktop.isDesktopSupported()) return
	val desktop = Desktop.getDesktop()
	if (!desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) return
	desktop.setOpenFileHandler { event -> event.files.forEach(controller::openFile) }
}

private fun ExternalInvitationController.openFile(file: File) {
	val result = runCatching {
		require(file.extension.equals(VniDropInvitationExtension, ignoreCase = true)) { "This is not a VniDrop invitation" }
		val bytes = file.inputStream().use { it.readNBytes(MaxVniDropInvitationBytes + 1) }
		decodeInvitationBytes(bytes)
	}
	result.fold(::openInvitation) { error ->
		reportOpenFailure(error.message ?: "The invitation could not be opened")
	}
}

private fun configureMacOsNativeAppearance() {
	if (!DesktopAppearanceBridge.isMacOs()) return
	// AWT reads this before creating the first native window. Runtime theme
	// changes are handled in the JVM platform appearance hook.
	System.setProperty("apple.awt.application.appearance", "system")
}

private val MacOsTitleBarHeight = 28.dp
private val MacOsTrafficLightsWidth = 76.dp
private val MacOsContentCornerRadius = 20.dp

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun WindowScope.MacOsTitleBar() {
	val colors = LocalVniDropColors.current
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.height(MacOsTitleBarHeight)
			.background(colors.backgroundSurface200),
	) {
		WindowDraggableArea(
			modifier = Modifier
				.fillMaxSize()
				.padding(start = MacOsTrafficLightsWidth)
				.onPointerEvent(PointerEventType.Press) { event ->
					if (event.awtEventOrNull?.clickCount == 2) {
						DesktopAppearanceBridge.toggleMaximized(window)
					}
				},
		) {
			Box(modifier = Modifier.fillMaxSize().padding(end = MacOsTrafficLightsWidth)) {
				BasicText(
					text = "vnidrop",
					modifier = Modifier.align(Alignment.Center),
					style = TextStyle(
						color = colors.foregroundDefault,
						fontSize = 13.sp,
						fontWeight = FontWeight.SemiBold,
					),
				)
			}
		}
	}
}
