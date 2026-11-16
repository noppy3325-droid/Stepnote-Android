package com.stepnote.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stepnote.app.domain.model.ThemeMode

data class StepnoteColors(
    val background: Color,
    val card: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val barTrack: Color,
    val hairline: Color,
    val warning: Color
)

val LocalStepnoteColors = staticCompositionLocalOf {
    StepnoteColors(
        background = StepnotePalette.LightBackground,
        card = StepnotePalette.LightCard,
        textPrimary = StepnotePalette.LightTextPrimary,
        textSecondary = StepnotePalette.LightTextSecondary,
        accent = StepnotePalette.LightAccent,
        barTrack = StepnotePalette.LightBarTrack,
        hairline = StepnotePalette.LightHairline,
        warning = StepnotePalette.LightWarning
    )
}

private val StepnoteShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp)
)

@Composable
fun StepnoteTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val customColors = if (useDark) {
        StepnoteColors(
            background = StepnotePalette.DarkBackground,
            card = StepnotePalette.DarkCard,
            textPrimary = StepnotePalette.DarkTextPrimary,
            textSecondary = StepnotePalette.DarkTextSecondary,
            accent = StepnotePalette.DarkAccent,
            barTrack = StepnotePalette.DarkBarTrack,
            hairline = StepnotePalette.DarkHairline,
            warning = StepnotePalette.DarkWarning
        )
    } else {
        StepnoteColors(
            background = StepnotePalette.LightBackground,
            card = StepnotePalette.LightCard,
            textPrimary = StepnotePalette.LightTextPrimary,
            textSecondary = StepnotePalette.LightTextSecondary,
            accent = StepnotePalette.LightAccent,
            barTrack = StepnotePalette.LightBarTrack,
            hairline = StepnotePalette.LightHairline,
            warning = StepnotePalette.LightWarning
        )
    }

    val m3Scheme = if (useDark) {
        darkColorScheme(
            primary = customColors.accent,
            onPrimary = StepnotePalette.DarkBackground,
            background = customColors.background,
            onBackground = customColors.textPrimary,
            surface = customColors.card,
            onSurface = customColors.textPrimary,
            surfaceVariant = customColors.barTrack,
            onSurfaceVariant = customColors.textSecondary,
            outline = customColors.hairline
        )
    } else {
        lightColorScheme(
            primary = customColors.accent,
            onPrimary = Color.White,
            background = customColors.background,
            onBackground = customColors.textPrimary,
            surface = customColors.card,
            onSurface = customColors.textPrimary,
            surfaceVariant = customColors.barTrack,
            onSurfaceVariant = customColors.textSecondary,
            outline = customColors.hairline
        )
    }

    CompositionLocalProvider(LocalStepnoteColors provides customColors) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = StepnoteTypography,
            shapes = StepnoteShapes,
            content = content
        )
    }
}
