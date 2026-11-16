package com.stepnote.app.domain.usecase

import com.stepnote.app.domain.model.DailyStepSummary
import com.stepnote.app.domain.model.DataStatus
import com.stepnote.app.domain.model.SourceScope
import com.stepnote.app.domain.model.StepSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CalculateStepStatsUseCaseTest {

    private val useCase = CalculateStepStatsUseCase()

    @Test
    fun `progressFraction clamps at 100 percent while exact percent allows exceeding 100`() {
        assertEquals(0f, useCase.progressFraction(0L, 10_000), 0.0001f)
        assertEquals(0.0001f, useCase.progressFraction(1L, 10_000), 0.0001f)
        assertEquals(0.0999f, useCase.progressFraction(999L, 10_000), 0.0001f)
        assertEquals(1.0f, useCase.progressFraction(10_000L, 10_000), 0.0001f)
        assertEquals(1.0f, useCase.progressFraction(24_500L, 10_000), 0.0001f)

        assertEquals(245, useCase.progressPercentExact(24_500L, 10_000))
    }

    @Test
    fun `percent never wraps around for a large valid count`() {
        assertEquals(Int.MAX_VALUE, useCase.progressPercentExact(Long.MAX_VALUE, 1_000))
    }

    @Test
    fun `calculatePeriodStats excludes unmeasured days from average denominator`() {
        val base = LocalDate.of(2026, 11, 1)
        val days = listOf(
            summary(base, 8_000L, DataStatus.MEASURED),
            summary(base.plusDays(1), 0L, DataStatus.CONFIRMED_ZERO),
            summary(base.plusDays(2), 0L, DataStatus.UNMEASURED),
            summary(base.plusDays(3), 12_000L, DataStatus.MEASURED)
        )

        val stats = useCase.calculatePeriodStats(days, goalOverride = 10_000)

        assertEquals(20_000L, stats.totalSteps)
        assertEquals(3, stats.validDayCount) // MEASURED (2) + CONFIRMED_ZERO (1), UNMEASURED excluded
        assertEquals(6_667L, stats.averageValidDaySteps) // 20000 / 3 = 6666.66 -> 6667
        assertEquals(12_000L, stats.maxDaySteps)
        assertEquals(1, stats.goalAchievedDays)
    }

    @Test
    fun `estimateCalories returns null without valid weight`() {
        assertNull(useCase.estimateCaloriesKcal(8_000L, 70, null))
        assertNull(useCase.estimateCaloriesKcal(8_000L, 70, 0f))
    }

    private fun summary(date: LocalDate, steps: Long, status: DataStatus) = DailyStepSummary(
        localDate = date,
        steps = steps,
        goal = 10_000,
        dataSource = StepSourceKind.HEALTH_CONNECT,
        sourceScope = SourceScope.THIS_PHONE_ONLY,
        fetchedAt = Instant.EPOCH,
        status = status
    )
}
