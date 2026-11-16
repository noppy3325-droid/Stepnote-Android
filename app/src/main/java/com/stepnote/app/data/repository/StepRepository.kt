package com.stepnote.app.data.repository

import androidx.health.connect.client.HealthConnectClient
import com.stepnote.app.data.health.DeviceCapabilityChecker
import com.stepnote.app.data.health.HealthConnectStepSource
import com.stepnote.app.data.health.LocalRecordingStepSource
import com.stepnote.app.data.health.SourceFetchResult
import com.stepnote.app.data.health.StepDataSource
import com.stepnote.app.data.local.DailyStepEntity
import com.stepnote.app.data.local.HourlyStepEntity
import com.stepnote.app.data.local.StepDao
import com.stepnote.app.data.preferences.UserPreferences
import com.stepnote.app.data.preferences.UserPreferencesRepository
import com.stepnote.app.domain.model.DailyStepSummary
import com.stepnote.app.domain.model.DataStatus
import com.stepnote.app.domain.model.EnvironmentDiagnostic
import com.stepnote.app.domain.model.HourlyStepSummary
import com.stepnote.app.domain.model.SourceScope
import com.stepnote.app.domain.model.StepSourceKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StepRepository(
    private val stepDao: StepDao,
    private val prefsRepo: UserPreferencesRepository,
    private val capabilityChecker: DeviceCapabilityChecker,
    private val healthConnectSource: HealthConnectStepSource,
    private val localRecordingSource: LocalRecordingStepSource
) {
    private val syncLock = Mutex()

    suspend fun resolveActiveSource(prefs: UserPreferences): StepDataSource? {
        if (prefs.sourceScope == SourceScope.ALL_HEALTH_CONNECT) {
            return healthConnectSource.takeIf { it.isAvailable(prefs.sourceScope) }
        }
        return when (prefs.preferredSourceKind) {
            StepSourceKind.HEALTH_CONNECT -> {
                if (healthConnectSource.isAvailable(prefs.sourceScope)) {
                    healthConnectSource
                } else if (
                    prefs.sourceScope == SourceScope.THIS_PHONE_ONLY &&
                    localRecordingSource.isAvailable(prefs.sourceScope)
                ) {
                    localRecordingSource
                } else {
                    null
                }
            }
            StepSourceKind.LOCAL_RECORDING -> {
                if (localRecordingSource.isAvailable(prefs.sourceScope)) {
                    localRecordingSource
                } else {
                    null
                }
            }
            StepSourceKind.NONE -> null
        }
    }

    /**
     * Fetches fresh aggregated totals for [date] and idempotently replaces the Room snapshot.
     * Never adds on top of existing cached values.
     */
    suspend fun syncDay(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        background: Boolean = false
    ): SourceFetchResult<Pair<DailyStepSummary, List<HourlyStepSummary>>> = syncLock.withLock {
        val prefs = prefsRepo.preferencesFlow.first()
        val source = resolveActiveSource(prefs)
            ?: return SourceFetchResult.Unavailable(
                reason = "この端末では対応する歩数記録基盤（Health Connect Extension>=20/22 または Recording API）が利用できません。",
                suggestFallback = localRecordingSource.isAvailable(prefs.sourceScope)
            )

        if (background && !source.hasBackgroundPermission()) {
            return SourceFetchResult.PermissionMissing(emptyList(), "バックグラウンド読み取りの許可が必要です。")
        }

        val asOf = Instant.now()
        val dailyResult = withTimeoutOrNull(12_000) {
            source.fetchDailyTotal(date, zoneId, prefs.dailyGoal, prefs.sourceScope, asOf)
        } ?: SourceFetchResult.Failure("歩数の読み取りがタイムアウトしました。")
        val summary = when (dailyResult) {
            is SourceFetchResult.Success -> dailyResult.data
            is SourceFetchResult.PermissionMissing -> return dailyResult
            is SourceFetchResult.Unavailable -> return dailyResult
            is SourceFetchResult.Failure -> return dailyResult
        }

        val cached = getCachedDaily(date, source.kind, prefs.sourceScope, zoneId)
        if (summary.status == DataStatus.UNMEASURED && source.kind == StepSourceKind.LOCAL_RECORDING &&
            cached != null
        ) {
            return SourceFetchResult.Success(cached.copy(goal = prefs.dailyGoal) to
                getCachedHourly(date, source.kind, prefs.sourceScope, zoneId))
        }

        val hourlyResult = withTimeoutOrNull(12_000) {
            source.fetchHourlyBreakdown(date, zoneId, prefs.sourceScope, asOf)
        }
        val hourlyList = when (hourlyResult) {
            is SourceFetchResult.Success -> hourlyResult.data
            else -> getCachedHourly(date, source.kind, prefs.sourceScope, zoneId)
        }

        stepDao.replaceDay(
            DailyStepEntity.fromDomain(summary, zoneId.id),
            hourlyList.map { HourlyStepEntity.fromDomain(it) }
        )
        prefsRepo.updateLastSyncAt(summary.fetchedAt)

        SourceFetchResult.Success(summary to hourlyList)
    }

    suspend fun getCachedDaily(
        date: LocalDate,
        sourceKind: StepSourceKind,
        scope: SourceScope,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): DailyStepSummary? {
        return stepDao.getDaily(date.toEpochDay(), sourceKind.name, scope.name, zoneId.id)?.toDomain()
    }

    suspend fun getCachedHourly(
        date: LocalDate,
        sourceKind: StepSourceKind,
        scope: SourceScope,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<HourlyStepSummary> {
        return stepDao.getHourlyForDay(date.toEpochDay(), sourceKind.name, scope.name, zoneId.id)
            .map { it.toDomain() }
    }

    fun observeCachedRange(
        startDate: LocalDate,
        endDate: LocalDate,
        sourceKind: StepSourceKind,
        scope: SourceScope,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<List<DailyStepSummary>> {
        return stepDao.observeDailyRange(
            startEpochDay = startDate.toEpochDay(),
            endEpochDay = endDate.toEpochDay(),
            sourceKind = sourceKind.name,
            sourceScope = scope.name,
            zoneId = zoneId.id
        ).map { list -> list.map { it.toDomain() } }
    }

    suspend fun syncRange(
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): SourceFetchResult<List<DailyStepSummary>> = syncLock.withLock {
        val prefs = prefsRepo.preferencesFlow.first()
        val source = resolveActiveSource(prefs)
            ?: return SourceFetchResult.Unavailable("利用可能な歩数データソースがありません。", false)

        val fetched = withTimeoutOrNull(25_000) {
            source.fetchDailyRange(startDate, endDate, zoneId, prefs.dailyGoal, prefs.sourceScope)
        } ?: SourceFetchResult.Failure("履歴の読み取りがタイムアウトしました。")
        if (fetched is SourceFetchResult.Success) {
            val entities = fetched.data
                .filter { it.status != DataStatus.UNMEASURED || source.kind == StepSourceKind.HEALTH_CONNECT }
                .map { DailyStepEntity.fromDomain(it, zoneId.id) }
            if (entities.isNotEmpty()) {
                stepDao.replaceTotals(entities)
            }
            prefsRepo.updateLastSyncAt(Instant.now())
        }
        fetched
    }

    suspend fun inspectEnvironment(): EnvironmentDiagnostic {
        val prefs = prefsRepo.preferencesFlow.first()
        val snap = capabilityChecker.inspectSync()
        val hcGranted = capabilityChecker.getHealthConnectGrantedPermissions()
        val activeSource = resolveActiveSource(prefs)

        val hcStatusLabel = when (snap.healthConnectSdkStatus) {
            HealthConnectClient.SDK_AVAILABLE -> "利用可能"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "更新が必要"
            else -> "利用不可"
        }

        val notes = buildList {
            if (snap.upsideDownCakeExtension < 20) {
                add("SDK Extension が 20 未満のため、Health Connect のスマホ単体歩数自動収集に未対応の可能性があります。")
            }
            if (snap.upsideDownCakeExtension in 20..21 && prefs.sourceScope == SourceScope.THIS_PHONE_ONLY) {
                add("「このスマホのみ」を正確に識別するには SDK Extension 22 以上または Recording API が必要です。")
            }
            if (!snap.supportsBackgroundReadFeature) {
                add("バックグラウンド読み取り機能に未対応のため、ウィジェット更新はアプリ起動時に同期されます。")
            }
            if (!snap.hasStepCounterSensor) {
                add("端末にハードウェア歩数センサー（TYPE_STEP_COUNTER）が検出されませんでした。")
            }
        }

        return EnvironmentDiagnostic(
            androidVersion = snap.androidSdk,
            upsideDownCakeExtension = snap.upsideDownCakeExtension,
            hasStepCounterSensor = snap.hasStepCounterSensor,
            healthConnectStatusLabel = hcStatusLabel,
            supportsDeviceStepsExtension = snap.supportsDeviceAutoSteps,
            supportsBackgroundRead = snap.supportsBackgroundReadFeature,
            hasReadStepsPermission = hcGranted.contains(DeviceCapabilityChecker.READ_STEPS_PERMISSION),
            hasBackgroundReadPermission = hcGranted.contains(DeviceCapabilityChecker.READ_BACKGROUND_PERMISSION),
            hasActivityRecognitionPermission = snap.hasActivityRecognition,
            isPlayServicesAvailable = snap.isPlayServicesAvailable,
            activeSourceKind = activeSource?.kind ?: StepSourceKind.NONE,
            activeSourceScope = prefs.sourceScope,
            lastSyncAt = prefs.lastSyncAt,
            notes = notes
        )
    }

    suspend fun clearLocalCache(resetPreferences: Boolean) = syncLock.withLock {
        stepDao.clearCache()
        if (resetPreferences) {
            prefsRepo.resetAllPreferences()
        } else {
            prefsRepo.clearLastSync()
        }
    }
}
