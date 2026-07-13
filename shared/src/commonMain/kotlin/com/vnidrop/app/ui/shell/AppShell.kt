package com.vnidrop.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vnidrop.app.ui.navigation.AppBottomNavigation
import com.vnidrop.app.ui.navigation.AppDestination
import com.vnidrop.app.ui.navigation.AppSidebarNavigation
import com.vnidrop.app.ui.state.WindowClass
import com.vnidrop.app.ui.state.useBottomNavigation
import com.vnidrop.app.ui.theme.LocalVniDropColors

@Composable
fun AppShell(
	modifier: Modifier = Modifier,
	selectedDestination: AppDestination,
	windowClass: WindowClass,
	mainContentTopStartRadius: Dp = 0.dp,
	onDestinationSelected: (AppDestination) -> Unit,
	overlay: @Composable BoxScope.() -> Unit = {},
	floatingAction: (@Composable BoxScope.() -> Unit)? = null,
	content: @Composable () -> Unit,
) {
	val colors = LocalVniDropColors.current
	Surface(
		modifier = modifier
			.fillMaxSize()
			.background(colors.backgroundDashCanvas),
		color = colors.backgroundDashCanvas,
	) {
		if (useBottomNavigation(windowClass)) {
			PhoneShell(
				selectedDestination = selectedDestination,
				onDestinationSelected = onDestinationSelected,
				overlay = overlay,
				floatingAction = floatingAction,
				content = content,
			)
		} else {
			WideShell(
				selectedDestination = selectedDestination,
				mainContentTopStartRadius = mainContentTopStartRadius,
				onDestinationSelected = onDestinationSelected,
				overlay = overlay,
				floatingAction = floatingAction,
				content = content,
			)
		}
	}
}

@Composable
private fun WideShell(
	selectedDestination: AppDestination,
	mainContentTopStartRadius: Dp,
	onDestinationSelected: (AppDestination) -> Unit,
	overlay: @Composable BoxScope.() -> Unit,
	floatingAction: (@Composable BoxScope.() -> Unit)?,
	content: @Composable () -> Unit,
) {
	val colors = LocalVniDropColors.current
	val roundedContent = mainContentTopStartRadius > 0.dp
	Row(
		modifier = Modifier
			.fillMaxSize()
			.then(if (roundedContent) Modifier.background(colors.backgroundSurface200) else Modifier),
	) {
		AppSidebarNavigation(
			selected = selectedDestination,
			dividerTopInset = mainContentTopStartRadius,
			onDestinationSelected = onDestinationSelected,
		)
		Box(
			modifier = Modifier
				.weight(1f)
				.fillMaxSize()
				.then(
					if (roundedContent) {
						Modifier
							.clip(RoundedCornerShape(topStart = mainContentTopStartRadius))
							.background(colors.backgroundDashCanvas)
					} else {
						Modifier
					},
				),
		) {
			content()
			floatingAction?.invoke(this)
			Box(Modifier.fillMaxSize().padding(bottom = if (floatingAction == null) 0.dp else 72.dp)) { overlay() }
		}
	}
}

@Composable
private fun PhoneShell(
	selectedDestination: AppDestination,
	onDestinationSelected: (AppDestination) -> Unit,
	overlay: @Composable BoxScope.() -> Unit,
	floatingAction: (@Composable BoxScope.() -> Unit)?,
	content: @Composable () -> Unit,
) {
	Column(modifier = Modifier.fillMaxSize()) {
		Box(modifier = Modifier.weight(1f).fillMaxSize()) {
			content()
			floatingAction?.invoke(this)
			Box(Modifier.fillMaxSize().padding(bottom = if (floatingAction == null) 0.dp else 72.dp)) { overlay() }
		}
		AppBottomNavigation(
			selected = selectedDestination,
			onDestinationSelected = onDestinationSelected,
		)
	}
}

@Composable
fun ScreenScrollContainer(
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit,
) {
	LazyColumn(
		modifier = modifier
			.fillMaxSize()
			.statusBarsPadding()
			.padding(16.dp),
		contentPadding = PaddingValues(bottom = 16.dp),
	) {
		item {
			content()
		}
	}
}
