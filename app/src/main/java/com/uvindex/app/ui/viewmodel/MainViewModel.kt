package com.uvindex.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uvindex.app.data.model.UVForecast
import com.uvindex.app.data.repository.WeatherRepository
import com.uvindex.app.widget.UVWidget
import com.uvindex.app.widget.UVWidgetMax
import com.uvindex.app.util.WidgetUpdateHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WeatherRepository(application)

    private val _uiState = MutableStateFlow<UVUiState>(UVUiState.Idle)
    val uiState: StateFlow<UVUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var hasLoadedInitially = false
    private var lastLoadTime = 0L

    fun startInitialLoad() {
        if (!hasLoadedInitially) {
            hasLoadedInitially = true
            loadForecast(forceRefresh = false)
        }
    }

    /**
     * Called when the app returns to the foreground.
     * Reloads data if it is stale (> 5 minutes old).
     */
    fun onResume() {
        if (!hasLoadedInitially) return

        val now = System.currentTimeMillis()
        val minutesSinceLastLoad = (now - lastLoadTime) / (1000 * 60)

        // If more than 5 minutes since last load → refresh
        if (minutesSinceLastLoad >= 5) {
            loadForecast(forceRefresh = false)
        }
    }

    fun loadForecast(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            // Prevent concurrent calls (race condition)
            if (_isRefreshing.value) return@launch

            // Set refreshing state
            _isRefreshing.value = true
            lastLoadTime = System.currentTimeMillis()

            // Show loading only when no data exists yet or explicitly requested
            val hasData = _uiState.value is UVUiState.Success
            if (forceRefresh || !hasData) {
                _uiState.value = UVUiState.Loading
            }

            repository.getUVForecast(forceRefresh).fold(
                onSuccess = { forecast ->
                    _uiState.value = UVUiState.Success(forecast)
                    _isRefreshing.value = false

                    // Update widgets after a successful data fetch with a short delay
                    if (forceRefresh) {
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(500) // 500ms delay to allow cache to be written
                            updateWidgets()
                        }
                    }
                },
                onFailure = { error ->
                    // Retain existing data on error if available
                    if (_uiState.value !is UVUiState.Success) {
                        _uiState.value = UVUiState.Error(error.message ?: "Unbekannter Fehler")
                    }
                    _isRefreshing.value = false
                }
            )
        }
    }

    private fun updateWidgets() {
        val context = getApplication<Application>()
        WidgetUpdateHelper.updateAllWidgets(context)
    }
}

sealed class UVUiState {
    object Idle : UVUiState()
    object Loading : UVUiState()
    data class Success(val forecast: UVForecast) : UVUiState()
    data class Error(val message: String) : UVUiState()
}