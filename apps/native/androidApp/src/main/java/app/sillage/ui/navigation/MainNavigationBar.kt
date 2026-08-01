package app.sillage.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.sillage.R
import app.sillage.features.records.MemoViewMode
import app.sillage.ui.Screen
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel
import app.sillage.ui.designsystem.SillageNavigationItem

private val NavigationContentHeight = 60.dp

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
                SillageNavigationItem(
                    selected = state.screen == Screen.Memos &&
                        state.memoViewMode == MemoViewMode.List,
                    onClick = { viewModel.updateMemoViewMode(MemoViewMode.List) },
                    enabled = enabled,
                    icon = Icons.Rounded.Home,
                    label = stringResource(R.string.nav_records),
                )
                SillageNavigationItem(
                    selected = state.screen == Screen.Memos &&
                        state.memoViewMode == MemoViewMode.Calendar,
                    onClick = { viewModel.updateMemoViewMode(MemoViewMode.Calendar) },
                    enabled = enabled,
                    icon = Icons.Rounded.CalendarMonth,
                    label = stringResource(R.string.nav_calendar),
                )
                SillageNavigationItem(
                    selected = state.screen == Screen.Ask,
                    onClick = viewModel::openAsk,
                    enabled = enabled,
                    icon = Icons.Rounded.QuestionAnswer,
                    label = stringResource(R.string.nav_ask),
                )
                SillageNavigationItem(
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
