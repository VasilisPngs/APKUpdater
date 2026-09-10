package com.example

import com.example.data.model.AppExistsApk
import com.example.data.model.AppExistsRelease
import com.example.data.model.AppExistsResponseData
import com.example.data.model.InstalledApp
import com.example.data.repository.AppUpdateRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StableReleaseFilterTest {

  @Test
  fun `filters out beta and alpha releases when onlyStable is true`() {
    val context = RuntimeEnvironment.getApplication()
    val repo = AppUpdateRepository(context)

    // Test reflection access to parseUpdates
    val method = AppUpdateRepository::class.java.getDeclaredMethod(
      "parseUpdates",
      List::class.java,
      List::class.java,
      Boolean::class.javaPrimitiveType
    )
    method.isAccessible = true

    val installed = listOf(
      InstalledApp("com.example.app", "Test App", "1.0.0", 10, false)
    )

    // 1. Case with beta release
    val betaResponse = listOf(
      AppExistsResponseData(
        pname = "com.example.app",
        exists = true,
        release = AppExistsRelease(version = "1.1.0-beta01", link = "/apk/test-beta/"),
        apks = listOf(
          AppExistsApk(versionCode = 20, link = "/apk/test-beta/download")
        )
      )
    )

    @Suppress("UNCHECKED_CAST")
    val resultBeta = method.invoke(repo, betaResponse, installed, true) as List<*>
    assertTrue("Beta update must be excluded in stable mode", resultBeta.isEmpty())

    // 2. Case with stable release
    val stableResponse = listOf(
      AppExistsResponseData(
        pname = "com.example.app",
        exists = true,
        release = AppExistsRelease(version = "1.1.0", link = "/apk/test-release/"),
        apks = listOf(
          AppExistsApk(versionCode = 20, link = "/apk/test-release/download")
        )
      )
    )

    @Suppress("UNCHECKED_CAST")
    val resultStable = method.invoke(repo, stableResponse, installed, true) as List<*>
    assertEquals("Stable update must be accepted", 1, resultStable.size)
  }
}
