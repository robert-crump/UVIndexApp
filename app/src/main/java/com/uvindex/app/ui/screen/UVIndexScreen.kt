package com.uvindex.app.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.uvindex.app.data.model.UVForecast
import com.uvindex.app.ui.viewmodel.MainViewModel
import com.uvindex.app.ui.viewmodel.UVUiState
import com.uvindex.app.ui.theme.UVColorHelper
import com.uvindex.app.ui.theme.AQIColorHelper
import com.uvindex.app.ui.components.UVBarChart
import com.uvindex.app.ui.components.TemperatureLineChart
import com.uvindex.app.ui.components.AQIScaleBar
import com.uvindex.app.uv.SkinType
import com.uvindex.app.uv.protectionTimeParts
import com.uvindex.app.wind.travelOctant
import android.content.Intent
import com.uvindex.app.InfoActivity
import com.uvindex.app.SettingsActivity
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun UVIndexScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val skinType by viewModel.skinType.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showMenu by remember { mutableStateOf(false) }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.loadForecast(forceRefresh = true) }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UV-Index") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Mehr",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Info") },
                                onClick = {
                                    showMenu = false
                                    val intent = Intent(context, InfoActivity::class.java)
                                    context.startActivity(intent)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Einstellungen") },
                                onClick = {
                                    showMenu = false
                                    val intent = Intent(context, SettingsActivity::class.java)
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) {
                when (val state = uiState) {
                    is UVUiState.Idle -> {
                        // Warte auf Berechtigungen und Dialoge - zeige Loading-Indikator
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    is UVUiState.Loading -> {
                        // Zeige Skeleton Screen mit Shimmer-Effekt
                        SkeletonUVContent()
                    }
                    is UVUiState.Success -> {
                        CompactUVContent(
                            forecast = state.forecast,
                            skinType = skinType,
                            onOpenSettings = {
                                val intent = Intent(context, SettingsActivity::class.java)
                                    .putExtra(SettingsActivity.EXTRA_HIGHLIGHT_SKIN_TYPE, true)
                                context.startActivity(intent)
                            }
                        )
                    }
                    is UVUiState.Error -> {
                        ErrorContent(
                            message = state.message,
                            onRetry = { viewModel.loadForecast(forceRefresh = false) }
                        )
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun CompactUVContent(forecast: UVForecast, skinType: SkinType?, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LocationTimeCard(
            locationName = forecast.locationName,
            lastUpdateTime = forecast.lastUpdateTime,
            countryCode = forecast.countryCode
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CurrentUVCard(
                currentUV = forecast.currentHour.uvIndex,
                currentHour = forecast.currentHour.hour,
                forecast = forecast,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.11f)
            )
            MaxUVCard(
                dailyMax = forecast.dailyMaxRemaining,
                maxHour = forecast.maxHourToday,
                forecast = forecast,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.11f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SelfProtectionCard(
                skinType = skinType,
                currentUV = forecast.currentHour.uvIndex,
                onOpenSettings = onOpenSettings,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.11f)
            )
            TemperatureCard(
                temperature = forecast.currentHour.temperature,
                forecast = forecast,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.11f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WindCard(
                windSpeed = forecast.currentHour.windSpeed,
                windDirection = forecast.currentHour.windDirection,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.11f)
            )
            AirQualityCard(
                aqi = forecast.airQuality,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.11f)
            )
        }
    }
}

@Composable
fun LocationTimeCard(locationName: String?, lastUpdateTime: String?, countryCode: String?) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = locationName ?: "Standort",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
                if (!countryCode.isNullOrEmpty()) {
                    val emoji = countryCodeToEmoji(countryCode)
                    if (emoji.isNotEmpty()) {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
            if (!lastUpdateTime.isNullOrEmpty()) {
                Text(
                    text = "Datenstand: $lastUpdateTime Uhr",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun countryCodeToEmoji(countryCode: String): String {
    return try {
        if (countryCode.length != 2) return ""
        val upperCode = countryCode.uppercase()
        val firstChar = Character.codePointAt(upperCode, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(upperCode, 1) - 0x41 + 0x1F1E6
        String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    } catch (e: Exception) {
        ""
    }
}

@Composable
fun CurrentUVCard(
    currentUV: Double,
    currentHour: Int,
    forecast: UVForecast,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uvColor = UVColorHelper.getColor(currentUV, context, UVColorHelper.ColorType.FOREGROUND)
    val categoryText = UVColorHelper.getCategoryText(currentUV)
    val backgroundColor = UVColorHelper.getColor(currentUV, context, UVColorHelper.ColorType.BACKGROUND)

    // State for dialog
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .clickable { showDialog = true },
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Number left-aligned, at mid-height, may overflow beyond half width
                Text(
                    text = currentUV.toInt().toString(),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = uvColor,
                    modifier = Modifier.fillMaxWidth(0.5f)
                )
                // Time label in UV color, bold
                Text(
                    text = "${String.format("%02d:00", currentHour)} Uhr",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = uvColor
                )
                // Category in UV color, bold
                Text(
                    text = categoryText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = uvColor
                )
            }
        }
    }

    // Dialog with UV chart
    if (showDialog) {
        UVChartDialog(
            forecast = forecast,
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun MaxUVCard(
    dailyMax: Double,
    maxHour: Int,
    forecast: UVForecast,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uvColor = UVColorHelper.getColor(dailyMax, context, UVColorHelper.ColorType.FOREGROUND)
    val categoryText = UVColorHelper.getCategoryText(dailyMax)
    val backgroundColor = UVColorHelper.getColor(dailyMax, context, UVColorHelper.ColorType.BACKGROUND)

    // State for dialog
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .clickable { showDialog = true },
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Number left-aligned, at mid-height, may overflow beyond half width
                Text(
                    text = dailyMax.toInt().toString(),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = uvColor,
                    modifier = Modifier.fillMaxWidth(0.5f)
                )
                // Time label in UV color, bold
                Text(
                    text = "${String.format("%02d:00", maxHour)} Uhr (Max.)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = uvColor
                )
                // Category in UV color, bold
                Text(
                    text = categoryText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = uvColor
                )
            }
        }
    }

    // Dialog with UV chart
    if (showDialog) {
        UVChartDialog(
            forecast = forecast,
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun TemperatureCard(
    temperature: Double,
    forecast: UVForecast,
    modifier: Modifier = Modifier
) {
    // State for dialog
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .clickable { showDialog = true }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Number left-aligned, at mid-height, may overflow beyond half width
                Row(
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "${temperature.toInt()}",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.Black
                    )
                    Text(
                        text = "°C",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 8.dp),
                        color = androidx.compose.ui.graphics.Color.Black
                    )
                }
                // Label in black, bold
                Text(
                    text = "Temperatur",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.Black
                )
                // Empty line so "Temperatur" aligns with "Eigenschutz" in SelfProtectionCard
                Text(
                    text = "",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    // Dialog with temperature chart
    if (showDialog) {
        TemperatureChartDialog(
            forecast = forecast,
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun SelfProtectionCard(
    skinType: SkinType?,
    currentUV: Double,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        skinType == null -> {
            Card(
                modifier = modifier.clickable { onOpenSettings() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Hauttyp in Einstellungen wählen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        currentUV < 1.0 -> {
            Card(modifier = modifier) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Kein Risiko",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.Black
                        )
                        Text(
                            text = "Eigenschutz",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.Black
                        )
                        Text(
                            text = skinType.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.Black
                        )
                    }
                }
            }
        }
        else -> {
            val minutes = skinType.protectionMinutes(currentUV)
            val parts = protectionTimeParts(minutes)
            Card(modifier = modifier) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row {
                            parts.forEachIndexed { index, part ->
                                if (index > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = part.value,
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color.Black,
                                    modifier = Modifier.alignByBaseline()
                                )
                                Text(
                                    text = part.unit,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = androidx.compose.ui.graphics.Color.Black,
                                    modifier = Modifier.alignByBaseline()
                                )
                            }
                        }
                        Text(
                            text = "Eigenschutz",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.Black
                        )
                        Text(
                            text = "(${skinType.label})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WindCard(
    windSpeed: Double,
    windDirection: Double,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row {
                    Text(
                        text = "${windSpeed.toInt()}",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.Black,
                        modifier = Modifier.alignByBaseline()
                    )
                    Text(
                        text = "km/h",
                        style = MaterialTheme.typography.headlineMedium,
                        color = androidx.compose.ui.graphics.Color.Black,
                        modifier = Modifier.alignByBaseline()
                    )
                }
                Text(
                    text = "Wind",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.Black
                )
                Text(
                    text = if (windSpeed == 0.0) {
                        "Windstill"
                    } else {
                        "Richtung ${travelOctant(windDirection).name}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.Black
                )
            }
        }
    }
}

@Composable
fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Fehler",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Erneut versuchen")
        }
    }
}

@Composable
fun UVChartCard(forecast: UVForecast) {
    // Formatiere das aktuelle Datum
    val currentDate = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        .format(java.util.Date())

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "UV-Index: $currentDate",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            UVBarChart(
                forecast = forecast,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(187.dp)
            )
        }
    }
}

@Composable
fun AirQualityCard(
    aqi: Double?,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    if (aqi != null) {
        val aqiColor = AQIColorHelper.getColor(aqi, context, AQIColorHelper.ColorType.FOREGROUND)
        val backgroundColor = AQIColorHelper.getColor(aqi, context, AQIColorHelper.ColorType.BACKGROUND)
        val level = com.uvindex.app.data.model.getAirQualityLevel(aqi)

        // State for dialog
        var showDialog by remember { mutableStateOf(false) }

        Card(
            modifier = modifier
                .clickable { showDialog = true },
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = aqi.toInt().toString(),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = aqiColor,
                        modifier = Modifier.fillMaxWidth(0.5f)
                    )
                    Text(
                        text = "Luftqualität",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = aqiColor
                    )
                    Text(
                        text = level.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = aqiColor
                    )
                }
            }
        }

        // Dialog with AQI scale
        if (showDialog) {
            AirQualityScaleDialog(
                aqi = aqi,
                onDismiss = { showDialog = false }
            )
        }
    } else {
        Card(modifier = modifier) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "N/A",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.Black
                    )
                    Text(
                        text = "Luftqualität",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun UVChartDialog(forecast: UVForecast, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDismiss() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Box {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header with title and close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "UV-Index: ${java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date())}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Schließen",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    UVBarChart(
                        forecast = forecast,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TemperatureChartDialog(forecast: UVForecast, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDismiss() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Box {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header with title and close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Temperatur: ${java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date())}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Schließen",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    TemperatureLineChart(
                        forecast = forecast,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AirQualityScaleDialog(aqi: Double, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDismiss() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Box {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header with title and close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Luftqualität (EAQI)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Schließen",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    AQIScaleBar(
                        aqi = aqi,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun ShimmerEffect(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    Box(
        modifier = modifier
            .background(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(
                        androidx.compose.ui.graphics.Color.LightGray.copy(alpha = 0.3f),
                        androidx.compose.ui.graphics.Color.LightGray.copy(alpha = 0.5f),
                        androidx.compose.ui.graphics.Color.LightGray.copy(alpha = 0.3f)
                    ),
                    startX = shimmerTranslate.value - 1000f,
                    endX = shimmerTranslate.value
                )
            )
    )
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Skeleton for number
                ShimmerEffect(
                    modifier = Modifier
                        .width(80.dp)
                        .height(60.dp)
                )
                // Skeleton for text
                ShimmerEffect(
                    modifier = Modifier
                        .width(120.dp)
                        .height(20.dp)
                )
                // Skeleton for category
                ShimmerEffect(
                    modifier = Modifier
                        .width(100.dp)
                        .height(20.dp)
                )
            }
        }
    }
}

@Composable
fun SkeletonUVContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Skeleton for location card
        Card(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ShimmerEffect(
                        modifier = Modifier
                            .width(150.dp)
                            .height(24.dp)
                    )
                    ShimmerEffect(
                        modifier = Modifier
                            .width(180.dp)
                            .height(16.dp)
                    )
                }
            }
        }

        // Zeile 2: Aktuell und Clear-Sky
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkeletonCard(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.11f)
            )
            SkeletonCard(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.11f)
            )
        }

        // Zeile 3: Maximum und Temperatur
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkeletonCard(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.11f)
            )
            SkeletonCard(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.11f)
            )
        }

        // Zeile 4: Wind und Luftqualität
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkeletonCard(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.11f)
            )
            SkeletonCard(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.11f)
            )
        }
    }
}