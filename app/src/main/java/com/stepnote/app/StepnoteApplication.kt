package com.stepnote.app

import android.app.Application
import com.stepnote.app.data.health.DeviceCapabilityChecker
import com.stepnote.app.data.health.HealthConnectStepSource
import com.stepnote.app.data.health.LocalRecordingStepSource
import com.stepnote.app.data.local.StepDatabase
import com.stepnote.app.data.preferences.UserPreferencesRepository
import com.stepnote.app.data.repository.StepRepository
import com.stepnote.app.domain.usecase.CalculateStepStatsUseCase
import com.stepnote.app.domain.usecase.GetStepHistoryUseCase
import com.stepnote.app.domain.usecase.GetTodayStepsUseCase
import com.stepnote.app.worker.StepSyncWorker

class AppContainer(application: Application) {
    val database = StepDatabase.getInstance(application)
    val preferencesRepository = UserPreferencesRepository(application)
    val capabilityChecker = DeviceCapabilityChecker(application)
    val healthConnectSource = HealthConnectStepSource(application, capabilityChecker)
    val localRecordingSource = LocalRecordingStepSource(application, capabilityChecker)
    val stepRepository = StepRepository(
        stepDao = database.stepDao(),
        prefsRepo = preferencesRepository,
        capabilityChecker = capabilityChecker,
        healthConnectSource = healthConnectSource,
        localRecordingSource = localRecordingSource
    )
    val statsUseCase = CalculateStepStatsUseCase()
    val getTodayStepsUseCase = GetTodayStepsUseCase(stepRepository, preferencesRepository, statsUseCase)
    val getStepHistoryUseCase = GetStepHistoryUseCase(stepRepository, preferencesRepository, statsUseCase)
}

class StepnoteApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        StepSyncWorker.schedulePeriodic(this)
    }
}
