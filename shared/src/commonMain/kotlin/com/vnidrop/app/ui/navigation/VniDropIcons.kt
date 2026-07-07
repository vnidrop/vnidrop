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
				moveTo(12f, 17f)
				lineTo(12f, 3f)
				moveTo(6f, 11f)
				lineTo(12f, 17f)
				lineTo(18f, 11f)
				moveTo(19f, 21f)
				lineTo(5f, 21f)
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
				moveTo(9.671f, 4.136f)
				arcToRelative(2.34f, 2.34f, 0f, false, true, 4.659f, 0f)
				arcToRelative(2.34f, 2.34f, 0f, false, false, 3.319f, 1.915f)
				arcToRelative(2.34f, 2.34f, 0f, false, true, 2.33f, 4.033f)
				arcToRelative(2.34f, 2.34f, 0f, false, false, 0f, 3.831f)
				arcToRelative(2.34f, 2.34f, 0f, false, true, -2.33f, 4.033f)
				arcToRelative(2.34f, 2.34f, 0f, false, false, -3.319f, 1.915f)
				arcToRelative(2.34f, 2.34f, 0f, false, true, -4.659f, 0f)
				arcToRelative(2.34f, 2.34f, 0f, false, false, -3.32f, -1.915f)
				arcToRelative(2.34f, 2.34f, 0f, false, true, -2.33f, -4.033f)
				arcToRelative(2.34f, 2.34f, 0f, false, false, 0f, -3.831f)
				arcTo(2.34f, 2.34f, 0f, false, true, 6.35f, 6.051f)
				arcToRelative(2.34f, 2.34f, 0f, false, false, 3.319f, -1.915f)
				moveTo(12f, 15f)
				arcTo(3f, 3f, 0f, false, true, 12f, 9f)
				arcTo(3f, 3f, 0f, false, true, 12f, 15f)
			}
		}.build()
	}
}
