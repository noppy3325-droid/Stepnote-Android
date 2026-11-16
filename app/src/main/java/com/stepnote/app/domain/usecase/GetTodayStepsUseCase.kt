package com.stepnote.app.domain.usecase

import com.stepnote.app.data.health.SourceFetchResult
import com.stepnote.app.data.preferences.UserPreferencesRepository
import com.stepnote.app.data.repository.StepRepository
import com.stepnote.app.domain.model.DataStatus
import com.stepnote.app.domain.model.StepSourceKind
import com.stepnote.app.domain.model.StepUiState
import com.stepnote.app.domain.model.SourceScope
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

class GetTodayStepsUseCase(
    private val repository: StepRepository,
    private val prefsRepository: UserPreferencesRepository,
    private val statsUseCase: CalculateStepStatsUseCase
) {
    suspend operator fun invoke(zoneId: ZoneId = ZoneId.systemDefault()): StepUiState {
        val today = LocalDate.now(zoneId)
        val prefs = prefsRepository.preferencesFlow.first()
        val activeSource = repository.resolveActiveSource(prefs)

        if (activeSource == null) {
            return StepUiState.Unsupported(
                title = "歩数記録基盤を確認できません",
                reason = "この端末では Health Connect（Extension 20/22以上）および Google Play services Recording API の利用条件を満たしていません。",
                canFallbackToRecordingApi = false
            )
        }

        val bgGranted = activeSource.hasBackgroundPermission()

        return when (val result = repository.syncDay(today, zoneId)) {
            is SourceFetchResult.Success -> {
                val (summary, hourly) = result.data
                if (summary.status == DataStatus.UNMEASURED) {
                    StepUiState.Empty(
                        localDate = today,
                        goal = prefs.dailyGoal,
                        activeSource = summary.dataSource,
                        message = "計測の準備ができました。スマホを持ち歩くとここに今日の歩数が表示されます。"
                    )
                } else {
                    StepUiState.Content(
                        summary = summary,
                        hourlyBuckets = hourly,
                        estimatedDistanceMeters = statsUseCase.estimateDistanceMeters(
                            summary.steps,
                            prefs.strideCm
                        ),
                        estimatedCaloriesKcal = if (prefs.showCalories) {
                            statsUseCase.estimateCaloriesKcal(
                                summary.steps,
                                prefs.strideCm,
                                prefs.weightKg
                            )
                        } else {
                            null
                        },
                        backgroundReadGranted = bgGranted
                    )
                }
            }

            is SourceFetchResult.PermissionMissing -> StepUiState.PermissionRequired(
                activeSource = activeSource.kind,
                title = "歩数データへのアクセスが必要です",
                reason = result.reason,
                missingPermissions = result.missingPermissions
            )

            is SourceFetchResult.Unavailable -> StepUiState.Unsupported(
                title = "現在の設定では計測できません",
                reason = result.reason,
                canFallbackToRecordingApi = result.suggestFallback &&
                    activeSource.kind != StepSourceKind.LOCAL_RECORDING &&
                    prefs.sourceScope == SourceScope.THIS_PHONE_ONLY
            )

            is SourceFetchResult.Failure -> {
                val cached = repository.getCachedDaily(
                    today,
                    activeSource.kind,
                    prefs.sourceScope
                )
                StepUiState.Error(
                    message = result.message,
                    lastKnownSummary = cached?.takeIf { it.status != DataStatus.UNMEASURED }
                )
            }
        }
    }
}
