package com.miofelix.sillage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
            SillageTheme {
                SillageApp(viewModel = viewModel)
            }
        }
    }
}
