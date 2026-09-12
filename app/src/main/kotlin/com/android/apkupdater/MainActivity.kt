package com.android.gupdater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.android.gupdater.ui.ApkUpdaterScreen
import com.android.gupdater.ui.ApkUpdaterViewModel
import com.android.gupdater.ui.theme.GUpdaterTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ApkUpdaterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GUpdaterTheme {
                ApkUpdaterScreen(viewModel = viewModel)
            }
        }
    }
}
