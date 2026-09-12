package com.android.apkupdater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.android.apkupdater.ui.ApkUpdaterScreen
import com.android.apkupdater.ui.ApkUpdaterViewModel
import com.android.apkupdater.ui.theme.GUpdaterTheme

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
