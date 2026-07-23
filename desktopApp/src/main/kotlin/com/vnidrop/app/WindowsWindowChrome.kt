package com.vnidrop.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.vnidrop.app.ui.theme.LocalVniDropColors

internal class WindowsChromeConfiguration(
	val topInset: Dp = 0.dp,
	val contentTopStartRadius: Dp = 0.dp,
	val useNativeBackdrop: Boolean = false,
	val onDarkThemeChanged: (Boolean) -> Unit = {},
	val chrome: (@Composable () -> Unit)? = null,
)

@Composable
internal fun WindowScope.WindowsWindowFrame(
	windowState: WindowState,
	content: @Composable (WindowsChromeConfiguration) -> Unit,
) {
	val controller = remember(window) { WindowsNativeWindowController.install(window) }
	DisposableEffect(controller) {
		onDispose { controller?.close() }
	}
	if (controller == null) {
		content(WindowsChromeConfiguration())
		return
	}
	if (!controller.usesCustomChrome) {
		content(WindowsChromeConfiguration())
		return
	}

	val density = LocalDensity.current
	val insets = controller.frameInsets
	Box(
		modifier = Modifier
			.fillMaxSize()
			.padding(
				start = with(density) { insets.left.toDp() },
				top = with(density) { insets.top.toDp() },
				end = with(density) { insets.right.toDp() },
				bottom = with(density) { insets.bottom.toDp() },
			),
	) {
		content(
			WindowsChromeConfiguration(
				topInset = WindowsTitleBarHeight,
				contentTopStartRadius = DesktopContentCornerRadius,
				useNativeBackdrop = controller.usesNativeBackdrop,
				onDarkThemeChanged = controller::setDarkTheme,
				chrome = {
					WindowsTitleBar(
						controller = controller,
						isMaximized = windowState.placement == WindowPlacement.Maximized,
					)
				},
			),
		)
	}
}

@Composable
private fun WindowsTitleBar(
	controller: WindowsNativeWindowController,
	isMaximized: Boolean,
) {
	val colors = LocalVniDropColors.current
	val foreground = colors.foregroundDefault.copy(alpha = if (controller.isWindowActive) 1f else 0.55f)
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.height(WindowsTitleBarHeight)
			.background(if (controller.usesNativeBackdrop) Color.Transparent else colors.backgroundSurface200)
			.onGloballyPositioned { controller.updateCaptionBounds(it.boundsInWindow()) },
	) {
		BasicText(
			text = "VniDrop",
			modifier = Modifier.align(Alignment.Center),
			style = TextStyle(
				color = foreground,
				fontSize = 13.sp,
				fontWeight = FontWeight.SemiBold,
			),
		)
		if (!controller.usesSystemCaptionButtons) {
			Row(modifier = Modifier.align(Alignment.TopEnd)) {
				WindowsCaptionButton(
					button = WindowsCaptionButton.Minimize,
					icon = WindowsMinimizeIcon,
					contentDescription = "Minimize window",
					controller = controller,
					onClick = controller::minimize,
				)
				WindowsCaptionButton(
					button = WindowsCaptionButton.Maximize,
					icon = if (isMaximized) WindowsRestoreIcon else WindowsMaximizeIcon,
					contentDescription = if (isMaximized) "Restore window" else "Maximize window",
					controller = controller,
					onClick = controller::toggleMaximize,
				)
				WindowsCaptionButton(
					button = WindowsCaptionButton.Close,
					icon = WindowsCloseIcon,
					contentDescription = "Close window",
					controller = controller,
					onClick = { controller.postCloseRequest() },
				)
			}
		}
	}
}

@Composable
private fun WindowsCaptionButton(
	button: WindowsCaptionButton,
	icon: ImageVector,
	contentDescription: String,
	controller: WindowsNativeWindowController,
	onClick: () -> Unit,
) {
	val colors = LocalVniDropColors.current
	val hovered = controller.hoveredCaptionButton == button
	val pressed = controller.pressedCaptionButton == button
	val destructive = button == WindowsCaptionButton.Close && (hovered || pressed)
	val background = when {
		destructive -> colors.destructiveDefault
		pressed -> colors.backgroundOverlayHover
		hovered -> colors.backgroundOverlayHover
		else -> Color.Transparent
	}
	val foreground = when {
		destructive -> Color.White
		controller.isWindowActive -> colors.foregroundDefault
		else -> colors.foregroundDefault.copy(alpha = 0.55f)
	}
	Box(
		modifier = Modifier
			.size(width = WindowsCaptionButtonWidth, height = WindowsCaptionButtonHeight)
			.background(background)
			.clickable(role = Role.Button, onClick = onClick)
			.onGloballyPositioned { coordinates ->
				when (button) {
					WindowsCaptionButton.Minimize -> controller.updateMinimizeButtonBounds(coordinates.boundsInWindow())
					WindowsCaptionButton.Maximize -> controller.updateMaximizeButtonBounds(coordinates.boundsInWindow())
					WindowsCaptionButton.Close -> controller.updateCloseButtonBounds(coordinates.boundsInWindow())
				}
			},
		contentAlignment = Alignment.Center,
	) {
		Image(
			painter = rememberVectorPainter(icon),
			contentDescription = contentDescription,
			colorFilter = ColorFilter.tint(foreground),
			modifier = Modifier.size(24.dp),
		)
	}
}

internal val WindowsTitleBarHeight = WindowsTitleBarHeightDip.dp
private val WindowsCaptionButtonWidth = 48.dp
private val WindowsCaptionButtonHeight = 48.dp

private val WindowsMinimizeIcon = windowsCaptionIcon("Minimize") {
	moveTo(5f, 12f)
	lineTo(19f, 12f)
}

private val WindowsMaximizeIcon = windowsCaptionIcon("Maximize") {
	moveTo(6.5f, 6.5f)
	lineTo(17.5f, 6.5f)
	lineTo(17.5f, 17.5f)
	lineTo(6.5f, 17.5f)
	close()
}

private val WindowsRestoreIcon = windowsCaptionIcon("Restore") {
	moveTo(8.5f, 8.5f)
	lineTo(18f, 8.5f)
	lineTo(18f, 18f)
	lineTo(8.5f, 18f)
	close()
	moveTo(6f, 15.5f)
	lineTo(6f, 6f)
	lineTo(15.5f, 6f)
}

private val WindowsCloseIcon = windowsCaptionIcon("Close") {
	moveTo(6.5f, 6.5f)
	lineTo(17.5f, 17.5f)
	moveTo(17.5f, 6.5f)
	lineTo(6.5f, 17.5f)
}

private fun windowsCaptionIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
	ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
		path(
			fill = SolidColor(Color.Transparent),
			stroke = SolidColor(Color.Black),
			strokeLineWidth = 1.4f,
			strokeLineCap = StrokeCap.Square,
			strokeLineJoin = StrokeJoin.Miter,
			pathFillType = PathFillType.NonZero,
			pathBuilder = block,
		)
	}.build()
