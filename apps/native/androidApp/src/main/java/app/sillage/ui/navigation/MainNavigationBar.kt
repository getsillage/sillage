package app.sillage.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.sillage.R
import app.sillage.features.records.MemoViewMode
import app.sillage.ui.Screen
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel
import app.sillage.ui.designsystem.SillageNavigationBar
import app.sillage.ui.designsystem.SillageNavigationItem

@Composable
internal fun MainNavigationBar(state: SillageUiState, viewModel: SillageViewModel) {
    val enabled = !state.ask.variant.loading

    SillageNavigationBar {
        SillageNavigationItem(
            selected = state.clientContext.screen == Screen.Memos &&
                state.records.browse.viewMode == MemoViewMode.List,
            onClick = { viewModel.updateMemoViewMode(MemoViewMode.List) },
            enabled = enabled,
            icon = Icons.Rounded.Home,
            label = stringResource(R.string.nav_records),
        )
        SillageNavigationItem(
            selected = state.clientContext.screen == Screen.Memos &&
                state.records.browse.viewMode == MemoViewMode.Calendar,
            onClick = { viewModel.updateMemoViewMode(MemoViewMode.Calendar) },
            enabled = enabled,
            icon = Icons.Rounded.CalendarMonth,
            label = stringResource(R.string.nav_calendar),
        )
        SillageNavigationItem(
            selected = state.clientContext.screen == Screen.Ask,
            onClick = viewModel::openAsk,
            enabled = enabled,
            icon = Icons.Rounded.QuestionAnswer,
            label = stringResource(R.string.nav_ask),
        )
        SillageNavigationItem(
            selected = state.clientContext.screen == Screen.AISettings,
            onClick = viewModel::openAISettings,
            enabled = enabled,
            icon = Icons.Rounded.Settings,
            label = stringResource(R.string.nav_settings),
        )
    }
}
