package com.stepnote.app.data.health

import android.Manifest
import android.content.Context
import com.google.android.gms.fitness.FitnessLocal
import com.google.android.gms.fitness.data.LocalDataType
import com.google.android.gms.fitness.data.LocalField
import com.google.android.gms.fitness.request.LocalDataReadRequest
import com.stepnote.app.domain.model.DailyStepSummary
import com.stepnote.app.domain.model.DataStatus
import com.stepnote.app.domain.model.HourlyStepSummary
import com.stepnote.app.domain.model.SourceScope
import com.stepnote.app.domain.model.StepSourceKind
import kotlinx.coroutines.tasks.await
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Fallback data source using Recording API on mobile (FitnessLocal).
 * Requires Google Play services and ACTIVITY_RECOGNITION permission; does NOT require a Google account.
 * Note: Recording API keeps ~10 days of local rolling data from subscription time, so StepRepository
 * snapshots daily/hourly summaries into Room periodically.
 */
class LocalRecordingStepSource(
    private val context: Context,
    private val capabilityChecker: DeviceCapabilityChecker
) : StepDataSource {

    override val kind: StepSourceKind = StepSourceKind.LOCAL_RECORDING

    override suspend fun isAvailable(scope: SourceScope): Boolean {
        val snapshot = capabilityChecker.inspectSync()
        return scope == SourceScope.THIS_PHONE_ONLY &&
            snapshot.isPlayServicesAvailable && snapshot.hasStepCounterSensor
    }

    override suspend fun hasRequiredPermissions(): Boolean {
        return capabilityChecker.inspectSync().hasActivityRecognition
    }

    override suspend fun hasBackgroundPermission(): Boolean {
        // Local Recording API collects passively in Google Play services once subscribed
        return hasRequiredPermissions()
    }

    private suspend fun ensureSubscribed(): SourceFetchResult<Unit> {
        if (!capabilityChecker.inspectSync().isPlayServicesAvailable) {
            return SourceFetchResult.Unavailable(
                reason = "Google Play services が利用できないため Recording API を開始できません。",
                suggestFallback = false
            )
        }
        if (!hasRequiredPermissions()) {
            return SourceFetchResult.PermissionMissing(
                missingPermissions = listOf(Manifest.permission.ACTIVITY_RECOGNITION),
                reason = "身体活動の認識権限（ACTIVITY_RECOGNITION）が必要です。"
            )
        }

        return fetchSteps("Recording API の購読開始に失敗しました。") {
            val client = FitnessLocal.getLocalRecordingClient(context)
            client.subscribe(LocalDataType.TYPE_STEP_COUNT_DELTA).await()
        }
    }

    override suspend fun fetchDailyTotal(
        date: LocalDate,
        zoneId: ZoneId,
        goal: Int,
        scope: SourceScope,
        asOf: Instant
    ): SourceFetchResult<DailyStepSummary> {
        val subResult = ensureSubscribed()
        if (subResult is SourceFetchResult.PermissionMissing) return subResult
        if (subResult is SourceFetchResult.Unavailable) return subResult
        if (subResult is SourceFetchResult.Failure) return subResult

        val (startMillis, endMillis) = readableRange(date, zoneId, asOf)
        if (startMillis >= endMillis) {
            return SourceFetchResult.Success(
                DailyStepSummary(date, 0, goal, kind, scope, asOf, DataStatus.UNMEASURED)
            )
        }

        return fetchSteps("Recording API からの歩数読み取りに失敗しました。") {
            val client = FitnessLocal.getLocalRecordingClient(context)
            val request = LocalDataReadRequest.Builder()
                .aggregate(LocalDataType.TYPE_STEP_COUNT_DELTA)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startMillis, endMillis, TimeUnit.MILLISECONDS)
                .build()

            val response = client.readData(request).await()
            var totalSteps = 0L
            var hasBucketData = false
            for (bucket in response.buckets) {
                for (dataSet in bucket.dataSets) {
                    for (dp in dataSet.dataPoints) {
                        hasBucketData = true
                        totalSteps += dp.getValue(LocalField.FIELD_STEPS).asInt().toLong()
                    }
                }
            }

            val status = when {
                !hasBucketData -> DataStatus.UNMEASURED
                totalSteps == 0L -> DataStatus.CONFIRMED_ZERO
                else -> DataStatus.MEASURED
            }

            DailyStepSummary(
                localDate = date,
                steps = totalSteps,
                goal = goal,
                dataSource = StepSourceKind.LOCAL_RECORDING,
                sourceScope = SourceScope.THIS_PHONE_ONLY,
                fetchedAt = asOf,
                status = status
            )
        }
    }

    override suspend fun fetchHourlyBreakdown(
        date: LocalDate,
        zoneId: ZoneId,
        scope: SourceScope,
        asOf: Instant
    ): SourceFetchResult<List<HourlyStepSummary>> {
        val subResult = ensureSubscribed()
        if (subResult is SourceFetchResult.PermissionMissing) return subResult
        if (subResult is SourceFetchResult.Unavailable) return subResult
        if (subResult is SourceFetchResult.Failure) return subResult

        val (startMillis, endMillis) = readableRange(date, zoneId, asOf)
        if (startMillis >= endMillis) return SourceFetchResult.Success(emptyList())

        return fetchSteps("Recording API 時間帯データの読み取りに失敗しました。") {
            val client = FitnessLocal.getLocalRecordingClient(context)
            val request = LocalDataReadRequest.Builder()
                .aggregate(LocalDataType.TYPE_STEP_COUNT_DELTA)
                .bucketByTime(1, TimeUnit.HOURS)
                .setTimeRange(startMillis, endMillis, TimeUnit.MILLISECONDS)
                .build()

            val response = client.readData(request).await()
            val byHour = mutableMapOf<Int, Long>()

            for (bucket in response.buckets) {
                val startInstant = Instant.ofEpochMilli(bucket.getStartTime(TimeUnit.MILLISECONDS))
                val hour = startInstant.atZone(zoneId).hour
                val points = bucket.dataSets.flatMap { it.dataPoints }
                if (points.isEmpty()) continue
                val bucketSteps = points.sumOf { it.getValue(LocalField.FIELD_STEPS).asInt().toLong() }
                byHour[hour] = (byHour[hour] ?: 0L) + bucketSteps
            }

            byHour.toSortedMap().map { (hour, steps) ->
                HourlyStepSummary(
                    localDate = date,
                    hour = hour,
                    steps = steps,
                    dataSource = StepSourceKind.LOCAL_RECORDING,
                    sourceScope = SourceScope.THIS_PHONE_ONLY,
                    fetchedAt = asOf
                )
            }
        }
    }

    private fun readableRange(date: LocalDate, zone: ZoneId, now: Instant): Pair<Long, Long> {
        val window = stepDayWindow(date, zone, now) ?: return 0L to 0L
        // Keep the oldest partial day from replacing a complete cached snapshot.
        if (window.start < now.minus(Duration.ofDays(10))) return 0L to 0L
        return window.start.toEpochMilli() to window.end.toEpochMilli()
    }

    override suspend fun fetchDailyRange(
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId,
        goal: Int,
        scope: SourceScope
    ): SourceFetchResult<List<DailyStepSummary>> {
        val results = mutableListOf<DailyStepSummary>()
        var cursor = startDate
        while (!cursor.isAfter(endDate)) {
            when (val res = fetchDailyTotal(cursor, zoneId, goal, scope)) {
                is SourceFetchResult.Success -> results.add(res.data)
                is SourceFetchResult.PermissionMissing -> return res
                is SourceFetchResult.Unavailable -> return res
                is SourceFetchResult.Failure -> return res
            }
            cursor = cursor.plusDays(1)
        }
        return SourceFetchResult.Success(results)
    }
}
