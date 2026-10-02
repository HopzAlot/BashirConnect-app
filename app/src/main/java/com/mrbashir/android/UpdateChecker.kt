package com.mrbashir.android

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object UpdateChecker {

    private const val API_URL =
        "https://api.github.com/repos/HopzAlot/BashirConnect-app/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // longer for the actual APK download
        .build()

    data class UpdateInfo(val tagName: String, val downloadUrl: String)

    /**
     * Checks GitHub for a newer release. Returns [UpdateInfo] if one exists, null otherwise.
     * Never throws — silently returns null on any error.
     */
    suspend fun check(currentVersionName: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val body = client.newCall(request).execute().use { it.body?.string() }
                ?: return@withContext null
            val json = JSONObject(body)
            val tagName = json.getString("tag_name")           // e.g. "v1.42"
            val latestVersion = tagName.trimStart('v')          // "1.42"
            val currentVersion = currentVersionName.trimStart('v')

            if (latestVersion == currentVersion) return@withContext null

            // Prefer the first .apk asset; fall back to the releases page
            val assets = json.getJSONArray("assets")
            val downloadUrl = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }
                ?.getString("browser_download_url")
                ?: "https://github.com/HopzAlot/BashirConnect-app/releases/latest"

            UpdateInfo(tagName, downloadUrl)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Downloads the APK from [url] into internal storage, reporting progress via [onProgress]
     * (0–100), then fires the system package installer.
     *
     * Call from a coroutine — runs on IO dispatcher internally.
     */
    suspend fun downloadAndInstall(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body ?: return@withContext

        val contentLength = body.contentLength()
        val updateDir = File(context.filesDir, "updates").also { it.mkdirs() }
        val apkFile = File(updateDir, "update.apk")

        body.byteStream().use { input ->
            FileOutputStream(apkFile).use { output ->
                val buffer = ByteArray(8_192)
                var downloaded = 0L
                var bytes: Int
                while (input.read(buffer).also { bytes = it } != -1) {
                    output.write(buffer, 0, bytes)
                    downloaded += bytes
                    if (contentLength > 0) {
                        onProgress((downloaded * 100 / contentLength).toInt())
                    }
                }
            }
        }

        // Hand the APK to the system installer — no browser needed
        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
