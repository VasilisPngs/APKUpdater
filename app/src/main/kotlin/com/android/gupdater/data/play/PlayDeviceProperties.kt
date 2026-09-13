package com.android.gupdater.data.play

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLDisplay
import android.opengl.GLES20
import android.os.Build
import java.util.Properties

object PlayDeviceProperties {

    private const val GSF_VERSION = "203019037"
    private const val VENDING_VERSION = "82151710"
    private const val VENDING_VERSION_STRING = "21.5.17-21 [0] [PR] 326734551"

    fun build(context: Context): Properties {
        val configuration = context.resources.configuration
        val metrics = context.resources.displayMetrics
        val packageManager = context.packageManager
        val activityManager = context.getSystemService(ActivityManager::class.java)

        return Properties().apply {
            setProperty("UserReadableName", "${Build.MANUFACTURER} ${Build.MODEL}")
            setProperty("Build.HARDWARE", Build.HARDWARE)
            setProperty("Build.RADIO", Build.getRadioVersion() ?: "unknown")
            setProperty("Build.BOOTLOADER", Build.BOOTLOADER)
            setProperty("Build.FINGERPRINT", Build.FINGERPRINT)
            setProperty("Build.BRAND", Build.BRAND)
            setProperty("Build.DEVICE", Build.DEVICE)
            setProperty("Build.VERSION.SDK_INT", Build.VERSION.SDK_INT.toString())
            setProperty("Build.VERSION.RELEASE", Build.VERSION.RELEASE)
            setProperty("Build.MODEL", Build.MODEL)
            setProperty("Build.MANUFACTURER", Build.MANUFACTURER)
            setProperty("Build.PRODUCT", Build.PRODUCT)
            setProperty("Build.ID", Build.ID)

            setProperty("TouchScreen", configuration.touchscreen.toString())
            setProperty("Keyboard", configuration.keyboard.toString())
            setProperty("Navigation", configuration.navigation.toString())
            setProperty(
                "ScreenLayout",
                (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK).toString()
            )
            setProperty(
                "HasHardKeyboard",
                (configuration.keyboard == Configuration.KEYBOARD_QWERTY).toString()
            )
            setProperty(
                "HasFiveWayNavigation",
                (configuration.navigation == Configuration.NAVIGATION_DPAD).toString()
            )

            setProperty("Screen.Density", metrics.densityDpi.toString())
            setProperty("Screen.Width", metrics.widthPixels.toString())
            setProperty("Screen.Height", metrics.heightPixels.toString())

            setProperty("Platforms", Build.SUPPORTED_ABIS.joinToString(separator = ","))
            setProperty(
                "Features",
                packageManager.systemAvailableFeatures.mapNotNull { it.name }
                    .joinToString(separator = ",")
            )
            setProperty(
                "SharedLibraries",
                packageManager.systemSharedLibraryNames.orEmpty().joinToString(separator = ",")
            )
            setProperty("Locales", locales(context).joinToString(separator = ","))

            setProperty(
                "GL.Version",
                (activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0).toString()
            )
            setProperty("GL.Extensions", glExtensions().joinToString(separator = ","))

            setProperty("Client", "android-google")
            setProperty("GSF.version", GSF_VERSION)
            setProperty("Vending.version", VENDING_VERSION)
            setProperty("Vending.versionString", VENDING_VERSION_STRING)

            setProperty("Roaming", "mobile-notroaming")
            setProperty("TimeZone", "UTC-10")
            setProperty("CellOperator", "310")
            setProperty("SimOperator", "38")
        }
    }

    private fun locales(context: Context): List<String> {
        val configured = context.resources.configuration.locales
        val preferred = (0 until configured.size()).map { configured.get(it).toString() }
        val supported = context.assets.locales.map { it.replace('-', '_') }
        return (preferred + supported).filter(String::isNotBlank).distinct()
    }

    private fun glExtensions(): List<String> {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return emptyList()

        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return emptyList()

        return try {
            val configs = arrayOfNulls<EGLConfig>(1)
            val configCount = IntArray(1)
            val configAttributes = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val hasConfig = EGL14.eglChooseConfig(
                display, configAttributes, 0, configs, 0, 1, configCount, 0
            )
            if (!hasConfig || configCount[0] == 0) return emptyList()

            readExtensions(display, configs[0]!!)
        } finally {
            EGL14.eglTerminate(display)
        }
    }

    private fun readExtensions(display: EGLDisplay, config: EGLConfig): List<String> {
        val context = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0
        )
        if (context == EGL14.EGL_NO_CONTEXT) return emptyList()

        val surface = EGL14.eglCreatePbufferSurface(
            display,
            config,
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE),
            0
        )
        if (surface == EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroyContext(display, context)
            return emptyList()
        }

        return try {
            EGL14.eglMakeCurrent(display, surface, surface, context)
            GLES20.glGetString(GLES20.GL_EXTENSIONS)
                .orEmpty()
                .split(' ')
                .filter(String::isNotBlank)
                .distinct()
                .sorted()
        } finally {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
        }
    }
}
