package com.vnidrop.app.ui.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object VniDropIcons {
	val Send: ImageVector by lazy {
		ImageVector.Builder("Send", 24.dp, 24.dp, 24f, 24f).apply {
			path(
				fill = SolidColor(Color.Transparent),
				stroke = SolidColor(Color.Black),
				strokeLineWidth = 2f,
				strokeLineCap = StrokeCap.Round,
				strokeLineJoin = StrokeJoin.Round,
				pathFillType = PathFillType.NonZero,
			) {
				moveTo(22f, 2f)
				lineTo(11f, 13f)
				moveTo(22f, 2f)
				lineTo(15f, 22f)
				lineTo(11f, 13f)
				lineTo(2f, 9f)
				lineTo(22f, 2f)
			}
		}.build()
	}

	val Receive: ImageVector by lazy {
		ImageVector.Builder("Receive", 24.dp, 24.dp, 24f, 24f).apply {
			path(
				fill = SolidColor(Color.Transparent),
				stroke = SolidColor(Color.Black),
				strokeLineWidth = 2f,
				strokeLineCap = StrokeCap.Round,
				strokeLineJoin = StrokeJoin.Round,
				pathFillType = PathFillType.NonZero,
			) {
				moveTo(12f, 3f)
				lineTo(12f, 15f)
				moveTo(7f, 10f)
				lineTo(12f, 15f)
				lineTo(17f, 10f)
				moveTo(5f, 21f)
				lineTo(19f, 21f)
				moveTo(5f, 17f)
				lineTo(5f, 21f)
				moveTo(19f, 17f)
				lineTo(19f, 21f)
			}
		}.build()
	}

	val Settings: ImageVector by lazy {
		ImageVector.Builder("Settings", 24.dp, 24.dp, 24f, 24f).apply {
			path(
				fill = SolidColor(Color.Transparent),
				stroke = SolidColor(Color.Black),
				strokeLineWidth = 2f,
				strokeLineCap = StrokeCap.Round,
				strokeLineJoin = StrokeJoin.Round,
				pathFillType = PathFillType.NonZero,
			) {
				moveTo(12f, 15f)
				arcTo(3f, 3f, 0f, false, false, 12f, 9f)
				arcTo(3f, 3f, 0f, false, false, 12f, 15f)
				moveTo(19.4f, 15f)
				lineTo(20.8f, 17.4f)
				lineTo(18.4f, 21f)
				lineTo(15.8f, 20f)
				moveTo(8.2f, 4f)
				lineTo(5.6f, 3f)
				lineTo(3.2f, 6.6f)
				lineTo(4.6f, 9f)
				moveTo(15.8f, 4f)
				lineTo(18.4f, 3f)
				lineTo(20.8f, 6.6f)
				lineTo(19.4f, 9f)
				moveTo(4.6f, 15f)
				lineTo(3.2f, 17.4f)
				lineTo(5.6f, 21f)
				lineTo(8.2f, 20f)
			}
		}.build()
	}
}
