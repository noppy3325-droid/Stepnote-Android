package com.stepnote.app.data.health

import android.content.Context
import android.health.connect.DeviceDataSource
import android.health.connect.HealthConnectException
import android.health.connect.HealthConnectManager
import android.os.Build
import android.os.OutcomeReceiver
import androidx.annotation.RequiresExtension
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.stepnote.app.domain.model.DailyStepSummary
import com.stepnote.app.domain.model.DataStatus
import com.stepnote.app.domain.model.HourlyStepSummary
import com.stepnote.app.domain.model.SourceScope
import com.stepnote.app.domain.model.StepSourceKind
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.resume

class HealthConnectStepSource(
    private val context: Context,
    private val capabilityChecker: DeviceCapabilityChecker
) : StepDataSource {

    override val kind: StepSourceKind = StepSourceKind.HEALTH_CONNECT

    private fun getClientOrNull(): HealthConnectClient? {
        return if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    override suspend fun isAvailable(scope: SourceScope): Boolean {
        val snapshot = capabilityChecker.inspectSync()
        if (snapshot.healthConnectSdkStatus != HealthConnectClient.SDK_AVAILABLE) return false
        return when (scope) {
            SourceScope.ALL_HEALTH_CONNECT -> true
            SourceScope.THIS_PHONE_ONLY -> {
                // Must have device auto-step collection (Extension >= 20) AND origin resolution
                snapshot.hasStepCounterSensor &&
                    snapshot.supportsDeviceAutoSteps && snapshot.supportsDeviceDataOriginResolver
            }
        }
    }

    override suspend fun hasRequiredPermissions(): Boolean {
        val granted = capabilityChecker.getHealthConnectGrantedPermissions()
        return granted.contains(DeviceCapabilityChecker.READ_STEPS_PERMISSION)
    }

    override suspend fun hasBackgroundPermission(): Boolean {
        val snapshot = capabilityChecker.inspectSync()
        if (!snapshot.supportsBackgroundReadFeature) return false
        val granted = capabilityChecker.getHealthConnectGrantedPermissions()
        return granted.contains(DeviceCapabilityChecker.READ_BACKGROUND_PERMISSION)
    }

    /**
     * Resolves the current device's Synthetic Package Name (SPN) dynamically without hardcoding.
     * Uses the current device origin and the historical platform origin.
     */
    private suspend fun resolveDeviceDataOriginFilter(scope: SourceScope): Set<DataOrigin>? {
        if (scope == SourceScope.ALL_HEALTH_CONNECT) {
            return emptySet() // Unfiltered: Health Connect deduplicates across all sources
        }

        val snapshot = capabilityChecker.inspectSync()
        if (!snapshot.supportsDeviceDataOriginResolver) {
            return null // Do NOT silently widen "this phone only" to all sources
        }

        val resolvedPackage = withTimeoutOrNull(5_000) { queryCurrentDeviceSpn() } ?: return null
        return setOf(
            DataOrigin(resolvedPackage),
            DataOrigin("android")
        )
    }

    @RequiresExtension(extension = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, version = 22)
    private suspend fun queryCurrentDeviceSpn(): String? {
        val manager = context.getSystemService(HealthConnectManager::class.java) ?: return null

        return suspendCancellableCoroutine { continuation ->
            manager.getCurrentDeviceDataSource(
                context.mainExecutor,
                object : OutcomeReceiver<DeviceDataSource, HealthConnectException> {
                    override fun onResult(result: DeviceDataSource) {
                        continuation.resume(result.deviceDataOrigin.packageName.takeIf { it.isNotBlank() })
                    }

                    override fun onError(error: HealthConnectException) {
                        continuation.resume(null)
                    }
                }
            )
        }
    }

    override suspend fun fetchDailyTotal(
        date: LocalDate,
        zoneId: ZoneId,
        goal: Int,
        scope: SourceScope,
        asOf: Instant
    ): SourceFetchResult<DailyStepSummary> = withOrigins(scope) { client, origins ->
        aggregateDay(client, origins, date, zoneId, goal, scope, asOf)
    }

    private suspend fun <T> withOrigins(
        scope: SourceScope,
        query: suspend (HealthConnectClient, Set<DataOrigin>) -> T
    ): SourceFetchResult<T> {
        return fetchSteps("Health Connect の歩数を取得できませんでした。") {
            val client = getClientOrNull() ?: return SourceFetchResult.Unavailable(
                "Health Connect が利用できません。端末の更新状況を確認してください。", true
            )
            if (!hasRequiredPermissions()) return SourceFetchResult.PermissionMissing(
                listOf(DeviceCapabilityChecker.READ_STEPS_PERMISSION),
                "Health Connect で歩数の読み取りを許可してください。"
            )
            val origins = resolveDeviceDataOriginFilter(scope) ?: return SourceFetchResult.Unavailable(
                "Health Connect でこのスマホを識別できませんでした。再読み込みするか、端末内の記録方式を選べます。",
                true
            )
            query(client, origins)
        }
    }

    private suspend fun aggregateDay(
        client: HealthConnectClient,
        origins: Set<DataOrigin>,
        date: LocalDate,
        zoneId: ZoneId,
        goal: Int,
        scope: SourceScope,
        now: Instant
    ): DailyStepSummary {
        val window = stepDayWindow(date, zoneId, now)
        val count = if (window != null) {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(window.start, window.end),
                    dataOriginFilter = origins
                )
            )[StepsRecord.COUNT_TOTAL]
        } else {
            null
        }
        val status = when (count) {
            null -> DataStatus.UNMEASURED
            0L -> DataStatus.CONFIRMED_ZERO
            else -> DataStatus.MEASURED
        }
        return DailyStepSummary(date, count ?: 0, goal, kind, scope, now, status)
    }

    override suspend fun fetchHourlyBreakdown(
        date: LocalDate,
        zoneId: ZoneId,
        scope: SourceScope,
        asOf: Instant
    ): SourceFetchResult<List<HourlyStepSummary>> = withOrigins(scope) { client, origins ->
        val window = stepDayWindow(date, zoneId, asOf)
        if (window == null) {
            emptyList()
        } else {
            val buckets = client.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(window.start, window.end),
                    timeRangeSlicer = Duration.ofHours(1),
                    dataOriginFilter = origins
                )
            )

            val mapByHour = mutableMapOf<Int, Long>()
            for (bucket in buckets) {
                val localHour = bucket.startTime.atZone(zoneId).hour
                val value = bucket.result[StepsRecord.COUNT_TOTAL] ?: continue
                mapByHour[localHour] = (mapByHour[localHour] ?: 0L) + value
            }

            mapByHour.toSortedMap().map { (hour, steps) ->
                HourlyStepSummary(
                    localDate = date,
                    hour = hour,
                    steps = steps,
                    dataSource = StepSourceKind.HEALTH_CONNECT,
                    sourceScope = scope,
                    fetchedAt = asOf
                )
            }
        }
    }

    override suspend fun fetchDailyRange(
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId,
        goal: Int,
        scope: SourceScope
    ): SourceFetchResult<List<DailyStepSummary>> = withOrigins(scope) { client, origins ->
        val now = Instant.now()
        buildList {
            var date = endDate
            while (date >= startDate) {
                try {
                    add(aggregateDay(client, origins, date, zoneId, goal, scope, now))
                } catch (error: SecurityException) {
                    // Older intervals may precede the Health Connect history limit.
                    if (!hasRequiredPermissions() || date >= now.atZone(zoneId).toLocalDate().minusDays(29)) {
                        throw error
                    }
                    break
                }
                date = date.minusDays(1)
            }
        }
    }
}
