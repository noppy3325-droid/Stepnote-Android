package com.stepnote.app.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepnote.app.data.preferences.UserPreferencesRepository
import com.stepnote.app.domain.model.DailyStepSummary
import com.stepnote.app.domain.model.DataStatus
import com.stepnote.app.domain.model.HourlyStepSummary
import com.stepnote.app.domain.usecase.CalculateStepStatsUseCase
import com.stepnote.app.domain.usecase.GetStepHistoryUseCase
import com.stepnote.app.ui.components.HourlyStepBarChart
import com.stepnote.app.ui.theme.LocalStepnoteColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CalendarUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val daysMap: Map<LocalDate, DailyStepSummary> = emptyMap(),
    val selectedHourly: List<HourlyStepSummary> = emptyList(),
    val strideCm: Int = 70,
    val dailyGoal: Int = 10_000
)

class CalendarViewModel(
    private val getStepHistoryUseCase: GetStepHistoryUseCase,
    private val prefsRepository: UserPreferencesRepository,
    val statsUseCase: CalculateStepStatsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun refresh() {
        val state = _uiState.value
        loadMonth(state.currentMonth, state.selectedDate)
    }

    fun moveMonth(delta: Long) {
        val next = _uiState.value.currentMonth.plusMonths(delta)
        if (next.isAfter(YearMonth.now())) return
        loadMonth(next, next.atDay(1))
    }

    fun selectDate(date: LocalDate) {
        loadMonth(_uiState.value.currentMonth, date, refreshMonth = false)
    }

    private fun loadMonth(month: YearMonth, selected: LocalDate, refreshMonth: Boolean = true) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val prefs = prefsRepository.preferencesFlow.first()
            val selectedDayData = getStepHistoryUseCase.loadRange(selected, selected, refreshRecent = false)
            val monthData = getStepHistoryUseCase.loadRange(
                month.atDay(1), month.atEndOfMonth(), refreshRecent = refreshMonth
            )

            _uiState.value = CalendarUiState(
                currentMonth = month,
                selectedDate = selected,
                daysMap = monthData.days.associateBy { it.localDate },
                selectedHourly = selectedDayData.hourlyForSelectedDay,
                strideCm = statsUseCase.estimateStrideCm(prefs.heightCm, prefs.strideCm),
                dailyGoal = prefs.dailyGoal
            )
        }
    }
}

@Composable
fun CalendarScreen(
    uiState: CalendarUiState,
    statsUseCase: CalculateStepStatsUseCase,
    onMoveMonth: (Long) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalStepnoteColors.current
    val formatter = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }

    val firstDayOfMonth = uiState.currentMonth.atDay(1)
    val startOffset = (firstDayOfMonth.dayOfWeek.value - 1) // Monday = 0
    val daysInMonth = uiState.currentMonth.lengthOfMonth()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = uiState.currentMonth.format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.JAPANESE)),
                color = colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row {
                IconButton(onClick = { onMoveMonth(-1) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = "前の月",
                        tint = colors.textPrimary
                    )
                }
                IconButton(onClick = { onMoveMonth(1) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "次の月",
                        tint = colors.textPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("月", "火", "水", "木", "金", "土", "日").forEach { dow ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = dow, color = colors.textSecondary, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val totalCells = startOffset + daysInMonth
        val rows = (totalCells + 6) / 7
        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val cellIdx = r * 7 + c
                    val dayNum = cellIdx - startOffset + 1
                    if (dayNum in 1..daysInMonth) {
                        val date = uiState.currentMonth.atDay(dayNum)
                        val record = uiState.daysMap[date]
                        val isSelected = date == uiState.selectedDate
                        val isMeasured = record != null && record.status != DataStatus.UNMEASURED
                        val goalReached = isMeasured && (record?.steps ?: 0L) >= uiState.dailyGoal
                        val dailyStatus = when {
                            goalReached -> "目標達成"
                            isMeasured -> "記録あり"
                            else -> "未取得"
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) colors.accent.copy(alpha = 0.16f) else colors.background
                                )
                                .clickable { onSelectDate(date) }
                                .semantics {
                                    contentDescription = "${date.monthValue}月${date.dayOfMonth}日 $dailyStatus"
                                    selected = isSelected
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = dayNum.toString(),
                                color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            when {
                                goalReached -> Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent)
                                )
                                isMeasured -> Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(colors.textSecondary.copy(alpha = 0.45f))
                                )
                                else -> Spacer(modifier = Modifier.size(5.dp))
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val selectedRecord = uiState.daysMap[uiState.selectedDate]
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = uiState.selectedDate.format(DateTimeFormatter.ofPattern("M月d日 (E)", Locale.JAPANESE)),
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (selectedRecord == null || selectedRecord.status == DataStatus.UNMEASURED) {
                    Text(
                        text = "未計測の日です",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "記録が存在しないため、未達成日数や平均計算には含めません。",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                } else {
                    val pct = statsUseCase.progressPercentExact(selectedRecord.steps, uiState.dailyGoal)
                    val distKm = statsUseCase.estimateDistanceMeters(selectedRecord.steps, uiState.strideCm) / 1000.0
                    Text(
                        text = "${formatter.format(selectedRecord.steps)} 歩",
                        color = colors.textPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "目標比率 $pct% · 推定距離 ${String.format(Locale.getDefault(), "%.2f", distKm)} km",
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    HourlyStepBarChart(buckets = uiState.selectedHourly)
                }
            }
        }
    }
}
