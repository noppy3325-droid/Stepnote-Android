package com.stepnote.app.data.local

import androidx.room.Entity
import com.stepnote.app.domain.model.DailyStepSummary
import com.stepnote.app.domain.model.DataStatus
import com.stepnote.app.domain.model.HourlyStepSummary
import com.stepnote.app.domain.model.SourceScope
import com.stepnote.app.domain.model.StepSourceKind
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "daily_step_cache",
    primaryKeys = ["epochDay", "sourceKind", "sourceScope"]
)
data class DailyStepEntity(
    val epochDay: Long,
    val sourceKind: String,
    val sourceScope: String,
    val steps: Long,
    val goal: Int,
    val fetchedAtEpochMillis: Long,
    val status: String,
    val zoneId: String
) {
    fun toDomain(): DailyStepSummary = DailyStepSummary(
        localDate = LocalDate.ofEpochDay(epochDay),
        steps = steps,
        goal = goal,
        dataSource = StepSourceKind.valueOf(sourceKind),
        sourceScope = SourceScope.valueOf(sourceScope),
        fetchedAt = Instant.ofEpochMilli(fetchedAtEpochMillis),
        status = DataStatus.valueOf(status)
    )

    companion object {
        fun fromDomain(summary: DailyStepSummary, zoneId: String): DailyStepEntity = DailyStepEntity(
            epochDay = summary.localDate.toEpochDay(),
            sourceKind = summary.dataSource.name,
            sourceScope = summary.sourceScope.name,
            steps = summary.steps,
            goal = summary.goal,
            fetchedAtEpochMillis = summary.fetchedAt.toEpochMilli(),
            status = summary.status.name,
            zoneId = zoneId
        )
    }
}

@Entity(
    tableName = "hourly_step_cache",
    primaryKeys = ["epochDay", "hour", "sourceKind", "sourceScope"]
)
data class HourlyStepEntity(
    val epochDay: Long,
    val hour: Int,
    val sourceKind: String,
    val sourceScope: String,
    val steps: Long,
    val fetchedAtEpochMillis: Long
) {
    fun toDomain(): HourlyStepSummary = HourlyStepSummary(
        localDate = LocalDate.ofEpochDay(epochDay),
        hour = hour,
        steps = steps,
        dataSource = StepSourceKind.valueOf(sourceKind),
        sourceScope = SourceScope.valueOf(sourceScope),
        fetchedAt = Instant.ofEpochMilli(fetchedAtEpochMillis)
    )

    companion object {
        fun fromDomain(summary: HourlyStepSummary): HourlyStepEntity = HourlyStepEntity(
            epochDay = summary.localDate.toEpochDay(),
            hour = summary.hour,
            sourceKind = summary.dataSource.name,
            sourceScope = summary.sourceScope.name,
            steps = summary.steps,
            fetchedAtEpochMillis = summary.fetchedAt.toEpochMilli()
        )
    }
}
