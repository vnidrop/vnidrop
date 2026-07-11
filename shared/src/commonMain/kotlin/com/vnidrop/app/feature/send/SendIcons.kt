package com.vnidrop.app.feature.send

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object SendIcons {
	val Plus = lineIcon("Plus") {
		moveTo(12f, 5f)
		lineTo(12f, 19f)
		moveTo(5f, 12f)
		lineTo(19f, 12f)
	}
	val File = lineIcon("File") {
		moveTo(14f, 2f)
		lineTo(6f, 2f)
		lineTo(6f, 22f)
		lineTo(18f, 22f)
		lineTo(18f, 6f)
		close()
		moveTo(14f, 2f)
		lineTo(14f, 6f)
		lineTo(18f, 6f)
	}
	val Back = lineIcon("Back") {
		moveTo(19f, 12f)
		lineTo(5f, 12f)
		moveTo(12f, 19f)
		lineTo(5f, 12f)
		lineTo(12f, 5f)
	}
	val Delete = lineIcon("Delete") {
		moveTo(4f, 7f); lineTo(20f, 7f)
		moveTo(9f, 7f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 7f)
		moveTo(6f, 7f); lineTo(7f, 21f); lineTo(17f, 21f); lineTo(18f, 7f)
		moveTo(10f, 11f); lineTo(10f, 17f)
		moveTo(14f, 11f); lineTo(14f, 17f)
	}
	val ChevronRight = lineIcon("ChevronRight") {
		moveTo(9f, 18f)
		lineTo(15f, 12f)
		lineTo(9f, 6f)
	}
	val Shield = lineIcon("Shield") {
		moveTo(12f, 2f)
		lineTo(20f, 6f)
		lineTo(20f, 12f)
		arcTo(9f, 9f, 0f, false, true, 12f, 22f)
		arcTo(9f, 9f, 0f, false, true, 4f, 12f)
		lineTo(4f, 6f)
		close()
	}
	val Globe = lineIcon("Globe") {
		moveTo(21f, 12f)
		arcTo(9f, 9f, 0f, true, true, 3f, 12f)
		arcTo(9f, 9f, 0f, true, true, 21f, 12f)
		moveTo(3f, 12f)
		lineTo(21f, 12f)
		moveTo(12f, 3f)
		arcTo(14f, 14f, 0f, false, true, 12f, 21f)
		arcTo(14f, 14f, 0f, false, true, 12f, 3f)
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
