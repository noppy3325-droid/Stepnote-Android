package com.stepnote.app.domain.model

import java.time.Instant
import java.time.LocalDate

enum class StepSourceKind(val label: String) {
    HEALTH_CONNECT("Health Connect"),
    LOCAL_RECORDING("Recording API (端末内)"),
    NONE("未設定")
}

enum class SourceScope(val label: String) {
    THIS_PHONE_ONLY("このスマホのみ"),
    ALL_HEALTH_CONNECT("Health Connect 全対応データ")
}

enum class DataStatus {
    MEASURED,
    CONFIRMED_ZERO,
    UNMEASURED,
    ERROR
}

enum class ThemeMode(val label: String) {
    SYSTEM("システム設定に従う"),
    LIGHT("ライト"),
    DARK("ダーク")
}

data class DailyStepSummary(
    val localDate: LocalDate,
    val steps: Long,
    val goal: Int,
    val dataSource: StepSourceKind,
    val sourceScope: SourceScope,
    val fetchedAt: Instant,
    val status: DataStatus
)

data class HourlyStepSummary(
    val localDate: LocalDate,
    val hour: Int,
    val steps: Long,
    val dataSource: StepSourceKind,
    val sourceScope: SourceScope,
    val fetchedAt: Instant
)

data class StepPeriodStats(
    val totalSteps: Long,
    val validDayCount: Int,
    val totalDayCount: Int,
    val averageValidDaySteps: Long,
    val maxDaySteps: Long,
    val maxDayLabel: String?,
    val goalAchievedDays: Int
)

data class EnvironmentDiagnostic(
    val androidVersion: Int,
    val upsideDownCakeExtension: Int,
    val hasStepCounterSensor: Boolean,
    val healthConnectStatusLabel: String,
    val supportsDeviceStepsExtension: Boolean,
    val supportsBackgroundRead: Boolean,
    val hasReadStepsPermission: Boolean,
    val hasBackgroundReadPermission: Boolean,
    val hasActivityRecognitionPermission: Boolean,
    val isPlayServicesAvailable: Boolean,
    val activeSourceKind: StepSourceKind,
    val activeSourceScope: SourceScope,
    val lastSyncAt: Instant?,
    val notes: List<String>
)

sealed interface StepUiState {
    data object Loading : StepUiState

    data class Content(
        val summary: DailyStepSummary,
        val hourlyBuckets: List<HourlyStepSummary>,
        val estimatedDistanceMeters: Double,
        val estimatedCaloriesKcal: Double?,
        val backgroundReadGranted: Boolean
    ) : StepUiState

    data class Empty(
        val localDate: LocalDate,
        val goal: Int,
        val activeSource: StepSourceKind,
        val message: String
    ) : StepUiState

    data class PermissionRequired(
        val activeSource: StepSourceKind,
        val title: String,
        val reason: String,
        val missingPermissions: List<String>
    ) : StepUiState

    data class Unsupported(
        val title: String,
        val reason: String,
        val canFallbackToRecordingApi: Boolean
    ) : StepUiState

    data class Error(
        val message: String,
        val lastKnownSummary: DailyStepSummary?
    ) : StepUiState
}
