package com.mrbashir.android

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Direct port of the Python persistent_login_loop's HTTP logic:
 *   1. GET a plain-HTTP canary URL (neverssl.com) that has no HTTPS to
 *      redirect through, so a captive portal can transparently hijack it.
 *   2. If the final URL after redirects contains "fgtauth", we're behind
 *      the Fortinet portal — pull the "magic" token from the query string.
 *   3. POST username/password/magic back to the portal's base URL.
 *
 * Critical fix vs. the original desktop script: on a phone with both
 * Wi-Fi and mobile data active, Android's default network routing can
 * send this request over cellular even while we're actively watching
 * the Wi-Fi network — so a plain requests.Session() (or an unbound
 * OkHttpClient) gets a real 200 from neverssl.com and wrongly concludes
 * "already online" while the Wi-Fi is still stuck behind the portal.
 * That's exactly the "had to turn mobile data off and back on" bug.
 * Binding BOTH the socket (via socketFactory) AND DNS resolution (via
 * a custom Dns backed by network.getAllByName) to the specific captive
 * Network object closes that gap — every part of the request, including
 * the hostname lookup, is forced through the Wi-Fi network we're
 * actually trying to log into, regardless of what the OS would pick by
 * default.
 */
class PortalLoginClient(network: Network) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .socketFactory(network.socketFactory)
        .dns(NetworkBoundDns(network))
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private class NetworkBoundDns(private val network: Network) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            // Resolves via this network's own DNS servers, not whichever
            // network Android would otherwise pick as "default".
            return network.getAllByName(hostname).toList()
        }
    }

    sealed class Result {
        data class AlreadyOnline(val message: String) : Result()
        data class LoggedIn(val message: String) : Result()
        data class NoPortalYet(val message: String) : Result()
        data class Failure(val error: String) : Result()
    }

    suspend fun attemptLogin(username: String, password: String): Result =
        withContext(Dispatchers.IO) {
            try {
                val canaryRequest = Request.Builder()
                    .url("http://neverssl.com")
                    .build()

                client.newCall(canaryRequest).execute().use { response ->
                    val finalUrl = response.request.url.toString()

                    when {
                        finalUrl.contains("fgtauth") -> {
                            val magicToken = finalUrl.substringAfterLast('?')
                            val basePostUrl = finalUrl.substringBefore("fgtauth")

                            val formBody = FormBody.Builder()
                                .add("username", username)
                                .add("password", password)
                                .add("magic", magicToken)
                                .build()

                            val loginRequest = Request.Builder()
                                .url(basePostUrl)
                                .post(formBody)
                                .build()

                            client.newCall(loginRequest).execute().use { loginResponse ->
                                if (loginResponse.isSuccessful) {
                                    Result.LoggedIn("Portal accepted credentials (HTTP ${loginResponse.code})")
                                } else {
                                    Result.Failure("Portal rejected login (HTTP ${loginResponse.code})")
                                }
                            }
                        }

                        finalUrl.contains("neverssl.com") -> {
                            Result.AlreadyOnline("Already online, no portal in the way")
                        }

                        else -> {
                            Result.NoPortalYet("Unexpected redirect target: $finalUrl")
                        }
                    }
                }
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Unknown network error")
            }
        }
}
