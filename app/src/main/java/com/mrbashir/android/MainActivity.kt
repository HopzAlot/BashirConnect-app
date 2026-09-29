package com.mrbashir.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.MobileAds
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

        // Initialize AdMob SDK once. Must be called before any ad is loaded.
        // This is a lightweight background init — does not block the UI thread.
        MobileAds.initialize(this)

        credentialStore = CredentialStore(this)
        requestNotificationPermissionIfNeeded()

        setContent {
            MrBashirTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Banner is pinned at the bottom inside Scaffold so it NEVER
            // overlaps buttons or text — Scaffold gives the content
            // innerPadding that already accounts for the banner height.
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                AdMobBanner(modifier = Modifier.fillMaxWidth())
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BashirConnect",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(Modifier.height(16.dp))

            // Hero card: mascot + live status
            HeroCard(
                hasCredentials = hasCredentials,
                isRunning = isRunning,
                state = state
            )

            Spacer(Modifier.height(16.dp))

            // Stat row — only meaningful once Mr. Bashir has actually run
            if (stats.streakMs > 0 || stats.loginsToday > 0) {
                StatsDashboard(stats = stats)
                Spacer(Modifier.height(16.dp))
            }

            // Credentials form — shown ONLY when nothing is saved yet
            AnimatedVisibility(visible = !hasCredentials) {
                Column {
                    CredentialsCard(
                        username = username,
                        onUsernameChange = { username = it },
                        password = password,
                        onPasswordChange = { password = it },
                        validationError = validationError,
                        onSaveAndStart = {
                            if (username.isBlank() || password.isBlank()) {
                                validationError = "Enter a username and password first"
                                return@CredentialsCard
                            }
                            validationError = null
                            onSave(username, password)
                            hasCredentials = true
                            isRunning = true
                            scope.launch {
                                snackbarHostState.showSnackbar("Saved. Mr. Bashir is watching for networks now")
                            }
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Action buttons: Start, Stop, Forget
            ActionButtons(
                isRunning = isRunning,
                hasCredentials = hasCredentials,
                onStart = {
                    onStart()
                    isRunning = true
                    scope.launch {
                        snackbarHostState.showSnackbar("Mr. Bashir is watching for networks now")
                    }
                },
                onStop = {
                    onStop()
                    isRunning = false
                    scope.launch {
                        snackbarHostState.showSnackbar("Stopped. Wi-Fi auto-login is off for now")
                    }
                },
                onForget = {
                    onForget()
                    username = ""
                    password = ""
                    hasCredentials = false
                    isRunning = false
                    scope.launch {
                        snackbarHostState.showSnackbar("Credentials forgotten")
                    }
                }
            )

            if (hasCredentials) {
                Spacer(Modifier.height(16.dp))
            }

            // Recent activity collapsible drawer
            ActivityLogCard(
                logs = log,
                expanded = activityExpanded,
                onToggleExpand = { activityExpanded = !activityExpanded }
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "© 2026 BashirConnect · v1.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )
        }
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
