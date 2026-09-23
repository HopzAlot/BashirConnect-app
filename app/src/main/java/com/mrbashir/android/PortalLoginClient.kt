package com.mrbashir.android

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Direct port of the Python persistent_login_loop's HTTP logic:
 *   1. GET a plain-HTTP canary URL (httpforever.com) that has no HTTPS to
 *      redirect through, so a captive portal can transparently hijack it.
 *   1. GET the standard Android captive-portal canary URL
 *      (connectivitycheck.gstatic.com/generate_204). Fortinet Fortigate
 *      devices are explicitly configured to intercept this URL and redirect
 *      unauthenticated clients to their login page, making it more reliable
 *      than httpforever.com.
 *   2. If the final URL after redirects contains "fgtauth", we're behind
 *      the Fortinet portal — pull the "magic" token from the query string.
 *   3. POST username/password/magic back to the portal's base URL.
 *   4. HTTP 204 response (no redirect) means already online.
 *
 * Everything is bound to a *specific* Network object — both the socket
 * (socketFactory) AND DNS resolution (custom Dns via
 * network.getAllByName) — so this always talks to the network we're
 * actually trying to log into, regardless of mobile data or whatever
 * network Android would otherwise pick as "default".
 *
 * A browser-like User-Agent is sent because some Fortigate configurations
 * only redirect requests that look like they come from a browser.
 */
class PortalLoginClient(network: Network) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .socketFactory(network.socketFactory)
        .dns(NetworkBoundDns(network))
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private class NetworkBoundDns(private val network: Network) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
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
                // connectivitycheck.gstatic.com/generate_204 returns HTTP 204
                // (No Content) when there is a real internet connection.
                // Captive portals redirect it to their login page instead.
                val canaryRequest = Request.Builder()
                    .url("http://httpforever.com")
                    .url("http://connectivitycheck.gstatic.com/generate_204")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    )
                    .build()

                client.newCall(canaryRequest).execute().use { response ->
                    val finalUrl = response.request.url.toString()

                    when {
                        finalUrl.contains("fgtauth") -> {
                            // Fortinet captive portal — extract the magic token and login.
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

                        finalUrl.contains("httpforever.com") -> {
                        response.code == 204 -> {
                            // Standard "you're online" response — no portal in the way.
                            Result.AlreadyOnline("Already online (HTTP 204, no portal)")
                        }

                        finalUrl.contains("connectivitycheck") -> {
                            // Reached the canary without a redirect but got an unexpected
                            // status — treat as online (portal would have redirected us).
                            Result.AlreadyOnline("Already online, no portal in the way")
                        }

                        else -> {
                            Result.NoPortalYet("Unexpected redirect target: $finalUrl")
                        }
                    }
                }
            } catch (e: UnknownHostException) {
                Result.Failure("DNS lookup failed for httpforever.com on this network")
                Result.Failure("DNS lookup failed for connectivitycheck.gstatic.com on this network")
            } catch (e: SocketTimeoutException) {
                Result.Failure("Connection to httpforever.com timed out (network may still be settling)")
                Result.Failure("Connection timed out (network may still be settling)")
            } catch (e: Exception) {
                Result.Failure(e.message ?: "Unknown network error")
            }
        }
}