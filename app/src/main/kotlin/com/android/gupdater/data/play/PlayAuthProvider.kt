package com.android.gupdater.data.play

import android.content.Context
import com.android.gupdater.data.api.SharedHttpClient
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.helpers.AuthHelper
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.Properties

class PlayAuthProvider(private val context: Context) {

    private val preferences = context.getSharedPreferences("play_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun cached(): AuthData? = preferences.getString(KEY_AUTH_DATA, null)
        ?.let { stored -> runCatching { json.decodeFromString(AuthData.serializer(), stored) }.getOrNull() }

    fun create(): AuthData {
        val properties = PlayDeviceProperties.build(context)
        val credentials = requestAnonymousCredentials(properties)
        val authData = AuthHelper.build(
            email = credentials.first,
            token = credentials.second,
            tokenType = AuthHelper.Token.AUTH,
            isAnonymous = true,
            properties = properties,
            locale = Locale.getDefault()
        )
        preferences.edit()
            .putString(KEY_AUTH_DATA, json.encodeToString(AuthData.serializer(), authData))
            .apply()
        return authData
    }

    fun invalidate() = preferences.edit().remove(KEY_AUTH_DATA).apply()

    private fun requestAnonymousCredentials(properties: Properties): Pair<String, String> {
        val payload = JSONObject(
            properties.stringPropertyNames().associateWith(properties::getProperty)
        ).toString()

        val request = Request.Builder()
            .url(DISPENSER_URL)
            .header("User-Agent", userAgent())
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val body = SharedHttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException(dispenserError(response.code))
            response.body.string()
        }

        val credentials = JSONObject(body)
        val email = credentials.optString("email")
        val token = credentials.optString("authToken")
        if (email.isEmpty() || token.isEmpty()) {
            throw IllegalStateException("The anonymous account dispenser returned no credentials.")
        }
        return email to token
    }

    private fun userAgent(): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return "${context.packageName}-${packageInfo.longVersionCode}"
    }

    private fun dispenserError(code: Int): String = when (code) {
        403 -> "The anonymous account dispenser rejected this network. Turn off any VPN and retry."
        429 -> "The anonymous account dispenser is rate limiting this device. Retry later."
        in 500..599 -> "The anonymous account dispenser is unavailable ($code)."
        else -> "The anonymous account dispenser failed ($code)."
    }

    private companion object {
        const val DISPENSER_URL = "https://auroraoss.com/api/auth"
        const val KEY_AUTH_DATA = "auth_data"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
