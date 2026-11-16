package com.stepnote.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stepnote.app.domain.model.HourlyStepSummary
import com.stepnote.app.ui.theme.LocalStepnoteColors
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HourlyStepBarChart(
    buckets: List<HourlyStepSummary>,
    modifier: Modifier = Modifier
) {
    val colors = LocalStepnoteColors.current
    val formatter = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
    var selectedHour by remember(buckets) { mutableStateOf<Int?>(null) }

    val byHour = remember(buckets) {
        val map = buckets.associateBy { it.hour }
        (0..23).map { hour -> map[hour]?.steps }
    }
    val maxSteps = remember(byHour) {
        (byHour.filterNotNull().maxOrNull() ?: 0L).coerceAtLeast(100L)
    }
    fun hourLabel(hour: Int): String {
        val steps = byHour[hour]?.let { "${formatter.format(it)} 歩" } ?: "未取得"
        return String.format(Locale.getDefault(), "%02d:00 · %s", hour, steps)
    }
    val activeInfo = selectedHour?.let(::hourLabel) ?: if (buckets.isEmpty()) "未取得" else "0〜24時"

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "時間帯別歩数",
                color = colors.textSecondary,
                fontSize = 12.sp
            )
            Text(
                text = activeInfo,
                color = if (selectedHour != null) colors.textPrimary else colors.textSecondary,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .pointerInput(byHour) {
                    detectTapGestures { offset ->
                        val slotWidth = size.width / 24f
                        val tapped = (offset.x / slotWidth).toInt().coerceIn(0, 23)
                        selectedHour = if (selectedHour == tapped) null else tapped
                    }
                }
                .semantics {
                    contentDescription = "時間帯別歩数グラフ。$activeInfo"
                    customActions = listOf(
                        CustomAccessibilityAction("前の時間帯") {
                            selectedHour = ((selectedHour ?: 0) - 1).mod(24)
                            true
                        },
                        CustomAccessibilityAction("次の時間帯") {
                            selectedHour = ((selectedHour ?: -1) + 1).mod(24)
                            true
                        }
                    )
                }
        ) {
            val slotWidth = size.width / 24f
            val barWidth = (slotWidth * 0.62f).coerceAtLeast(4f)
            val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())

            byHour.forEachIndexed { hour, steps ->
                val x = hour * slotWidth + (slotWidth - barWidth) / 2f
                // Subtle baseline track
                drawRoundRect(
                    color = when {
                        selectedHour == hour || steps == 0L -> colors.accent
                        else -> colors.barTrack.copy(alpha = 0.55f)
                    },
                    topLeft = Offset(x, size.height - 4.dp.toPx()),
                    size = Size(barWidth, 4.dp.toPx()),
                    cornerRadius = corner
                )
                if (steps != null && steps > 0L) {
                    val ratio = (steps.toFloat() / maxSteps.toFloat()).coerceIn(0.06f, 1f)
                    val barHeight = size.height * ratio
                    val barColor = if (selectedHour == null || selectedHour == hour) {
                        colors.accent
                    } else {
                        colors.accent.copy(alpha = 0.35f)
                    }
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = corner
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("0時", "6時", "12時", "18時", "24時").forEach { label ->
                Text(text = label, color = colors.textSecondary, fontSize = 11.sp)
            }
        }
    }
}
