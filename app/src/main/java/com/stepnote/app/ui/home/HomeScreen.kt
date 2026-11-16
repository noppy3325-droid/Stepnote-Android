package com.stepnote.app.ui.home

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepnote.app.data.preferences.UserPreferencesRepository
import com.stepnote.app.domain.model.StepSourceKind
import com.stepnote.app.domain.model.StepUiState
import com.stepnote.app.domain.usecase.CalculateStepStatsUseCase
import com.stepnote.app.domain.usecase.GetTodayStepsUseCase
import com.stepnote.app.ui.components.HourlyStepBarChart
import com.stepnote.app.ui.components.StateNoticeCard
import com.stepnote.app.ui.components.StepProgressBar
import com.stepnote.app.ui.theme.LocalStepnoteColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeViewModel(
    private val getTodayStepsUseCase: GetTodayStepsUseCase,
    private val prefsRepository: UserPreferencesRepository,
    val statsUseCase: CalculateStepStatsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<StepUiState>(StepUiState.Loading)
    val uiState: StateFlow<StepUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = getTodayStepsUseCase()
        }
    }

    fun switchToRecordingFallback() {
        viewModelScope.launch {
            prefsRepository.setPreferredSourceKind(StepSourceKind.LOCAL_RECORDING)
            refresh()
        }
    }
}

@Composable
fun HomeScreen(
    uiState: StepUiState,
    statsUseCase: CalculateStepStatsUseCase,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPermissions: (StepSourceKind) -> Unit,
    onSwitchToRecordingFallback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalStepnoteColors.current
    val formatter = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
    val date = when (uiState) {
        is StepUiState.Content -> uiState.summary.localDate
        is StepUiState.Empty -> uiState.localDate
        else -> LocalDate.now()
    }
    val todayLabel = date.format(DateTimeFormatter.ofPattern("M月d日 (E)", Locale.JAPANESE))

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // 1. Date and minimal Settings icon (48dp touch target)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = todayLabel,
                color = colors.textSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "最新の歩数を読み込む",
                        tint = colors.textSecondary
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "設定を開く",
                        tint = colors.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (uiState) {
            is StepUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }

            is StepUiState.Content -> {
                val summary = uiState.summary
                val animatedSteps by animateIntAsState(
                    targetValue = summary.steps.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                    animationSpec = tween(durationMillis = 220),
                    label = "today_steps_anim"
                )
                val progress = statsUseCase.progressFraction(summary.steps, summary.goal)
                val percentExact = statsUseCase.progressPercentExact(summary.steps, summary.goal)
                val remaining = (summary.goal - summary.steps).coerceAtLeast(0L)

                // 2. Hero step count
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatter.format(if (summary.steps > Int.MAX_VALUE) summary.steps else animatedSteps.toLong()),
                        color = colors.textPrimary,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-1.5).sp,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "歩",
                        color = colors.textSecondary,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Slim progress bar
                StepProgressBar(progress = progress, percentText = percentExact)

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Remaining steps / subtle achievement text
                Text(
                    text = if (remaining == 0L) {
                        "目標の ${formatter.format(summary.goal)} 歩を達成しました ($percentExact%)"
                    } else {
                        "目標 ${formatter.format(summary.goal)} 歩まであと ${formatter.format(remaining)} 歩 ($percentExact%)"
                    },
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 5. Hourly chart (0-24h)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HourlyStepBarChart(
                        buckets = uiState.hourlyBuckets,
                        modifier = Modifier.padding(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 6. Estimated distance (+ optional calories)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val distanceKm = uiState.estimatedDistanceMeters / 1000.0
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.card,
                        border = BorderStroke(1.dp, colors.hairline),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "推定移動距離",
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = String.format(Locale.getDefault(), "%.2f km", distanceKm),
                                color = colors.textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (uiState.estimatedCaloriesKcal != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.card,
                            border = BorderStroke(1.dp, colors.hairline),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "推定消費エネルギー",
                                    color = colors.textSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = String.format(
                                        Locale.getDefault(),
                                        "%.0f kcal",
                                        uiState.estimatedCaloriesKcal
                                    ),
                                    color = colors.textPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 7. Quiet freshness & source attribution
                val timeStr = summary.fetchedAt
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                Text(
                    text = "最終更新: $timeStr · 取得元: ${summary.sourceScope.label} (${summary.dataSource.label})",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )

                if (!uiState.backgroundReadGranted && summary.dataSource == StepSourceKind.HEALTH_CONNECT) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "バックグラウンド読み取り未許可のため、ウィジェット更新はアプリ起動時に行われます。",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            is StepUiState.Empty -> {
                Text(
                    text = "—",
                    color = colors.textSecondary,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))
                StateNoticeCard(
                    title = "まだ今日の記録がありません",
                    description = uiState.message,
                    primaryLabel = "再読み込み",
                    onPrimaryClick = onRefresh
                )
            }

            is StepUiState.PermissionRequired -> {
                StateNoticeCard(
                    title = uiState.title,
                    description = "${uiState.reason}\n許可は端末の設定から変更できます。保存済みの履歴は設定から削除できます。",
                    primaryLabel = "権限を許可する",
                    onPrimaryClick = { onRequestPermissions(uiState.activeSource) },
                    secondaryLabel = "設定・診断を見る",
                    onSecondaryClick = onOpenSettings
                )
            }

            is StepUiState.Unsupported -> {
                StateNoticeCard(
                    title = uiState.title,
                    description = uiState.reason,
                    primaryLabel = if (uiState.canFallbackToRecordingApi) {
                        "Recording API (端末内) に切り替える"
                    } else {
                        "診断情報を確認"
                    },
                    onPrimaryClick = {
                        if (uiState.canFallbackToRecordingApi) {
                            onSwitchToRecordingFallback()
                        } else {
                            onOpenSettings()
                        }
                    }
                )
            }

            is StepUiState.Error -> {
                StateNoticeCard(
                    title = "歩数の読み込みに失敗しました",
                    description = buildString {
                        append(uiState.message)
                        uiState.lastKnownSummary?.let { cached ->
                            val time = cached.fetchedAt
                                .atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("HH:mm"))
                            append("\n前回の正常値 ($time): ${formatter.format(cached.steps)} 歩")
                        }
                    },
                    primaryLabel = "再試行",
                    onPrimaryClick = onRefresh,
                    secondaryLabel = "診断を開く",
                    onSecondaryClick = onOpenSettings
                )
            }
        }
    }
}
