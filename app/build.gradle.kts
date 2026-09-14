plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.android.apkupdater"
    compileSdkPreview = "CANARY"

    defaultConfig {
        applicationId = "com.android.apkupdater"
        minSdk = 36
        targetSdk = 37
        versionCode = (System.currentTimeMillis() / 1000).toInt()
        versionName = "APKUpdater"
    }

    val releaseKeystore = rootProject.file("release.keystore")

    signingConfigs {
        create("release") {
            storeFile = releaseKeystore
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: "release"
            keyPassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_26
        targetCompatibility = JavaVersion.VERSION_26
    }

    buildFeatures {
        compose = true
    }

    lint {
        toolchain.languageVersion.set(JavaLanguageVersion.of(26))
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
}
