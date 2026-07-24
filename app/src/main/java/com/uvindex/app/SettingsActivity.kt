package com.uvindex.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.uvindex.app.data.local.DataStoreManager
import com.uvindex.app.data.location.BackgroundLocationStep
import com.uvindex.app.data.location.nextBackgroundLocationStep
import com.uvindex.app.notification.SharedPreferencesNotificationHistoryStore
import com.uvindex.app.ui.theme.UVIndexTheme
import com.uvindex.app.util.WidgetUpdateHelper
import com.uvindex.app.uv.SkinType
import com.uvindex.app.widget.NotificationScheduler
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    companion object {
        const val EXTRA_HIGHLIGHT_SKIN_TYPE = "highlight_skin_type"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val highlightSkinType = intent.getBooleanExtra(EXTRA_HIGHLIGHT_SKIN_TYPE, false)

        // Enable edge-to-edge for seamless display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            UVIndexTheme {
                // Set status bar color
                LaunchedEffect(Unit) {
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
                }

                SettingsScreen(
                    onBackPressed = { finish() },
                    highlightSkinType = highlightSkinType
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBackPressed: () -> Unit, highlightSkinType: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val historyStore = remember { SharedPreferencesNotificationHistoryStore(context) }
    val dataStoreManager = remember { DataStoreManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var dailyNotificationEnabled by remember { mutableStateOf(true) }
    var hourlyNotificationEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val history = historyStore.snapshot()
        dailyNotificationEnabled = history.dailyEnabled
        hourlyNotificationEnabled = history.uvWarningEnabled
    }

    val currentSkinType by dataStoreManager.getSkinType().collectAsState(initial = null)
    var skinTypeDropdownExpanded by remember { mutableStateOf(false) }

    // Stop blinking automatically after 3 s so it doesn't run indefinitely.
    var activeHighlight by remember { mutableStateOf(highlightSkinType) }
    LaunchedEffect(highlightSkinType) {
        if (highlightSkinType) {
            kotlinx.coroutines.delay(3000)
            activeHighlight = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "skinTypeHighlight")
    val highlightAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (activeHighlight) 0.18f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "highlight_alpha"
    )

    // --- Background location opt-in (Issue #21) -------------------------------------------
    // The Switch mirrors the real OS grant state (the user may grant/revoke in system
    // settings), re-read every time the screen resumes rather than stored as a bool.
    fun foregroundLocationGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun backgroundLocationGranted(): Boolean =
        if (Build.VERSION.SDK_INT <= 28) {
            foregroundLocationGranted()
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    var backgroundLocationEnabled by remember { mutableStateOf(false) }
    var showDisclosureDialog by remember { mutableStateOf(false) }
    var showRevokeDialog by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        backgroundLocationEnabled = backgroundLocationGranted()
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        backgroundLocationEnabled = granted
    }

    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            // Foreground just granted — continue to the appropriate background step.
            when (nextBackgroundLocationStep(
                Build.VERSION.SDK_INT, foregroundGranted = true, backgroundGranted = false
            )) {
                BackgroundLocationStep.AlreadyGranted -> backgroundLocationEnabled = true
                BackgroundLocationStep.RequestBackgroundInline ->
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                BackgroundLocationStep.DirectToSystemSettings ->
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                BackgroundLocationStep.RequestForegroundFirst -> Unit // not reachable here
            }
        }
    }

    fun startBackgroundLocationOptIn() {
        when (nextBackgroundLocationStep(
            Build.VERSION.SDK_INT,
            foregroundGranted = foregroundLocationGranted(),
            backgroundGranted = backgroundLocationGranted()
        )) {
            BackgroundLocationStep.AlreadyGranted -> backgroundLocationEnabled = true
            BackgroundLocationStep.RequestForegroundFirst -> foregroundLocationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            BackgroundLocationStep.RequestBackgroundInline ->
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            BackgroundLocationStep.DirectToSystemSettings -> openAppSettings()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Skin type section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha))
                    .padding(vertical = 4.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Hauttyp",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Wird zur Berechnung der Eigenschutzzeit verwendet (Fitzpatrick-Skala)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ExposedDropdownMenuBox(
                        expanded = skinTypeDropdownExpanded,
                        onExpandedChange = { skinTypeDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = currentSkinType?.let { "${it.label} – ${it.description}" }
                                ?: "Nicht ausgewählt",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Hauttyp") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = skinTypeDropdownExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = skinTypeDropdownExpanded,
                            onDismissRequest = { skinTypeDropdownExpanded = false }
                        ) {
                            SkinType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = type.label,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Text(
                                                text = type.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        coroutineScope.launch {
                                            dataStoreManager.saveSkinType(type)
                                            WidgetUpdateHelper.updateAllWidgets(context)
                                        }
                                        skinTypeDropdownExpanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                }
            }

            // Heading
            Text(
                text = "Benachrichtigungen",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Tagesbenachrichtigung
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Tagesbenachrichtigung",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tägliche Benachrichtigung um 06:30 mit Tageshöchstwert und, je nach Kategorie, Schutzempfehlung oder Zeitraum zum Vermeiden direkter Sonne",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = dailyNotificationEnabled,
                    onCheckedChange = { enabled ->
                        dailyNotificationEnabled = enabled
                        coroutineScope.launch { historyStore.setDailyEnabled(enabled) }
                        if (enabled) NotificationScheduler.scheduleDailyNotification(context)
                        else NotificationScheduler.cancelDailyNotification(context)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // Hourly warning
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Warnung vor hoher Strahlung",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Du wirst gewarnt, wenn UV-Strahlung (sehr) hoch ist oder in der nächsten Stunde auf hohe Werte ansteigt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = hourlyNotificationEnabled,
                    onCheckedChange = { enabled ->
                        hourlyNotificationEnabled = enabled
                        coroutineScope.launch { historyStore.setUvWarningEnabled(enabled) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // Location heading
            Text(
                text = stringResource(R.string.settings_location_heading),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Background location opt-in
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.settings_background_location_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_background_location_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = backgroundLocationEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            showDisclosureDialog = true
                        } else {
                            // Cannot revoke programmatically — guide the user to settings.
                            showRevokeDialog = true
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }

    if (showDisclosureDialog) {
        AlertDialog(
            onDismissRequest = { showDisclosureDialog = false },
            title = { Text(stringResource(R.string.background_location_disclosure_title)) },
            text = { Text(stringResource(R.string.background_location_disclosure_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosureDialog = false
                    startBackgroundLocationOptIn()
                }) {
                    Text(stringResource(R.string.background_location_disclosure_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosureDialog = false }) {
                    Text(stringResource(R.string.background_location_disclosure_cancel))
                }
            }
        )
    }

    if (showRevokeDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeDialog = false },
            title = { Text(stringResource(R.string.background_location_revoke_title)) },
            text = { Text(stringResource(R.string.background_location_revoke_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRevokeDialog = false
                    openAppSettings()
                }) {
                    Text(stringResource(R.string.background_location_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeDialog = false }) {
                    Text(stringResource(R.string.background_location_disclosure_cancel))
                }
            }
        )
    }
}