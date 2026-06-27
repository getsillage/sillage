package app.sillage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.sillage.data.SessionStore
import app.sillage.ui.SillageApp
import app.sillage.ui.SillageViewModel
import app.sillage.ui.theme.SillageTheme

class MainActivity : ComponentActivity() {
    private val viewModel: SillageViewModel by viewModels {
        SillageViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsState()
            SillageTheme(darkTheme = state.themeMode == SessionStore.THEME_DARK) {
                SillageApp(viewModel = viewModel)
            }
        }
    }
}
