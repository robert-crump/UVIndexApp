package com.uvindex.app.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uvindex.app.R
import com.uvindex.app.permission.isLocationGranted
import com.uvindex.app.schedule.BackgroundSchedule
import kotlinx.coroutines.delay

/** Presentation only: renders [SettingsViewModel.state], forwards intents, and launches the OS permission UI it asks for. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackPressed: () -> Unit,
    highlightSkinType: Boolean = false,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Stop blinking automatically after 3 s so it doesn't run indefinitely.
    var activeHighlight by remember { mutableStateOf(highlightSkinType) }
    LaunchedEffect(highlightSkinType) {
        if (highlightSkinType) {
            delay(3000)
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

    var showDisclosureDialog by remember { mutableStateOf(false) }
    var showRevokeDialog by remember { mutableStateOf(false) }

    // The switch mirrors the real OS grant state, which the user may change in system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        viewModel::onBackgroundLocationResult
    )
    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> viewModel.onForegroundLocationResult(isLocationGranted(result)) }

    LaunchedEffect(viewModel) {
        viewModel.permissionActions.collect { action ->
            when (action) {
                PermissionAction.RequestForegroundLocation -> foregroundLocationLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
                PermissionAction.RequestBackgroundLocation ->
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                PermissionAction.OpenAppSettings -> context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
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
            SkinTypeSection(
                skinType = state.skinType,
                highlightAlpha = highlightAlpha,
                onSkinTypeSelected = viewModel::setSkinType
            )

            SectionHeading("Benachrichtigungen")
            SettingSwitchRow(
                title = "Tagesbenachrichtigung",
                description = "Tägliche Benachrichtigung um ${BackgroundSchedule.dailyNotificationTimeText} mit Tageshöchstwert und, je nach Kategorie, Schutzempfehlung oder Zeitraum zum Vermeiden direkter Sonne",
                checked = state.dailyEnabled,
                onCheckedChange = viewModel::setDailyEnabled
            )
            SettingSwitchRow(
                title = "Stündliche Benachrichtigung vor hoher UV-Strahlung",
                description = "Du wirst gewarnt, wenn UV-Strahlung (sehr) hoch ist oder in der nächsten Stunde auf hohe Werte ansteigt",
                checked = state.uvWarningEnabled,
                onCheckedChange = viewModel::setUvWarningEnabled
            )

            SectionHeading(stringResource(R.string.settings_location_heading))
            SettingSwitchRow(
                title = stringResource(R.string.settings_background_location_title),
                description = stringResource(R.string.settings_background_location_description),
                checked = state.backgroundLocationGranted,
                // Revoking cannot be done in-app, so switching off explains where to do it.
                onCheckedChange = { enabled ->
                    if (enabled) showDisclosureDialog = true else showRevokeDialog = true
                }
            )
        }
    }

    if (showDisclosureDialog) {
        SettingsDialog(
            title = stringResource(R.string.background_location_disclosure_title),
            body = stringResource(R.string.background_location_disclosure_body),
            confirmText = stringResource(R.string.background_location_disclosure_confirm),
            dismissText = stringResource(R.string.background_location_disclosure_cancel),
            onConfirm = viewModel::requestBackgroundLocation,
            onDismiss = { showDisclosureDialog = false }
        )
    }
    if (showRevokeDialog) {
        SettingsDialog(
            title = stringResource(R.string.background_location_revoke_title),
            body = stringResource(R.string.background_location_revoke_body),
            confirmText = stringResource(R.string.background_location_open_settings),
            dismissText = stringResource(R.string.background_location_disclosure_cancel),
            onConfirm = viewModel::openAppSettings,
            onDismiss = { showRevokeDialog = false }
        )
    }
}
