package com.miofelix.sillage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.miofelix.sillage.data.SessionStore
import com.miofelix.sillage.ui.SillageApp
import com.miofelix.sillage.ui.SillageViewModel
import com.miofelix.sillage.ui.theme.SillageTheme

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
