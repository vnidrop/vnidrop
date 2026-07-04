package com.vnidrop.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ThemeMode {
	System,
	Light,
	Dark,
}

fun resolveDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean =
	when (mode) {
		ThemeMode.System -> systemDark
		ThemeMode.Light -> false
		ThemeMode.Dark -> true
	}

@Immutable
data class VniDropColors(
	val canvas: Color,
	val sidebar: Color,
	val surface: Color,
	val surfaceRaised: Color,
	val surfaceMuted: Color,
	val border: Color,
	val borderStrong: Color,
	val textPrimary: Color,
	val textSecondary: Color,
	val textMuted: Color,
	val brand: Color,
	val brandPressed: Color,
	val warning: Color,
	val destructive: Color,
	val success: Color,
)

val LocalVniDropColors = staticCompositionLocalOf { lightVniDropColors }

private val lightVniDropColors = VniDropColors(
	canvas = hsl(0f, 0f, 97.3f),
	sidebar = hsl(0f, 0f, 98.8f),
	surface = hsl(0f, 0f, 100f),
	surfaceRaised = hsl(0f, 0f, 98.8f),
	surfaceMuted = hsl(0f, 0f, 95.3f),
	border = hsl(0f, 0f, 85.9f),
	borderStrong = hsl(0f, 0f, 78f),
	textPrimary = hsl(0f, 0f, 9f),
	textSecondary = hsl(0f, 0f, 32.2f),
	textMuted = hsl(0f, 0f, 43.9f),
	brand = hsl(153.1f, 60.2f, 52.7f),
	brandPressed = hsl(152.9f, 56.1f, 46.5f),
	warning = hsl(38.9f, 100f, 57.1f),
	destructive = hsl(10.2f, 77.9f, 53.9f),
	success = hsl(153.1f, 60.2f, 40f),
)

private val darkVniDropColors = VniDropColors(
	canvas = hsl(0f, 0f, 7.1f),
	sidebar = hsl(0f, 0f, 9f),
	surface = hsl(0f, 0f, 12.2f),
	surfaceRaised = hsl(0f, 0f, 14.1f),
	surfaceMuted = hsl(0f, 0f, 16.1f),
	border = hsl(0f, 0f, 24.3f),
	borderStrong = hsl(0f, 0f, 31.4f),
	textPrimary = hsl(0f, 0f, 98f),
	textSecondary = hsl(0f, 0f, 70.6f),
	textMuted = hsl(0f, 0f, 53.7f),
	brand = hsl(153.1f, 60.2f, 52.7f),
	brandPressed = hsl(152.9f, 56.1f, 46.5f),
	warning = hsl(38.9f, 100f, 42.9f),
	destructive = hsl(10.2f, 77.9f, 53.9f),
	success = hsl(153.1f, 60.2f, 52.7f),
)

private fun materialScheme(tokens: VniDropColors, dark: Boolean): ColorScheme {
	val base = if (dark) {
		darkColorScheme()
	} else {
		lightColorScheme()
	}
	return base.copy(
		primary = tokens.brand,
		onPrimary = if (dark) Color.Black else Color.White,
		primaryContainer = tokens.surfaceMuted,
		onPrimaryContainer = tokens.textPrimary,
		background = tokens.canvas,
		onBackground = tokens.textPrimary,
		surface = tokens.surface,
		onSurface = tokens.textPrimary,
		surfaceVariant = tokens.surfaceMuted,
		onSurfaceVariant = tokens.textSecondary,
		outline = tokens.border,
		outlineVariant = tokens.border,
		error = tokens.destructive,
		errorContainer = tokens.destructive.copy(alpha = if (dark) 0.22f else 0.16f),
		onErrorContainer = tokens.textPrimary,
	)
}

@Composable
fun VniDropTheme(
	mode: ThemeMode,
	content: @Composable () -> Unit,
) {
	val dark = resolveDarkTheme(mode, isSystemInDarkTheme())
	val tokens = if (dark) darkVniDropColors else lightVniDropColors
	androidx.compose.runtime.CompositionLocalProvider(LocalVniDropColors provides tokens) {
		MaterialTheme(
			colorScheme = materialScheme(tokens, dark),
			content = content,
		)
	}
}

private fun hsl(hue: Float, saturation: Float, lightness: Float): Color {
	val h = ((hue % 360f) + 360f) % 360f / 360f
	val s = saturation.coerceIn(0f, 100f) / 100f
	val l = lightness.coerceIn(0f, 100f) / 100f
	if (s == 0f) {
		return Color(l, l, l)
	}
	val q = if (l < 0.5f) l * (1 + s) else l + s - l * s
	val p = 2 * l - q
	return Color(
		red = hueToRgb(p, q, h + 1f / 3f),
		green = hueToRgb(p, q, h),
		blue = hueToRgb(p, q, h - 1f / 3f),
	)
}

private fun hueToRgb(p: Float, q: Float, input: Float): Float {
	var t = input
	if (t < 0f) t += 1f
	if (t > 1f) t -= 1f
	return when {
		t < 1f / 6f -> p + (q - p) * 6f * t
		t < 1f / 2f -> q
		t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
		else -> p
	}.let { min(1f, max(0f, it)) }
}

fun Color.contrastAgainst(other: Color): Float =
	abs(luminanceApproximation() - other.luminanceApproximation())

private fun Color.luminanceApproximation(): Float =
	(red * 0.2126f) + (green * 0.7152f) + (blue * 0.0722f)
