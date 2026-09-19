package com.uvindex.app.ui.settings

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uvindex.app.data.local.DataStoreManager
import com.uvindex.app.data.local.SkinTypeStore
import com.uvindex.app.data.location.BackgroundLocationStep
import com.uvindex.app.data.location.nextBackgroundLocationStep
import com.uvindex.app.notification.NotificationHistoryStore
import com.uvindex.app.notification.SharedPreferencesNotificationHistoryStore
import com.uvindex.app.permission.AppPermissions
import com.uvindex.app.permission.LocationPermissionState
import com.uvindex.app.util.WidgetUpdateHelper
import com.uvindex.app.uv.SkinType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val skinType: SkinType? = null,
    val dailyEnabled: Boolean = true,
    val uvWarningEnabled: Boolean = true,
    val backgroundLocationGranted: Boolean = false,
)

/** A step of the background-location opt-in that only the UI layer (an Activity) can carry out. */
enum class PermissionAction { RequestForegroundLocation, RequestBackgroundLocation, OpenAppSettings }

/**
 * Owns the user's settings intent: every setter performs all side effects it implies, so the
 * screen only renders [state] and calls these methods.
 */
class SettingsViewModel(
    private val skinTypeStore: SkinTypeStore,
    private val historyStore: NotificationHistoryStore,
    private val locationPermissions: LocationPermissionState,
    private val refreshWidgets: () -> Unit,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(backgroundLocationGranted = locationPermissions.hasBackgroundLocation())
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _permissionActions = Channel<PermissionAction>(Channel.BUFFERED)
    val permissionActions = _permissionActions.receiveAsFlow()

    init {
        viewModelScope.launch {
            skinTypeStore.getSkinType().collect { type -> _state.update { it.copy(skinType = type) } }
        }
        viewModelScope.launch {
            val history = historyStore.snapshot()
            _state.update {
                it.copy(dailyEnabled = history.dailyEnabled, uvWarningEnabled = history.uvWarningEnabled)
            }
        }
    }

    fun setSkinType(type: SkinType) {
        viewModelScope.launch {
            skinTypeStore.saveSkinType(type)
            refreshWidgets()
        }
    }

    /** The daily notification is decided by the tick worker from this flag, so no schedule work is needed. */
    fun setDailyEnabled(enabled: Boolean) {
        _state.update { it.copy(dailyEnabled = enabled) }
        viewModelScope.launch { historyStore.setDailyEnabled(enabled) }
    }

    fun setUvWarningEnabled(enabled: Boolean) {
        _state.update { it.copy(uvWarningEnabled = enabled) }
        viewModelScope.launch { historyStore.setUvWarningEnabled(enabled) }
    }

    /** Re-reads the OS grant state; call whenever the screen resumes (the user may change it in system settings). */
    fun refreshPermissions() {
        _state.update { it.copy(backgroundLocationGranted = locationPermissions.hasBackgroundLocation()) }
    }

    fun requestBackgroundLocation() {
        when (
            nextBackgroundLocationStep(
                sdkInt,
                foregroundGranted = locationPermissions.hasForegroundLocation(),
                backgroundGranted = locationPermissions.hasBackgroundLocation(),
            )
        ) {
            BackgroundLocationStep.AlreadyGranted -> refreshPermissions()
            BackgroundLocationStep.RequestForegroundFirst -> emit(PermissionAction.RequestForegroundLocation)
            BackgroundLocationStep.RequestBackgroundInline -> emit(PermissionAction.RequestBackgroundLocation)
            BackgroundLocationStep.DirectToSystemSettings -> emit(PermissionAction.OpenAppSettings)
        }
    }

    /** Result of the foreground request that [requestBackgroundLocation] started. */
    fun onForegroundLocationResult(granted: Boolean) {
        if (!granted) return
        when (nextBackgroundLocationStep(sdkInt, foregroundGranted = true, backgroundGranted = false)) {
            BackgroundLocationStep.AlreadyGranted -> _state.update { it.copy(backgroundLocationGranted = true) }
            else -> emit(PermissionAction.RequestBackgroundLocation)
        }
    }

    fun onBackgroundLocationResult(granted: Boolean) {
        _state.update { it.copy(backgroundLocationGranted = granted) }
    }

    /** Revoking cannot be done in-app; the user is sent to the app's system settings. */
    fun openAppSettings() = emit(PermissionAction.OpenAppSettings)

    private fun emit(action: PermissionAction) {
        _permissionActions.trySend(action)
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    skinTypeStore = DataStoreManager(application),
                    historyStore = SharedPreferencesNotificationHistoryStore(application),
                    locationPermissions = AppPermissions.locationPermissionState(application),
                    refreshWidgets = { WidgetUpdateHelper.updateAllWidgets(application) },
                )
            }
        }
    }
}
