package com.uvindex.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uvindex.app.data.model.AirQualityLevel
import com.uvindex.app.ui.theme.AQIColorHelper
import kotlin.math.roundToInt

/** The fixed scale the EAQI bar always spans, regardless of the current reading. */
private const val AQI_SCALE_MIN = 0.0
private const val AQI_SCALE_MAX = 100.0

private data class AQIBand(val level: AirQualityLevel, val midpoint: Double)

// Extremely Poor (>100) is intentionally excluded from the bar itself, since the
// scale is fixed at 0-100; it still appears in the legend below the bar.
private val AQI_BANDS = listOf(
    AQIBand(AirQualityLevel.GOOD, 10.0),
    AQIBand(AirQualityLevel.FAIR, 30.0),
    AQIBand(AirQualityLevel.MODERATE, 50.0),
    AQIBand(AirQualityLevel.POOR, 70.0),
    AQIBand(AirQualityLevel.VERY_POOR, 90.0)
)

/**
 * Fixed 0-100 EAQI scale bar with a marker showing where the current AQI falls.
 * Unlike the UV/temperature charts, this never rescales to the data.
 */
@Composable
fun AQIScaleBar(
    aqi: Double,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val markerColor = AQIColorHelper.getColor(aqi, context, AQIColorHelper.ColorType.FOREGROUND)
    val fraction = ((aqi - AQI_SCALE_MIN) / (AQI_SCALE_MAX - AQI_SCALE_MIN))
        .coerceIn(0.0, 1.0)
        .toFloat()

    Column(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val barWidth = maxWidth
            val markerDiameter = 28.dp
            val markerX = (barWidth - markerDiameter) * fraction

            Column {
                // Value marker, positioned along the fixed 0-100 axis
                Box(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                    Text(
                        text = aqi.roundToInt().toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = markerColor,
                        modifier = Modifier
                            .offset(x = markerX)
                            .width(markerDiameter),
                        textAlign = TextAlign.Center
                    )
                }

                // Segmented gradient bar, always spanning 0-100
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    AQI_BANDS.forEach { band ->
                        val bandColor = AQIColorHelper.getColor(band.midpoint, context)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .height(20.dp)
                                .background(bandColor)
                        )
                    }
                }

                // Pointer marking the exact position on the bar
                Box(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                    Box(
                        modifier = Modifier
                            .offset(x = markerX + markerDiameter / 2 - 5.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(markerColor)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Tick labels: 0 and 100 flush with the bar's edges, 20/40/60/80
                // centered exactly under the band boundaries above.
                val tickSlotWidth = 24.dp
                Box(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                    listOf(0, 20, 40, 60, 80, 100).forEach { tick ->
                        val tickFraction = tick / AQI_SCALE_MAX.toFloat()
                        val tickX = (barWidth * tickFraction - tickSlotWidth / 2)
                            .coerceIn(0.dp, barWidth - tickSlotWidth)
                        Text(
                            text = tick.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .offset(x = tickX)
                                .width(tickSlotWidth)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AQILegend()
    }
}

/** Legend listing all six EAQI levels, including Extremely Poor which sits beyond the fixed bar. */
@Composable
private fun AQILegend(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(modifier = modifier) {
        AirQualityLevel.entries.forEach { level ->
            val representativeAqi = when (level) {
                AirQualityLevel.GOOD -> 10.0
                AirQualityLevel.FAIR -> 30.0
                AirQualityLevel.MODERATE -> 50.0
                AirQualityLevel.POOR -> 70.0
                AirQualityLevel.VERY_POOR -> 90.0
                AirQualityLevel.EXTREMELY_POOR -> 110.0
            }
            val color = AQIColorHelper.getColor(representativeAqi, context)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = level.label,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
