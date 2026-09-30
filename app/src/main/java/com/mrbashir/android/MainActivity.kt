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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
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
            MrBashirTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val pendingCrash = remember {
                        CrashReporter.consumePendingCrash(this@MainActivity)
                    }
                    if (pendingCrash != null) {
                        CrashDialog(trace = pendingCrash)
                    }

                    // Simple in-memory navigation: main screen ↔ tip jar
                    var showTipJar by remember { mutableStateOf(false) }

                    if (showTipJar) {
                        TipJarScreen(onBack = { showTipJar = false })
                    } else {
                        MrBashirScreen(
                            credentialStore = credentialStore,
                            onSave = { username, password ->
                                credentialStore.save(username, password)
                                CaptivePortalService.start(this)
                            },
                            onStart = { CaptivePortalService.start(this) },
                            onStop = { CaptivePortalService.stop(this) },
                            onShowTipJar = { showTipJar = true }
                        )
                    }
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
    onShowTipJar: () -> Unit = {}
) {
    val context = LocalContext.current
    var hasCredentials by remember { mutableStateOf(credentialStore.hasCredentials()) }
    var isEditing by remember { mutableStateOf(false) }
    val statsStore = remember { StatsStore(context) }
    var isRunning by remember { mutableStateOf(statsStore.isServiceEnabled()) }
    var username by remember { mutableStateOf(credentialStore.getUsername().orEmpty()) }
    var password by remember { mutableStateOf(credentialStore.getPassword().orEmpty()) }
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Fund Me Jar button at bottom above copyright
                Surface(
                    onClick = onShowTipJar,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("🍯", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Fund Me Jar · Keep Bashir Saab Alive ☕",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Copyright shifted to the very bottom
                Text(
                    text = "© 2026 BashirConnect",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
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

            // Credentials form — shown when nothing is saved yet or when editing
            AnimatedVisibility(visible = !hasCredentials || isEditing) {
                Column {
                    CredentialsCard(
                        username = username,
                        onUsernameChange = {
                            username = it
                            if (validationError != null) validationError = null
                        },
                        password = password,
                        onPasswordChange = {
                            password = it
                            if (validationError != null) validationError = null
                        },
                        isEditing = isEditing,
                        onCancel = {
                            username = credentialStore.getUsername().orEmpty()
                            password = credentialStore.getPassword().orEmpty()
                            validationError = null
                            isEditing = false
                        },
                        validationError = validationError,
                        onSaveAndStart = {
                            if (username.isBlank() || password.isBlank()) {
                                validationError = "Please enter both Student ID and Password"
                                return@CredentialsCard
                            }
                            validationError = null
                            val wasEditing = isEditing
                            onSave(username.trim(), password.trim())
                            hasCredentials = true
                            isEditing = false
                            isRunning = true
                            scope.launch {
                                val msg = if (wasEditing) {
                                    "Credentials updated successfully! ✅"
                                } else {
                                    "Saved. Mr. Bashir is watching for networks now"
                                }
                                snackbarHostState.showSnackbar(msg)
                            }
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Action buttons: Start, Stop, Edit credentials
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
                onEdit = {
                    username = credentialStore.getUsername().orEmpty()
                    password = credentialStore.getPassword().orEmpty()
                    validationError = null
                    isEditing = !isEditing
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
