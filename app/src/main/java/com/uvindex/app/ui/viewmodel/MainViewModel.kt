package com.uvindex.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uvindex.app.data.local.DataStoreManager
import com.uvindex.app.data.model.UVForecast
import com.uvindex.app.data.repository.FetchIntent
import com.uvindex.app.data.repository.WeatherRepository
import com.uvindex.app.util.WidgetUpdateHelper
import com.uvindex.app.uv.SkinType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WeatherRepository(application)
    private val dataStoreManager = DataStoreManager(application)

    val skinType: StateFlow<SkinType?> = dataStoreManager.getSkinType()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _uiState = MutableStateFlow<UVUiState>(UVUiState.Idle)
    val uiState: StateFlow<UVUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var hasLoadedInitially = false

    fun startInitialLoad() {
        if (!hasLoadedInitially) {
            hasLoadedInitially = true
            loadForecast(FetchIntent.FreshIfStale)
        }
    }

    /**
     * Called when the app returns to the foreground.
     * The repository decides whether the cache is stale.
     */
    fun onResume() {
        if (!hasLoadedInitially) return
        loadForecast(FetchIntent.FreshIfStale)
    }

    fun loadForecast(intent: FetchIntent) {
        viewModelScope.launch {
            // Prevent concurrent calls (race condition)
            if (_isRefreshing.value) return@launch

            // Set refreshing state
            _isRefreshing.value = true

            // Show loading only when no data exists yet or explicitly requested
            val hasData = _uiState.value is UVUiState.Success
            if (intent == FetchIntent.Fresh || !hasData) {
                _uiState.value = UVUiState.Loading
            }

            repository.getUVForecast(intent).fold(
                onSuccess = { forecast ->
                    _uiState.value = UVUiState.Success(forecast)
                    _isRefreshing.value = false

                    // Update widgets after a successful data fetch. No delay needed: the
                    // repository's DataStore.edit() call suspends until the cache write is
                    // persisted, so by the time onSuccess runs here the cache is durable.
                    if (intent == FetchIntent.Fresh) {
                        updateWidgets()
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