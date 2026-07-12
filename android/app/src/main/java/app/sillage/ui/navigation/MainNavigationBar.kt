package app.sillage.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sillage.ui.MemoViewMode
import app.sillage.ui.Screen
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel
import app.sillage.R

private val NavigationContentHeight = 60.dp
private val NavigationIndicatorWidth = 40.dp
private val NavigationIndicatorHeight = 26.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainNavigationBar(state: SillageUiState, viewModel: SillageViewModel) {
    val enabled = !state.askVariantLoading

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                    .height(NavigationContentHeight)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MainNavigationItem(
                    selected = state.screen == Screen.Memos && state.memoViewMode == MemoViewMode.List,
                    onClick = { viewModel.updateMemoViewMode(MemoViewMode.List) },
                    enabled = enabled,
                    icon = Icons.Rounded.Home,
                    label = stringResource(R.string.nav_records),
                )
                MainNavigationItem(
                    selected = state.screen == Screen.Memos && state.memoViewMode == MemoViewMode.Calendar,
                    onClick = { viewModel.updateMemoViewMode(MemoViewMode.Calendar) },
                    enabled = enabled,
                    icon = Icons.Rounded.CalendarMonth,
                    label = stringResource(R.string.nav_calendar),
                )
                MainNavigationItem(
                    selected = state.screen == Screen.Ask,
                    onClick = viewModel::openAsk,
                    enabled = enabled,
                    icon = Icons.Rounded.QuestionAnswer,
                    label = stringResource(R.string.nav_ask),
                )
                MainNavigationItem(
                    selected = state.screen == Screen.AISettings,
                    onClick = viewModel::openAISettings,
                    enabled = enabled,
                    icon = Icons.Rounded.Settings,
                    label = stringResource(R.string.nav_settings),
                )
            }
        }
    }
}

@Composable
private fun RowScope.MainNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    icon: ImageVector,
    label: String,
) {
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val iconColor by animateColorAsState(
        targetValue = when {
            !enabled -> disabledColor
            selected -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "navigationIconColor",
    )
    val labelColor by animateColorAsState(
        targetValue = when {
            !enabled -> disabledColor
            selected -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "navigationLabelColor",
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (enabled) 1f else 0.38f)
        } else {
            Color.Transparent
        },
        label = "navigationIndicatorColor",
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                role = Role.Tab,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(NavigationIndicatorWidth)
                .height(NavigationIndicatorHeight)
                .background(indicatorColor, RoundedCornerShape(NavigationIndicatorHeight / 2)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconColor,
            )
        }
        Text(
            text = label,
            modifier = Modifier.padding(top = 2.dp),
            color = labelColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                letterSpacing = 0.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
