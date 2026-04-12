package com.uvindex.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uvindex.app.ui.screen.UVIndexScreen
import com.uvindex.app.ui.theme.UVIndexTheme
import com.uvindex.app.ui.viewmodel.MainViewModel
import androidx.core.view.WindowCompat
import com.uvindex.app.widget.WidgetUpdateScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "uv_app_prefs"
        private const val KEY_BATTERY_DIALOG_SHOWN = "battery_dialog_shown"
    }

    private val _permissionsGrantedFlow = MutableStateFlow(false)
    private val permissionsGrantedFlow = _permissionsGrantedFlow.asStateFlow()

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if at least one location permission was granted
        val hasLocation = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)

        // After location permission → request notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission()) {
                notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                updatePermissionsState()
            }
        } else {
            updatePermissionsState()
        }
    }

    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        updatePermissionsState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge for seamless display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Schedule widget updates
        WidgetUpdateScheduler.schedulePeriodicUpdates(this)

        // Immediate update with reparse (no API request)
        WidgetUpdateScheduler.triggerImmediateUpdate(this, forceRefresh = false)

        setContent {
            UVIndexTheme {
                // Set status bar color
                LaunchedEffect(Unit) {
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
                }

                // State for permissions and dialogs
                val permissionsGranted by permissionsGrantedFlow.collectAsState()
                var showBatteryDialog by remember { mutableStateOf(shouldShowBatteryDialog()) }
                val viewModel: MainViewModel = viewModel()

                // Lifecycle-aware: refresh data when app returns to foreground
                androidx.compose.runtime.DisposableEffect(Unit) {
                    val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            viewModel.onResume()
                        }
                    }
                    lifecycle.addObserver(lifecycleObserver)
                    onDispose {
                        lifecycle.removeObserver(lifecycleObserver)
                    }
                }

                // Request permissions only once on first composition
                LaunchedEffect(Unit) {
                    checkAndRequestPermissions()
                }

                // Start data fetch once all prerequisites are met
                LaunchedEffect(permissionsGranted, showBatteryDialog) {
                    if (permissionsGranted && !showBatteryDialog) {
                        viewModel.startInitialLoad()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UVIndexScreen(viewModel = viewModel)
                }

                // Battery optimization dialog on first launch
                if (showBatteryDialog) {
                    BatteryOptimizationDialog(
                        onDismiss = {
                            markBatteryDialogShown()
                            showBatteryDialog = false
                        },
                        onOpenSettings = {
                            openBatteryOptimizationSettings()
                            markBatteryDialogShown()
                            showBatteryDialog = false
                        }
                    )
                }
            }
        }
    }

    private fun updatePermissionsState() {
        _permissionsGrantedFlow.value = hasAllPermissions()
    }

    private fun shouldShowBatteryDialog(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dialogShown = prefs.getBoolean(KEY_BATTERY_DIALOG_SHOWN, false)

        if (dialogShown) return false

        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return !powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun markBatteryDialogShown() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BATTERY_DIALOG_SHOWN, true)
            .apply()
    }

    private fun openBatteryOptimizationSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            } else {
                Intent(Settings.ACTION_SETTINGS)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback: open general battery settings
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(fallbackIntent)
        }
    }

    private fun checkAndRequestPermissions() {
        // Location permission (COARSE only for fast API startup)
        if (!hasLocationPermission()) {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            // Notification permission (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!hasNotificationPermission()) {
                    notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    updatePermissionsState()
                }
            } else {
                updatePermissionsState()
            }
        }
    }

    private fun hasAllPermissions(): Boolean {
        return hasLocationPermission() && hasNotificationPermission()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun onResume() {
        super.onResume()
        // Widget update on app return (with reparse)
        WidgetUpdateScheduler.triggerImmediateUpdate(this, forceRefresh = false)
    }
}

@Composable
fun BatteryOptimizationDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Hintergrund-Updates aktivieren",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Für zuverlässige Benachrichtigungen und Widget-Updates wird uneingeschränkte Hintergrundnutzung benötigt.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Dies erlaubt der App:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = "• Tägliche UV-Warnungen um 6:30 Uhr\n• Stündliche Benachrichtigungen bei hoher UV-Strahlung\n• Automatische Widget-Aktualisierungen",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(onClick = onOpenSettings) {
                Text("Einstellungen öffnen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Später")
            }
        }
    )
}