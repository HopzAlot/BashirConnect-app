package com.mrbashir.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var credentialStore: CredentialStore

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op either way, service still runs */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Install FIRST — catches any exception that follows, including
        // EncryptedSharedPreferences crashes, Compose init failures, etc.
        CrashReporter.install(this)

        credentialStore = CredentialStore(this)
        requestNotificationPermissionIfNeeded()

        setContent {
            MaterialTheme(typography = MrBashirTypography) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Check for a crash saved from the previous run and show it
                    // in a dialog so you can read it without USB / Logcat.
                    val pendingCrash = remember {
                        CrashReporter.consumePendingCrash(this@MainActivity)
                    }
                    if (pendingCrash != null) {
                        CrashDialog(trace = pendingCrash)
                    }

                    MrBashirScreen(
                        credentialStore = credentialStore,
                        onSave = { username, password ->
                            credentialStore.save(username, password)
                            CaptivePortalService.start(this)
                        },
                        onStart = { CaptivePortalService.start(this) },
                        onStop = { CaptivePortalService.stop(this) },
                        onForget = {
                            credentialStore.clear()
                            CaptivePortalService.stop(this)
                        }
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun MrBashirScreen(
    credentialStore: CredentialStore,
    onSave: (String, String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onForget: () -> Unit
) {
    val context = LocalContext.current
    var hasCredentials by remember { mutableStateOf(credentialStore.hasCredentials()) }
    val statsStore = remember { StatsStore(context) }
    var isRunning by remember { mutableStateOf(statsStore.isServiceEnabled()) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var activityExpanded by remember { mutableStateOf(false) }

    // Streak is elapsed time, so it needs to keep ticking while the
    // screen is open, not just refresh on external events.
    LaunchedEffect(Unit) {
        while (true) {
            AppStats.update(statsStore.currentStats())
            kotlinx.coroutines.delay(60_000)
        }
    }
    val stats by AppStats.stats.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val state by AppStatus.state.collectAsState()
    val log by AppStatus.log.collectAsState()

    val (heroTitle, heroSubtitle) = when {
        !hasCredentials -> "Not set up yet" to "Enter your details once and Mr. Bashir takes it from here"
        state == ConnectionState.LOGGED_IN -> "Logged in!" to "Mr. Bashir handled it while you weren't looking"
        !isRunning -> "Paused" to "Mr. Bashir won't auto-login until you start again"
        else -> "Watching" to "Mr. Bashir is keeping an eye on your networks"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("BashirConnect", style = MaterialTheme.typography.headlineMedium)
            }

            Spacer(Modifier.height(16.dp))

            // Hero card: mascot + live status
            Surface(
                color = Color(0xFFEAF3DE),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                ) {
                    AnimatedMascot()
                    Spacer(Modifier.height(8.dp))
                    Text(heroTitle, style = MaterialTheme.typography.titleMedium)
                    Text(
                        heroSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Stat row — only meaningful once Mr. Bashir has actually run
            if (stats.streakMs > 0 || stats.loginsToday > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard(emoji = "🔥", value = formatStreak(stats.streakMs), label = "running", modifier = Modifier.weight(1f))
                    StatCard(emoji = "⚡", value = "${stats.loginsToday}", label = "logins today", modifier = Modifier.weight(1f))
                    StatCard(emoji = "⏱", value = formatSpeed(stats.avgSpeedMs), label = "avg speed", modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
            }

            // Credentials form — shown ONLY when nothing is saved yet
            AnimatedVisibility(visible = !hasCredentials) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Portal credentials",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        validationError?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Recent activity — collapsible drawer backed by AppStatus.log
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activityExpanded = !activityExpanded }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recent activity",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(if (activityExpanded) "▲" else "▼", style = MaterialTheme.typography.bodySmall)
                    }
                    AnimatedVisibility(visible = activityExpanded) {
                        LazyColumn(
                            modifier = Modifier
                                .heightIn(max = 160.dp)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            items(log.reversed()) { line ->
                                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                    Text(
                                        line.timestamp,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(56.dp)
                                    )
                                    Text(line.message, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (!hasCredentials) {
                    Button(
                        onClick = {
                            if (username.isBlank() || password.isBlank()) {
                                validationError = "Enter a username and password first"
                                return@Button
                            }
                            validationError = null
                            onSave(username, password)
                            hasCredentials = true
                            isRunning = true
                            scope.launch { snackbarHostState.showSnackbar("Saved. Mr. Bashir is watching for networks now") }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Save & start") }
                } else {
                    Button(
                        onClick = {
                            onStart()
                            isRunning = true
                            scope.launch { snackbarHostState.showSnackbar("Mr. Bashir is watching for networks now") }
                        },
                        enabled = !isRunning,
                        modifier = Modifier.weight(1f)
                    ) { Text("Start") }

                    OutlinedButton(
                        onClick = {
                            onStop()
                            isRunning = false
                            scope.launch { snackbarHostState.showSnackbar("Stopped. Wi-Fi auto-login is off for now") }
                        },
                        enabled = isRunning,
                        modifier = Modifier.weight(1f)
                    ) { Text("Stop") }
                }

                OutlinedButton(
                    onClick = {
                        onForget()
                        username = ""
                        password = ""
                        hasCredentials = false
                        isRunning = false
                        scope.launch { snackbarHostState.showSnackbar("Credentials forgotten") }
                    },
                    enabled = hasCredentials,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) { Text("Forget me") }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "© 2026 BashirConnect · v1.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatCard(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(10.dp)
        ) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.labelLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatSpeed(avgSpeedMs: Long): String {
    if (avgSpeedMs <= 0) return "—"
    return "%.1fs".format(avgSpeedMs / 1000.0)
}

private fun formatStreak(streakMs: Long): String {
    if (streakMs <= 0) return "—"
    val totalMinutes = streakMs / 60_000
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

/**
 * Shows a scrollable dialog with the previous run's crash stack trace.
 * Only appears when CrashReporter has a saved trace — helps diagnose
 * crashes without needing USB / logcat / Termux.
 */
@Composable
private fun CrashDialog(trace: String) {
    var open by remember { mutableStateOf(true) }
    if (!open) return

    AlertDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TextButton(onClick = { open = false }) { Text("Dismiss") }
        },
        title = { Text("⚠️ Previous crash", style = MaterialTheme.typography.titleMedium) },
        text = {
            Text(
                text = trace,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    )
}
