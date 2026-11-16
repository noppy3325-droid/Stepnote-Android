package com.stepnote.app.domain.usecase

import com.stepnote.app.data.health.SourceFetchResult
import com.stepnote.app.data.preferences.UserPreferencesRepository
import com.stepnote.app.data.repository.StepRepository
import com.stepnote.app.domain.model.DailyStepSummary
import com.stepnote.app.domain.model.HourlyStepSummary
import com.stepnote.app.domain.model.StepPeriodStats
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

enum class HistoryGranularity(val label: String) {
    DAY("日"),
    WEEK("週"),
    MONTH("月"),
    YEAR("年")
}

data class HistoryPeriodResult(
    val days: List<DailyStepSummary>,
    val hourlyForSelectedDay: List<HourlyStepSummary>,
    val stats: StepPeriodStats
)

class GetStepHistoryUseCase(
    private val repository: StepRepository,
    private val prefsRepository: UserPreferencesRepository,
    private val statsUseCase: CalculateStepStatsUseCase
) {
    suspend fun loadRange(
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        refreshRecent: Boolean = true
    ): HistoryPeriodResult {
        val prefs = prefsRepository.preferencesFlow.first()
        val activeSource = repository.resolveActiveSource(prefs)
        val kind = activeSource?.kind ?: prefs.preferredSourceKind
        val today = LocalDate.now(zoneId)

        val hourly = if (startDate == endDate && !startDate.isAfter(today)) {
            val cachedHourly = repository.getCachedHourly(startDate, kind, prefs.sourceScope, zoneId)
            if (activeSource != null && (refreshRecent || cachedHourly.isEmpty())) {
                when (val fetched = repository.syncDay(startDate, zoneId)) {
                    is SourceFetchResult.Success -> fetched.data.second
                    else -> cachedHourly
                }
            } else {
                cachedHourly
            }
        } else {
            emptyList()
        }

        if (startDate != endDate && refreshRecent && activeSource != null) {
            val syncEnd = minOf(endDate, today)
            val syncStart = if (activeSource.kind == com.stepnote.app.domain.model.StepSourceKind.LOCAL_RECORDING) {
                maxOf(startDate, today.minusDays(9))
            } else {
                startDate
            }
            if (!syncStart.isAfter(syncEnd)) {
                repository.syncRange(syncStart, syncEnd, zoneId)
            }
        }

        val cachedDays = repository.observeCachedRange(
            startDate,
            endDate,
            kind,
            prefs.sourceScope,
            zoneId
        ).first()

        val stats = statsUseCase.calculatePeriodStats(cachedDays, prefs.dailyGoal)
        return HistoryPeriodResult(
            days = cachedDays,
            hourlyForSelectedDay = hourly,
            stats = stats
        )
    }
}
