package com.android.gupdater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.android.gupdater.ui.GUpdaterScreen
import com.android.gupdater.ui.GUpdaterViewModel
import com.android.gupdater.ui.theme.GUpdaterTheme

class MainActivity : ComponentActivity() {
    private val viewModel: GUpdaterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GUpdaterTheme {
                GUpdaterScreen(viewModel = viewModel)
            }
        }
    }
}
