package com.vnidrop.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import vnidrop.shared.generated.resources.Res

@Composable
internal fun EmptyStateAnimation(
	assetPath: String,
	modifier: Modifier = Modifier,
) {
	val composition by rememberLottieComposition {
		LottieCompositionSpec.JsonString(
			Res.readBytes(assetPath).decodeToString(),
		)
	}

	Image(
		painter = rememberLottiePainter(
			composition = composition,
			iterations = Compottie.IterateForever,
		),
		contentDescription = null,
		modifier = modifier,
		contentScale = ContentScale.Fit,
	)
}
