package com.stepnote.app.data.health

import com.stepnote.app.domain.model.DailyStepSummary
import com.stepnote.app.domain.model.HourlyStepSummary
import com.stepnote.app.domain.model.SourceScope
import com.stepnote.app.domain.model.StepSourceKind
import com.stepnote.app.domain.model.StepInterval
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId

sealed interface SourceFetchResult<out T> {
    data class Success<T>(val data: T) : SourceFetchResult<T>
    data class PermissionMissing(val missingPermissions: List<String>, val reason: String) : SourceFetchResult<Nothing>
    data class Unavailable(val reason: String, val suggestFallback: Boolean) : SourceFetchResult<Nothing>
    data class Failure(val message: String, val cause: Throwable? = null) : SourceFetchResult<Nothing>
}

internal inline fun <T> fetchSteps(errorMessage: String, read: () -> T): SourceFetchResult<T> {
    return try {
        SourceFetchResult.Success(read())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        SourceFetchResult.Failure(errorMessage, error)
    }
}

interface StepDataSource {
    val kind: StepSourceKind

    suspend fun isAvailable(scope: SourceScope): Boolean
    suspend fun hasRequiredPermissions(): Boolean
    suspend fun hasBackgroundPermission(): Boolean

    suspend fun prepareInterval(scope: SourceScope): SourceFetchResult<Unit>

    suspend fun fetchInterval(interval: StepInterval, scope: SourceScope): SourceFetchResult<Long?>

    suspend fun fetchDailyTotal(
        date: LocalDate,
        zoneId: ZoneId,
        goal: Int,
        scope: SourceScope,
        asOf: Instant = Instant.now()
    ): SourceFetchResult<DailyStepSummary>

    suspend fun fetchHourlyBreakdown(
        date: LocalDate,
        zoneId: ZoneId,
        scope: SourceScope,
        asOf: Instant = Instant.now()
    ): SourceFetchResult<List<HourlyStepSummary>>

    suspend fun fetchDailyRange(
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId,
        goal: Int,
        scope: SourceScope
    ): SourceFetchResult<List<DailyStepSummary>>
}
