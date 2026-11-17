package com.stepnote.app.domain.usecase

import com.stepnote.app.domain.model.DailyStepSummary
import com.stepnote.app.domain.model.DataStatus
import com.stepnote.app.domain.model.StepPeriodStats
import kotlin.math.roundToLong
import kotlin.math.roundToInt

class CalculateStepStatsUseCase {

    fun estimateStrideCm(heightCm: Int?, fallbackStrideCm: Int): Int =
        heightCm?.takeIf { it in 80..250 }?.let { (it * 0.415).roundToInt() }
            ?: fallbackStrideCm.coerceIn(30, 140)

    fun estimateDistanceMeters(steps: Long, strideCm: Int): Double {
        val safeSteps = steps.coerceAtLeast(0L)
        val safeStrideCm = strideCm.coerceIn(30, 140)
        return safeSteps * (safeStrideCm / 100.0)
    }

    /**
     * Only calculates calories when the user explicitly opts in and provides body weight.
     * Walking estimate based on distance and weight.
     */
    fun estimateCaloriesKcal(
        steps: Long,
        strideCm: Int,
        weightKg: Float?
    ): Double? {
        if (weightKg == null || !weightKg.isFinite() || weightKg <= 20f || weightKg >= 300f) return null
        val distanceKm = estimateDistanceMeters(steps, strideCm) / 1000.0
        // Walking metabolic approximation: ~0.55 kcal * weightKg * distanceKm
        return distanceKm * weightKg * 0.55
    }

    fun progressFraction(steps: Long, goal: Int): Float {
        if (goal <= 0) return 0f
        return (steps.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
    }

    fun progressPercentExact(steps: Long, goal: Int): Int {
        if (goal <= 0) return 0
        return ((steps.coerceAtLeast(0L).toDouble() / goal) * 100.0).roundToInt()
    }

    /**
     * Computes period summary using ONLY days with valid measured or confirmed-zero records
     * as the denominator so unmeasured days do not drag down the average.
     */
    fun calculatePeriodStats(
        days: List<DailyStepSummary>,
        goalOverride: Int? = null
    ): StepPeriodStats {
        val validDays = days.filter {
            it.status == DataStatus.MEASURED || it.status == DataStatus.CONFIRMED_ZERO
        }
        val totalSteps = validDays.sumOf { it.steps }
        val validCount = validDays.size
        val average = if (validCount > 0) {
            (totalSteps.toDouble() / validCount.toDouble()).roundToLong()
        } else {
            0L
        }
        val maxDay = validDays.maxByOrNull { it.steps }
        val goalAchievedDays = validDays.count { summary ->
            val target = (goalOverride ?: summary.goal).coerceAtLeast(1)
            summary.steps >= target
        }

        return StepPeriodStats(
            totalSteps = totalSteps,
            validDayCount = validCount,
            totalDayCount = days.size,
            averageValidDaySteps = average,
            maxDaySteps = maxDay?.steps ?: 0L,
            maxDayLabel = maxDay?.localDate?.toString(),
            goalAchievedDays = goalAchievedDays
        )
    }
}
