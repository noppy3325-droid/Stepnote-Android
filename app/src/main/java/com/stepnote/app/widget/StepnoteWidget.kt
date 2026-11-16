package com.stepnote.app.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.stepnote.app.MainActivity
import com.stepnote.app.StepnoteApplication
import com.stepnote.app.domain.model.DailyStepSummary
import com.stepnote.app.domain.model.DataStatus
import com.stepnote.app.domain.model.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class StepnoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StepnoteWidget()
}

class StepnoteWidget : GlanceAppWidget() {

    companion object {
        private val SMALL_SIZE = DpSize(120.dp, 120.dp)
        private val MEDIUM_SIZE = DpSize(240.dp, 120.dp)
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL_SIZE, MEDIUM_SIZE)
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as StepnoteApplication
        val repository = app.container.stepRepository
        val prefsFlow = app.container.preferencesRepository.preferencesFlow
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

        val snapshots = prefsFlow
            .map { it.copy(lastSyncAt = null) }
            .distinctUntilChanged()
            .flatMapLatest { prefs ->
                val source = repository.resolveActiveSource(prefs)
                val records = if (source == null) {
                    flowOf(emptyList<DailyStepSummary>())
                } else {
                    repository.observeCachedRange(today, today, source.kind, prefs.sourceScope, zone)
                }
                records.map { prefs to it.firstOrNull()?.takeIf { day -> day.status != DataStatus.UNMEASURED } }
            }
        val initial = snapshots.first()

        provideContent {
            val snapshot by snapshots.collectAsState(initial)
            val (prefs, cached) = snapshot
            val steps = cached?.steps ?: 0L
            val goal = prefs.dailyGoal
            StepnoteWidgetContent(
                steps = steps,
                goal = goal,
                percent = app.container.statsUseCase.progressPercentExact(steps, goal),
                remaining = (goal - steps).coerceAtLeast(0L),
                hasRecord = cached != null,
                updatedLabel = cached?.fetchedAt?.atZone(zone)
                    ?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty(),
                dark = prefs.themeMode == ThemeMode.DARK ||
                    (prefs.themeMode == ThemeMode.SYSTEM && systemDark)
            )
        }
    }
}

@Composable
private fun StepnoteWidgetContent(
    steps: Long,
    goal: Int,
    percent: Int,
    remaining: Long,
    hasRecord: Boolean,
    updatedLabel: String,
    dark: Boolean
) {
    val size = LocalSize.current
    val isMedium = size.width >= 200.dp
    val formatter = NumberFormat.getNumberInstance(Locale.getDefault())

    val cardBg = ColorProvider(if (dark) Color(0xFF242A25) else Color.White)
    val textPrimary = ColorProvider(if (dark) Color(0xFFEFF1EB) else Color(0xFF252821))
    val textSecondary = ColorProvider(if (dark) Color(0xFFA9B0A6) else Color(0xFF67685F))
    val accent = ColorProvider(if (dark) Color(0xFF9AB99E) else Color(0xFF526C56))
    val barBg = ColorProvider(if (dark) Color(0xFF364039) else Color(0xFFE5E6DE))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(cardBg)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(14.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "今日",
                style = TextStyle(color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = if (hasRecord) "更新 $updatedLabel" else "未同期",
                style = TextStyle(color = textSecondary, fontSize = 10.sp)
            )
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        Row(verticalAlignment = Alignment.Vertical.Bottom) {
            Text(
                text = if (hasRecord) formatter.format(steps) else "—",
                style = TextStyle(
                    color = textPrimary,
                    fontSize = if (isMedium) 30.sp else 24.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = "歩",
                style = TextStyle(color = textSecondary, fontSize = 12.sp)
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (isMedium) {
            val fillRatio = (steps.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = fillRatio,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = accent,
                backgroundColor = barBg
            )

            Spacer(modifier = GlanceModifier.height(6.dp))

            Text(
                text = when {
                    !hasRecord -> "タップして最新の歩数を取得"
                    remaining == 0L -> "目標 ${formatter.format(goal)} 歩を達成 ($percent%)"
                    else -> "目標まであと ${formatter.format(remaining)} 歩 ($percent%)"
                },
                style = TextStyle(color = textSecondary, fontSize = 11.sp)
            )
        } else {
            Text(
                text = if (hasRecord) "達成率 $percent%" else "タップして更新",
                style = TextStyle(color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            )
        }
    }
}
