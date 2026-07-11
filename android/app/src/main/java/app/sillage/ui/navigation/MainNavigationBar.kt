package app.sillage.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.sillage.ui.MemoViewMode
import app.sillage.ui.Screen
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainNavigationBar(state: SillageUiState, viewModel: SillageViewModel) {
    NavigationBar {
        NavigationBarItem(
            selected = state.screen == Screen.Memos && state.memoViewMode == MemoViewMode.List,
            onClick = { viewModel.updateMemoViewMode(MemoViewMode.List) },
            enabled = !state.askVariantLoading,
            icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
            label = { Text("记录") },
        )
        NavigationBarItem(
            selected = state.screen == Screen.Memos && state.memoViewMode == MemoViewMode.Calendar,
            onClick = { viewModel.updateMemoViewMode(MemoViewMode.Calendar) },
            enabled = !state.askVariantLoading,
            icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
            label = { Text("日历") },
        )
        NavigationBarItem(
            selected = state.screen == Screen.Ask,
            onClick = viewModel::openAsk,
            enabled = !state.askVariantLoading,
            icon = { Icon(Icons.Rounded.QuestionAnswer, contentDescription = null) },
            label = { Text("问答") },
        )
        NavigationBarItem(
            selected = state.screen == Screen.AISettings,
            onClick = viewModel::openAISettings,
            enabled = !state.askVariantLoading,
            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
            label = { Text("设置") },
        )
    }
}
