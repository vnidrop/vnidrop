package com.vnidrop.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vnidrop.app.ui.theme.LocalVniDropColors
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppSidebarNavigation(
	selected: AppDestination,
	onDestinationSelected: (AppDestination) -> Unit,
	dividerTopInset: Dp = 0.dp,
	modifier: Modifier = Modifier,
) {
	val colors = LocalVniDropColors.current
	Box(
		modifier = modifier
			.width(88.dp)
			.fillMaxHeight()
			.background(colors.backgroundSurface200),
	) {
		Column(
			modifier = Modifier
				.fillMaxHeight()
				.padding(vertical = 10.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(2.dp),
		) {
			primaryNavigationItems.forEach { item ->
				SidebarNavigationItem(
					item = item,
					selected = item.destination == selected,
					onClick = { onDestinationSelected(item.destination) },
				)
			}
		}
		Box(
			modifier = Modifier
				.align(Alignment.TopEnd)
				.padding(top = dividerTopInset)
				.width(1.dp)
				.fillMaxHeight()
				.background(colors.borderDefault),
		)
	}
}

@Composable
fun AppBottomNavigation(
	selected: AppDestination,
	onDestinationSelected: (AppDestination) -> Unit,
	modifier: Modifier = Modifier,
) {
	val colors = LocalVniDropColors.current
	Column(
		modifier = modifier
			.fillMaxWidth()
			.background(colors.backgroundSurface200),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(64.dp)
				.padding(horizontal = 8.dp, vertical = 4.dp),
			horizontalArrangement = Arrangement.spacedBy(4.dp),
		) {
			primaryNavigationItems.forEach { item ->
				BottomNavigationItem(
					item = item,
					selected = item.destination == selected,
					onClick = { onDestinationSelected(item.destination) },
					modifier = Modifier.weight(1f),
				)
			}
		}
		Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
	}
}

@Composable
private fun SidebarNavigationItem(
	item: NavigationItem,
	selected: Boolean,
	onClick: () -> Unit,
) {
	val colors = LocalVniDropColors.current
	val foreground = if (selected) colors.brandLink else colors.foregroundLight
	val label = stringResource(item.label)
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.selectable(selected = selected, onClick = onClick)
			.padding(vertical = 13.dp),
	) {
		Column(
			modifier = Modifier.align(Alignment.Center),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(5.dp),
		) {
			Icon(imageVector = item.icon, contentDescription = label, tint = foreground, modifier = Modifier.size(24.dp))
			Text(
				text = label,
				color = foreground,
				style = MaterialTheme.typography.labelSmall,
				fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				textAlign = TextAlign.Center,
			)
		}
	}
}

@Composable
private fun BottomNavigationItem(
	item: NavigationItem,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val colors = LocalVniDropColors.current
	val foreground = if (selected) colors.brandLink else colors.foregroundLight
	val label = stringResource(item.label)
	Column(
		modifier = modifier
			.clip(RoundedCornerShape(12.dp))
			.selectable(selected = selected, onClick = onClick)
			.fillMaxHeight()
			.padding(horizontal = 8.dp, vertical = 4.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
	) {
		Icon(imageVector = item.icon, contentDescription = label, tint = foreground, modifier = Modifier.size(24.dp))
		Text(
			text = label,
			color = foreground,
			style = MaterialTheme.typography.labelSmall,
			fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
	}
}
