package com.mrbashir.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object UpdateChecker {

    private const val API_URL =
        "https://api.github.com/repos/HopzAlot/BashirConnect-app/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    data class UpdateInfo(val tagName: String, val downloadUrl: String)

    /**
     * Returns [UpdateInfo] if a newer release exists, null otherwise (including on errors).
     * Comparison is by tag name string — if tag differs from current version, we flag an update.
     */
    suspend fun check(currentVersionName: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val body = client.newCall(request).execute().use { it.body?.string() } ?: return@withContext null
            val json = JSONObject(body)
            val tagName = json.getString("tag_name")          // e.g. "v1.42"
            val latestVersion = tagName.trimStart('v')         // "1.42"
            val currentVersion = currentVersionName.trimStart('v') // "1.41"

            if (latestVersion == currentVersion) return@withContext null

            // Pick the first .apk asset, fall back to the release HTML page
            val assets = json.getJSONArray("assets")
            val downloadUrl = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }
                ?.getString("browser_download_url")
                ?: "https://github.com/HopzAlot/BashirConnect-app/releases/latest"

            UpdateInfo(tagName, downloadUrl)
        } catch (_: Exception) {
            null // silently fail — network unavailable, API down, etc.
        }
    }
}
