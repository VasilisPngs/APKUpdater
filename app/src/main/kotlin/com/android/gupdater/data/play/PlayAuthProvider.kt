package com.android.gupdater.data.play

import android.content.Context
import com.android.gupdater.data.api.SharedHttpClient
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.helpers.AuthHelper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.UnknownHostException
import java.util.Locale
import java.util.Properties

class PlayAuthProvider(private val context: Context) {

    private var session: AuthData? = null

    @Synchronized
    fun session(): AuthData = session ?: create()

    @Synchronized
    fun renew(stale: AuthData): AuthData = session.takeIf { it !== stale } ?: create()

    private fun create(): AuthData {
        val properties = PlayDeviceProperties.build(context)
        val credentials = requestAnonymousCredentials(properties)
        return AuthHelper.build(
            email = credentials.first,
            token = credentials.second,
            tokenType = AuthHelper.Token.AUTH,
            isAnonymous = true,
            properties = properties,
            locale = Locale.getDefault()
        ).also { session = it }
    }

    private fun requestAnonymousCredentials(properties: Properties): Pair<String, String> {
        val payload = JSONObject(
            properties.stringPropertyNames().associateWith(properties::getProperty)
        ).toString()

        val request = Request.Builder()
            .url(DISPENSER_URL)
            .header("User-Agent", USER_AGENT)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val body = try {
            SharedHttpClient.instance.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException(dispenserError(response.code))
                response.body.string()
            }
        } catch (exception: UnknownHostException) {
            throw IllegalStateException(
                "This device could not resolve $DISPENSER_HOST. A VPN, private DNS or content blocker is filtering it.",
                exception
            )
        }

        val credentials = JSONObject(body)
        val email = credentials.optString("email")
        val token = credentials.optString("authToken")
        if (email.isEmpty() || token.isEmpty()) {
            throw IllegalStateException("The account dispenser returned no credentials.")
        }
        return email to token
    }

    private fun dispenserError(code: Int): String = when (code) {
        400 -> "The account dispenser rejected the device configuration."
        403 -> "The account dispenser refused this network."
        429 -> "The account dispenser is rate limiting this network. Retry in ten minutes."
        in 500..599 -> "The account dispenser is unavailable ($code)."
        else -> "The account dispenser failed ($code)."
    }

    private companion object {
        const val USER_AGENT = "GUpdater"
        const val DISPENSER_HOST = "auroraoss.com"
        const val DISPENSER_URL = "https://auroraoss.com/api/auth/"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
