package com.vnidrop.app.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object SettingsIcons {
	val ChevronRight = lineIcon("ChevronRight") {
		moveTo(9f, 18f)
		lineTo(15f, 12f)
		lineTo(9f, 6f)
	}
	val Back = lineIcon("Back") {
		moveTo(19f, 12f)
		lineTo(5f, 12f)
		moveTo(12f, 19f)
		lineTo(5f, 12f)
		lineTo(12f, 5f)
	}
	val Check = lineIcon("Check") {
		moveTo(20f, 6f)
		lineTo(9f, 17f)
		lineTo(4f, 12f)
	}
	val Sun = lineIcon("Sun") {
		moveTo(12f, 4f)
		lineTo(12f, 2f)
		moveTo(12f, 22f)
		lineTo(12f, 20f)
		moveTo(4.93f, 4.93f)
		lineTo(6.34f, 6.34f)
		moveTo(17.66f, 17.66f)
		lineTo(19.07f, 19.07f)
		moveTo(2f, 12f)
		lineTo(4f, 12f)
		moveTo(20f, 12f)
		lineTo(22f, 12f)
		moveTo(4.93f, 19.07f)
		lineTo(6.34f, 17.66f)
		moveTo(17.66f, 6.34f)
		lineTo(19.07f, 4.93f)
		moveTo(16f, 12f)
		arcTo(4f, 4f, 0f, true, true, 8f, 12f)
		arcTo(4f, 4f, 0f, true, true, 16f, 12f)
	}
	val Moon = lineIcon("Moon") {
		moveTo(21f, 12.79f)
		arcTo(9f, 9f, 0f, true, true, 11.21f, 3f)
		arcTo(7f, 7f, 0f, false, false, 21f, 12.79f)
	}
	val Device = lineIcon("Device") {
		roundRect(7f, 2f, 10f, 20f, 2.5f)
		moveTo(11f, 18f)
		lineTo(13f, 18f)
	}
	val Folder = lineIcon("Folder") {
		moveTo(3f, 7f)
		lineTo(9f, 7f)
		lineTo(11f, 9f)
		lineTo(21f, 9f)
		lineTo(21f, 19f)
		lineTo(3f, 19f)
		close()
	}
	val Info = lineIcon("Info") {
		moveTo(12f, 16f)
		lineTo(12f, 12f)
		moveTo(12f, 8f)
		lineTo(12.01f, 8f)
		moveTo(21f, 12f)
		arcTo(9f, 9f, 0f, true, true, 3f, 12f)
		arcTo(9f, 9f, 0f, true, true, 21f, 12f)
	}
	val Bell = lineIcon("Bell") {
		moveTo(18f, 8f)
		arcTo(6f, 6f, 0f, false, false, 6f, 8f)
		lineTo(6f, 13f)
		lineTo(4f, 17f)
		lineTo(20f, 17f)
		lineTo(18f, 13f)
		close()
		moveTo(10f, 21f)
		arcTo(2f, 2f, 0f, false, false, 14f, 21f)
	}
	/** Broadcast arcs over a point — the relay/network section. */
	val Antenna = lineIcon("Antenna") {
		moveTo(12f, 13f)
		arcTo(1.5f, 1.5f, 0f, false, false, 12f, 10f)
		arcTo(1.5f, 1.5f, 0f, false, false, 12f, 13f)
		close()
		moveTo(8.5f, 15f)
		arcTo(5f, 5f, 0f, false, true, 8.5f, 8f)
		moveTo(15.5f, 8f)
		arcTo(5f, 5f, 0f, false, true, 15.5f, 15f)
		moveTo(6f, 18f)
		arcTo(9f, 9f, 0f, false, true, 6f, 5f)
		moveTo(18f, 5f)
		arcTo(9f, 9f, 0f, false, true, 18f, 18f)
	}
	val Document = lineIcon("Document") {
		moveTo(14f, 2f)
		lineTo(6f, 2f)
		arcTo(2f, 2f, 0f, false, false, 4f, 4f)
		lineTo(4f, 20f)
		arcTo(2f, 2f, 0f, false, false, 6f, 22f)
		lineTo(18f, 22f)
		arcTo(2f, 2f, 0f, false, false, 20f, 20f)
		lineTo(20f, 8f)
		lineTo(14f, 2f)
		moveTo(14f, 2f)
		lineTo(14f, 8f)
		lineTo(20f, 8f)
	}
	val Bug = lineIcon("Bug") {
		roundRect(7f, 6f, 10f, 14f, 5f)
		moveTo(3f, 10f)
		lineTo(7f, 10f)
		moveTo(17f, 10f)
		lineTo(21f, 10f)
		moveTo(3f, 16f)
		lineTo(7f, 16f)
		moveTo(17f, 16f)
		lineTo(21f, 16f)
		moveTo(12f, 6f)
		lineTo(12f, 20f)
	}
}

private fun lineIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
	ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
		path(
			fill = SolidColor(Color.Transparent),
			stroke = SolidColor(Color.Black),
			strokeLineWidth = 2f,
			strokeLineCap = StrokeCap.Round,
			strokeLineJoin = StrokeJoin.Round,
			pathFillType = PathFillType.NonZero,
			pathBuilder = block,
		)
	}.build()

private fun PathBuilder.roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float) {
	moveTo(x + radius, y)
	lineTo(x + width - radius, y)
	arcTo(radius, radius, 0f, false, true, x + width, y + radius)
	lineTo(x + width, y + height - radius)
	arcTo(radius, radius, 0f, false, true, x + width - radius, y + height)
	lineTo(x + radius, y + height)
	arcTo(radius, radius, 0f, false, true, x, y + height - radius)
	lineTo(x, y + radius)
	arcTo(radius, radius, 0f, false, true, x + radius, y)
}
