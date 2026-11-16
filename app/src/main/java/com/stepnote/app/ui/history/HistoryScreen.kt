package com.stepnote.app.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepnote.app.domain.model.DataStatus
import com.stepnote.app.domain.model.HourlyStepSummary
import com.stepnote.app.domain.model.StepPeriodStats
import com.stepnote.app.domain.usecase.GetStepHistoryUseCase
import com.stepnote.app.domain.usecase.HistoryGranularity
import com.stepnote.app.ui.components.HourlyStepBarChart
import com.stepnote.app.ui.theme.LocalStepnoteColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class HistoryBarPoint(
    val label: String,
    val detailLabel: String,
    val steps: Long?,
    val status: DataStatus
)

data class HistoryUiState(
    val granularity: HistoryGranularity = HistoryGranularity.WEEK,
    val anchorDate: LocalDate = LocalDate.now(),
    val periodTitle: String = "",
    val bars: List<HistoryBarPoint> = emptyList(),
    val hourlyBuckets: List<HourlyStepSummary> = emptyList(),
    val stats: StepPeriodStats = StepPeriodStats(0, 0, 0, 0, 0, null, 0),
    val isLoading: Boolean = false
)

class HistoryViewModel(
    private val getStepHistoryUseCase: GetStepHistoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun setGranularity(granularity: HistoryGranularity) {
        _uiState.value = _uiState.value.copy(granularity = granularity)
        load()
    }

    fun movePeriod(delta: Long) {
        val current = _uiState.value
        val nextAnchor = when (current.granularity) {
            HistoryGranularity.DAY -> current.anchorDate.plusDays(delta)
            HistoryGranularity.WEEK -> current.anchorDate.plusWeeks(delta)
            HistoryGranularity.MONTH -> current.anchorDate.plusMonths(delta)
            HistoryGranularity.YEAR -> current.anchorDate.plusYears(delta)
        }
        if (nextAnchor.isAfter(LocalDate.now())) return
        _uiState.value = current.copy(anchorDate = nextAnchor)
        load()
    }

    fun load() {
        val state = _uiState.value
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = state.copy(
                isLoading = true, periodTitle = "", bars = emptyList(), hourlyBuckets = emptyList(),
                stats = StepPeriodStats(0, 0, 0, 0, 0, null, 0)
            )
            val anchor = state.anchorDate
            val (start, end, title) = when (state.granularity) {
                HistoryGranularity.DAY -> Triple(
                    anchor,
                    anchor,
                    anchor.format(DateTimeFormatter.ofPattern("yyyy年M月d日 (E)", Locale.JAPANESE))
                )
                HistoryGranularity.WEEK -> {
                    val weekStart = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val weekEnd = weekStart.plusDays(6)
                    val fmt = DateTimeFormatter.ofPattern("M/d")
                    Triple(weekStart, weekEnd, "${weekStart.format(fmt)} 〜 ${weekEnd.format(fmt)}")
                }
                HistoryGranularity.MONTH -> {
                    val ym = YearMonth.from(anchor)
                    Triple(
                        ym.atDay(1),
                        ym.atEndOfMonth(),
                        anchor.format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.JAPANESE))
                    )
                }
                HistoryGranularity.YEAR -> {
                    val yStart = LocalDate.of(anchor.year, 1, 1)
                    val yEnd = LocalDate.of(anchor.year, 12, 31)
                    Triple(yStart, yEnd, "${anchor.year}年")
                }
            }

            val result = getStepHistoryUseCase.loadRange(start, end)
            val byDate = result.days.associateBy { it.localDate }

            val bars = when (state.granularity) {
                HistoryGranularity.DAY -> emptyList()
                HistoryGranularity.WEEK -> {
                    (0L..6L).map { offset ->
                        val d = start.plusDays(offset)
                        val rec = byDate[d]
                        HistoryBarPoint(
                            label = d.format(DateTimeFormatter.ofPattern("E", Locale.JAPANESE)),
                            detailLabel = d.format(DateTimeFormatter.ofPattern("M/d (E)", Locale.JAPANESE)),
                            steps = rec?.takeIf { it.status != DataStatus.UNMEASURED }?.steps,
                            status = rec?.status ?: DataStatus.UNMEASURED
                        )
                    }
                }
                HistoryGranularity.MONTH -> {
                    val daysInMonth = YearMonth.from(anchor).lengthOfMonth()
                    (1..daysInMonth).map { day ->
                        val d = LocalDate.of(anchor.year, anchor.month, day)
                        val rec = byDate[d]
                        HistoryBarPoint(
                            label = if (day == 1 || day % 5 == 0) day.toString() else "",
                            detailLabel = "${anchor.monthValue}月${day}日",
                            steps = rec?.takeIf { it.status != DataStatus.UNMEASURED }?.steps,
                            status = rec?.status ?: DataStatus.UNMEASURED
                        )
                    }
                }
                HistoryGranularity.YEAR -> {
                    (1..12).map { month ->
                        val monthRecords = result.days.filter {
                            it.localDate.monthValue == month && it.status != DataStatus.UNMEASURED
                        }
                        val sum = if (monthRecords.isEmpty()) null else monthRecords.sumOf { it.steps }
                        HistoryBarPoint(
                            label = "${month}月",
                            detailLabel = "${anchor.year}年${month}月",
                            steps = sum,
                            status = if (sum == null) DataStatus.UNMEASURED else DataStatus.MEASURED
                        )
                    }
                }
            }

            _uiState.value = state.copy(
                periodTitle = title,
                bars = bars,
                hourlyBuckets = result.hourlyForSelectedDay,
                stats = result.stats,
                isLoading = false
            )
        }
    }
}

