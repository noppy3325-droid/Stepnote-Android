package com.stepnote.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stepnote.app.ui.theme.LocalStepnoteColors

@Composable
fun StepProgressBar(
    progress: Float,
    percentText: Int,
    modifier: Modifier = Modifier
) {
    val colors = LocalStepnoteColors.current
    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(durationMillis = 240),
        label = "step_progress"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .semantics {
                contentDescription = "目標達成率 $percentText パーセント"
            }
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = colors.barTrack,
            size = size,
            cornerRadius = radius
        )
        if (animated > 0f) {
            drawRoundRect(
                color = colors.accent,
                size = Size(width = size.width * animated, height = size.height),
                cornerRadius = radius
            )
        }
    }
}
