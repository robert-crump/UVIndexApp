package com.uvindex.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "uv_index_prefs")

class DataStoreManager(private val context: Context) {
    
    companion object {
        private val CACHED_WEATHER_DATA = stringPreferencesKey("cached_weather_data")
        private val LAST_LATITUDE = doublePreferencesKey("last_latitude")
        private val LAST_LONGITUDE = doublePreferencesKey("last_longitude")
        private val LAST_UPDATE_TIME = longPreferencesKey("last_update_time")
    }
    
    suspend fun saveCachedWeatherData(data: String) {
        context.dataStore.edit { preferences ->
            preferences[CACHED_WEATHER_DATA] = data
        }
    }
    
    fun getCachedWeatherData(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[CACHED_WEATHER_DATA]
        }
    }
    
    suspend fun saveLastLocation(latitude: Double, longitude: Double) {
        context.dataStore.edit { preferences ->
            preferences[LAST_LATITUDE] = latitude
            preferences[LAST_LONGITUDE] = longitude
            preferences[LAST_UPDATE_TIME] = System.currentTimeMillis()
        }
    }
    
    fun getLastLocation(): Flow<LocationData?> {
        return context.dataStore.data.map { preferences ->
            val lat = preferences[LAST_LATITUDE]
            val lon = preferences[LAST_LONGITUDE]
            val time = preferences[LAST_UPDATE_TIME]
            
            if (lat != null && lon != null && time != null) {
                LocationData(lat, lon, time)
            } else {
                null
            }
        }
    }
    
}

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)