@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onGranularityChange: (HistoryGranularity) -> Unit,
    onMovePeriod: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalStepnoteColors.current
    val formatter = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
    var selectedBarIndex by remember(uiState.bars) { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "履歴",
            color = colors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(14.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            HistoryGranularity.entries.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = uiState.granularity == item,
                    onClick = { onGranularityChange(item) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = HistoryGranularity.entries.size
                    )
                ) {
                    Text(text = item.label)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onMovePeriod(-1) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "前の期間",
                    tint = colors.textPrimary
                )
            }
            Text(
                text = uiState.periodTitle,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            IconButton(
                onClick = { onMovePeriod(1) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "次の期間",
                    tint = colors.textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            return@Column
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.granularity == HistoryGranularity.DAY) {
                HourlyStepBarChart(
                    buckets = uiState.hourlyBuckets,
                    modifier = Modifier.padding(18.dp)
                )
            } else {
                Column(modifier = Modifier.padding(18.dp)) {
                    val activeBar = selectedBarIndex?.let { uiState.bars.getOrNull(it) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = activeBar?.detailLabel ?: "棒をタップして詳細を表示",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = when {
                                activeBar == null -> ""
                                activeBar.steps == null -> "未取得"
                                else -> "${formatter.format(activeBar.steps)} 歩"
                            },
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val maxVal = (uiState.bars.mapNotNull { it.steps }.maxOrNull() ?: 0L).coerceAtLeast(1000L)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .pointerInput(uiState.bars) {
                                detectTapGestures { offset ->
                                    if (uiState.bars.isNotEmpty()) {
                                        val slot = size.width.toFloat() / uiState.bars.size
                                        val idx = (offset.x / slot).toInt().coerceIn(0, uiState.bars.lastIndex)
                                        selectedBarIndex = if (selectedBarIndex == idx) null else idx
                                    }
                                }
                            }
                            .semantics {
                                contentDescription = if (activeBar == null) {
                                    "期間別歩数グラフ"
                                } else {
                                    val steps = activeBar.steps?.let { "${formatter.format(it)} 歩" } ?: "未取得"
                                    "${activeBar.detailLabel} $steps"
                                }
                                if (uiState.bars.isNotEmpty()) {
                                    customActions = listOf(
                                        CustomAccessibilityAction("前の項目") {
                                            selectedBarIndex = ((selectedBarIndex ?: 0) - 1).mod(uiState.bars.size)
                                            true
                                        },
                                        CustomAccessibilityAction("次の項目") {
                                            selectedBarIndex = ((selectedBarIndex ?: -1) + 1).mod(uiState.bars.size)
                                            true
                                        }
                                    )
                                }
                            }
                    ) {
                        val count = uiState.bars.size.coerceAtLeast(1)
                        val slot = size.width / count
                        val barW = (slot * 0.58f).coerceAtLeast(4f)
                        val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

                        uiState.bars.forEachIndexed { idx, point ->
                            val x = idx * slot + (slot - barW) / 2f
                            if (point.steps == null) {
                                drawRoundRect(
                                    color = colors.barTrack.copy(alpha = 0.4f),
                                    topLeft = Offset(x, size.height - 3.dp.toPx()),
                                    size = Size(barW, 3.dp.toPx()),
                                    cornerRadius = radius
                                )
                            } else {
                                val ratio = (point.steps.toFloat() / maxVal.toFloat()).coerceIn(0.05f, 1f)
                                val h = size.height * ratio
                                val c = if (selectedBarIndex == null || selectedBarIndex == idx) {
                                    colors.accent
                                } else {
                                    colors.accent.copy(alpha = 0.35f)
                                }
                                drawRoundRect(
                                    color = c,
                                    topLeft = Offset(x, size.height - h),
                                    size = Size(barW, h),
                                    cornerRadius = radius
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        uiState.bars.forEach { bar ->
                            Text(
                                text = bar.label,
                                color = colors.textSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Summary metrics (valid days denominator explicitly noted)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                label = "期間合計",
                value = if (uiState.stats.validDayCount == 0) "—" else "${formatter.format(uiState.stats.totalSteps)} 歩",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "日平均 (${uiState.stats.validDayCount}日)",
                value = if (uiState.stats.validDayCount == 0) "—" else "${formatter.format(uiState.stats.averageValidDaySteps)} 歩",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                label = "最大記録",
                value = if (uiState.stats.validDayCount == 0) "—" else "${formatter.format(uiState.stats.maxDaySteps)} 歩",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "目標達成日数",
                value = "${uiState.stats.goalAchievedDays} 日",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalStepnoteColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.hairline),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, color = colors.textSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
