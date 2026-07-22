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
	val PaperPlane = lineIcon("PaperPlane") {
		moveTo(22f, 2f)
		lineTo(15f, 22f)
		lineTo(11f, 13f)
		lineTo(2f, 9f)
		close()
		moveTo(22f, 2f)
		lineTo(11f, 13f)
	}
	val AccountOff = lineIcon("AccountOff") {
		circle(9f, 8f, 4f)
		moveTo(2f, 21f)
		curveTo(2.8f, 16.8f, 5f, 15f, 9f, 15f)
		curveTo(11.1f, 15f, 12.7f, 15.5f, 14f, 16.5f)
		circle(18f, 18f, 4f)
		moveTo(16.6f, 16.6f)
		lineTo(19.4f, 19.4f)
		moveTo(19.4f, 16.6f)
		lineTo(16.6f, 19.4f)
	}
	val ShieldCheck = lineIcon("ShieldCheck") {
		moveTo(12f, 22f)
		curveTo(17f, 19.5f, 20f, 16.5f, 20f, 11f)
		lineTo(20f, 5f)
		lineTo(12f, 2f)
		lineTo(4f, 5f)
		lineTo(4f, 11f)
		curveTo(4f, 16.5f, 7f, 19.5f, 12f, 22f)
		moveTo(8f, 12f)
		lineTo(11f, 15f)
		lineTo(16f, 9f)
	}
	val Shield = lineIcon("Shield") {
		moveTo(12f, 22f)
		curveTo(17f, 19.5f, 20f, 16.5f, 20f, 11f)
		lineTo(20f, 5f)
		lineTo(12f, 2f)
		lineTo(4f, 5f)
		lineTo(4f, 11f)
		curveTo(4f, 16.5f, 7f, 19.5f, 12f, 22f)
	}
	val Lock = lineIcon("Lock") {
		roundRect(5f, 10f, 14f, 11f, 2f)
		moveTo(8f, 10f)
		lineTo(8f, 7f)
		arcTo(4f, 4f, 0f, false, true, 16f, 7f)
		lineTo(16f, 10f)
	}
	val Code = lineIcon("Code") {
		moveTo(8f, 9f)
		lineTo(3f, 14f)
		lineTo(8f, 19f)
		moveTo(16f, 9f)
		lineTo(21f, 14f)
		lineTo(16f, 19f)
		moveTo(14f, 4f)
		lineTo(10f, 22f)
	}
	val CloudOff = lineIcon("CloudOff") {
		moveTo(5.5f, 5.5f)
		lineTo(18.5f, 18.5f)
		moveTo(7f, 18f)
		lineTo(6f, 18f)
		curveTo(2.7f, 18f, 1f, 16.2f, 1f, 13.5f)
		curveTo(1f, 10.7f, 3.1f, 8.5f, 6f, 8.1f)
		curveTo(7.5f, 4.9f, 10.1f, 3f, 13.5f, 3f)
		curveTo(18f, 3f, 21f, 6.5f, 21f, 11f)
		curveTo(22.3f, 12f, 23f, 13.4f, 23f, 15f)
		curveTo(23f, 16.1f, 22.7f, 17f, 22f, 18f)
	}
	val Sync = lineIcon("Sync") {
		moveTo(20f, 7f)
		lineTo(20f, 3f)
		lineTo(16f, 3f)
		moveTo(20f, 3f)
		curveTo(17.7f, 1.4f, 14.8f, 1f, 12f, 2f)
		curveTo(9.6f, 2.8f, 7.7f, 4.6f, 7f, 7f)
		moveTo(4f, 17f)
		lineTo(4f, 21f)
		lineTo(8f, 21f)
		moveTo(4f, 21f)
		curveTo(6.3f, 22.6f, 9.2f, 23f, 12f, 22f)
		curveTo(14.4f, 21.2f, 16.3f, 19.4f, 17f, 17f)
	}
	val Megaphone = lineIcon("Megaphone") {
		moveTo(3f, 11f)
		lineTo(3f, 15f)
		lineTo(7f, 15f)
		lineTo(18f, 20f)
		lineTo(18f, 6f)
		lineTo(7f, 11f)
		close()
		moveTo(7f, 15f)
		lineTo(9f, 21f)
		lineTo(13f, 21f)
		lineTo(11.5f, 17f)
		moveTo(21f, 10f)
		lineTo(21f, 16f)
	}
	val QrCode = lineIcon("QrCode") {
		moveTo(3f, 9f)
		lineTo(3f, 3f)
		lineTo(9f, 3f)
		moveTo(15f, 3f)
		lineTo(21f, 3f)
		lineTo(21f, 9f)
		moveTo(3f, 15f)
		lineTo(3f, 21f)
		lineTo(9f, 21f)
		moveTo(15f, 21f)
		lineTo(15f, 15f)
		lineTo(21f, 15f)
		moveTo(7f, 7f)
		lineTo(7.01f, 7f)
		moveTo(17f, 7f)
		lineTo(17.01f, 7f)
		moveTo(7f, 17f)
		lineTo(7.01f, 17f)
		moveTo(20f, 20f)
		lineTo(20.01f, 20f)
	}
	val Hand = lineIcon("Hand", strokeWidth = 1.6f) {
		moveTo(4f, 14f)
		lineTo(4f, 10f)
		curveTo(4f, 8.9f, 4.9f, 8f, 6f, 8f)
		curveTo(7.1f, 8f, 8f, 8.9f, 8f, 10f)
		lineTo(8f, 12f)
		lineTo(8.5f, 12f)
		lineTo(8.5f, 6f)
		curveTo(8.5f, 4.9f, 9.4f, 4f, 10.5f, 4f)
		curveTo(11.6f, 4f, 12.5f, 4.9f, 12.5f, 6f)
		lineTo(12.5f, 11f)
		lineTo(13f, 11f)
		lineTo(13f, 4f)
		curveTo(13f, 2.9f, 13.9f, 2f, 15f, 2f)
		curveTo(16.1f, 2f, 17f, 2.9f, 17f, 4f)
		lineTo(17f, 12f)
		lineTo(17.5f, 12f)
		lineTo(17.5f, 7f)
		curveTo(17.5f, 5.9f, 18.4f, 5f, 19.5f, 5f)
		curveTo(20.6f, 5f, 21.5f, 5.9f, 21.5f, 7f)
		lineTo(21.5f, 14f)
		lineTo(22f, 13.5f)
		curveTo(22.4f, 13.1f, 23f, 13.2f, 23.4f, 13.7f)
		curveTo(23.9f, 14.4f, 23.7f, 15.3f, 23.1f, 16f)
		lineTo(18.8f, 21f)
		curveTo(17.7f, 22.3f, 16.1f, 23f, 14.3f, 23f)
		lineTo(12f, 23f)
		curveTo(7.6f, 23f, 4f, 19.4f, 4f, 15f)
		close()
	}
	val Radio = lineIcon("Radio") {
		moveTo(12f, 12f)
		lineTo(12f, 22f)
		moveTo(9f, 22f)
		lineTo(15f, 22f)
		moveTo(9f, 9f)
		curveTo(7.5f, 10.7f, 7.5f, 13.3f, 9f, 15f)
		moveTo(15f, 9f)
		curveTo(16.5f, 10.7f, 16.5f, 13.3f, 15f, 15f)
		moveTo(6f, 6f)
		curveTo(2.7f, 9.3f, 2.7f, 14.7f, 6f, 18f)
		moveTo(18f, 6f)
		curveTo(21.3f, 9.3f, 21.3f, 14.7f, 18f, 18f)
		circle(12f, 12f, 1f)
	}
	val Drive = lineIcon("Drive") {
		roundRect(2f, 6f, 20f, 12f, 3f)
		moveTo(2f, 14f)
		lineTo(22f, 14f)
		moveTo(17f, 16f)
		lineTo(17.01f, 16f)
		moveTo(20f, 16f)
		lineTo(20.01f, 16f)
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

private fun lineIcon(
	name: String,
	strokeWidth: Float = 2f,
	block: PathBuilder.() -> Unit,
): ImageVector =
	ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
		path(
			fill = SolidColor(Color.Transparent),
			stroke = SolidColor(Color.Black),
			strokeLineWidth = strokeWidth,
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

private fun PathBuilder.circle(centerX: Float, centerY: Float, radius: Float) {
	moveTo(centerX + radius, centerY)
	arcTo(radius, radius, 0f, true, true, centerX - radius, centerY)
	arcTo(radius, radius, 0f, true, true, centerX + radius, centerY)
}
