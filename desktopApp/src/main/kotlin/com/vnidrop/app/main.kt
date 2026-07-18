package com.vnidrop.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
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
	val linux = DesktopAppearanceBridge.isLinux()
	val customWindowChrome = macOs || linux
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
		val windowState = rememberWindowState()
		Window(
			onCloseRequest = ::exitApplication,
			state = windowState,
			title = "VniDrop",
			// Compose keeps edge resizers active for this client-decorated Linux window.
			undecorated = linux,
		) {
			App(
				dependencies = rememberJvmAppDependencies(externalInvitations),
				windowChromeTopInset = when {
					macOs -> MacOsTitleBarHeight
					linux -> LinuxTitleBarHeight
					else -> 0.dp
				},
				windowContentTopStartRadius = if (customWindowChrome) DesktopContentCornerRadius else 0.dp,
				windowChrome = when {
					macOs -> {
						{ MacOsTitleBar() }
					}
					linux -> {
						{
							LinuxTitleBar(
								isMaximized = windowState.placement == WindowPlacement.Maximized,
								onMinimize = { windowState.isMinimized = true },
								onToggleMaximize = {
									windowState.placement = toggledWindowPlacement(windowState.placement)
								},
								onClose = ::exitApplication,
							)
						}
					}
					else -> null
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
private val LinuxTitleBarHeight = 40.dp
private val LinuxWindowControlWidth = 46.dp
private val LinuxWindowControlsWidth = 138.dp
private val DesktopContentCornerRadius = 20.dp

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
					text = "VniDrop",
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

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun WindowScope.LinuxTitleBar(
	isMaximized: Boolean,
	onMinimize: () -> Unit,
	onToggleMaximize: () -> Unit,
	onClose: () -> Unit,
) {
	val colors = LocalVniDropColors.current
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.height(LinuxTitleBarHeight)
			.background(colors.backgroundSurface200),
	) {
		BasicText(
			text = "VniDrop",
			modifier = Modifier.align(Alignment.Center),
			style = TextStyle(
				color = colors.foregroundDefault,
				fontSize = 13.sp,
				fontWeight = FontWeight.SemiBold,
			),
		)
		WindowDraggableArea(
			modifier = Modifier
				.fillMaxSize()
				.padding(end = LinuxWindowControlsWidth)
				.onPointerEvent(PointerEventType.Press) { event ->
					if (event.awtEventOrNull?.clickCount == 2) onToggleMaximize()
				},
		) {
			Box(Modifier.fillMaxSize())
		}
		Row(
			modifier = Modifier
				.align(Alignment.CenterEnd)
				.fillMaxHeight(),
		) {
			LinuxWindowControlButton(
				icon = LinuxMinimizeIcon,
				contentDescription = "Minimize window",
				onClick = onMinimize,
			)
			LinuxWindowControlButton(
				icon = if (isMaximized) LinuxRestoreIcon else LinuxMaximizeIcon,
				contentDescription = if (isMaximized) "Restore window" else "Maximize window",
				onClick = onToggleMaximize,
			)
			LinuxWindowControlButton(
				icon = LinuxCloseIcon,
				contentDescription = "Close window",
				isClose = true,
				onClick = onClose,
			)
		}
	}
}

@Composable
private fun LinuxWindowControlButton(
	icon: ImageVector,
	contentDescription: String,
	isClose: Boolean = false,
	onClick: () -> Unit,
) {
	val colors = LocalVniDropColors.current
	val interactionSource = remember { MutableInteractionSource() }
	val hovered by interactionSource.collectIsHoveredAsState()
	val pressed by interactionSource.collectIsPressedAsState()
	val active = hovered || pressed
	val background = when {
		isClose && active -> colors.destructiveDefault
		active -> colors.backgroundOverlayHover
		else -> Color.Transparent
	}
	Box(
		modifier = Modifier
			.width(LinuxWindowControlWidth)
			.fillMaxHeight()
			.background(background)
			.hoverable(interactionSource)
			.clickable(
				interactionSource = interactionSource,
				indication = null,
				role = Role.Button,
				onClick = onClick,
			),
		contentAlignment = Alignment.Center,
	) {
		Image(
			painter = rememberVectorPainter(icon),
			contentDescription = contentDescription,
			colorFilter = ColorFilter.tint(if (isClose && active) Color.White else colors.foregroundLight),
			modifier = Modifier.size(15.dp),
		)
	}
}

internal fun toggledWindowPlacement(current: WindowPlacement): WindowPlacement =
	if (current == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized

private val LinuxMinimizeIcon = windowControlIcon("Minimize") {
	moveTo(6f, 12f)
	lineTo(18f, 12f)
}

private val LinuxMaximizeIcon = windowControlIcon("Maximize") {
	moveTo(6.5f, 6.5f)
	lineTo(17.5f, 6.5f)
	lineTo(17.5f, 17.5f)
	lineTo(6.5f, 17.5f)
	close()
}

private val LinuxRestoreIcon = windowControlIcon("Restore") {
	moveTo(8.5f, 8.5f)
	lineTo(18f, 8.5f)
	lineTo(18f, 18f)
	lineTo(8.5f, 18f)
	close()
	moveTo(6f, 15.5f)
	lineTo(6f, 6f)
	lineTo(15.5f, 6f)
}

private val LinuxCloseIcon = windowControlIcon("Close") {
	moveTo(7f, 7f)
	lineTo(17f, 17f)
	moveTo(17f, 7f)
	lineTo(7f, 17f)
}

private fun windowControlIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
	ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
		path(
			fill = SolidColor(Color.Transparent),
			stroke = SolidColor(Color.Black),
			strokeLineWidth = 1.6f,
			strokeLineCap = StrokeCap.Round,
			strokeLineJoin = StrokeJoin.Round,
			pathFillType = PathFillType.NonZero,
			pathBuilder = block,
		)
	}.build()
