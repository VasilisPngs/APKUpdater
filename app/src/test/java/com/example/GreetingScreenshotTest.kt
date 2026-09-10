package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.AppUpdateInfo
import com.example.ui.UpdateAppCard
import com.example.ui.theme.ApkUpdaterTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appUpdateCardScreenshot() {
        composeTestRule.setContent {
            ApkUpdaterTheme {
                UpdateAppCard(
                    update = AppUpdateInfo(
                        packageName = "com.spotify.music",
                        appName = "Spotify",
                        currentVersionName = "8.9.20.589",
                        currentVersionCode = 11223344L,
                        newVersionName = "8.9.30.600",
                        newVersionCode = 11223355L,
                        publishDate = "Today",
                        whatsNew = "Bug fixes and performance improvements",
                        apkMirrorUrl = "https://www.apkmirror.com/apk/spotify-ab/spotify-music/",
                        architectures = listOf("arm64-v8a", "universal"),
                        isSystemApp = false
                    ),
                    onDownloadClick = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/app_update_card.png")
    }
}
