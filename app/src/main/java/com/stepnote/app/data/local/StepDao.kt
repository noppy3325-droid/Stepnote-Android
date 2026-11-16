package com.stepnote.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDao {

    @Upsert
    suspend fun upsertDaily(entity: DailyStepEntity)

    @Upsert
    suspend fun upsertHourlyBatch(entities: List<HourlyStepEntity>)

    @Query("""
        DELETE FROM hourly_step_cache
        WHERE epochDay = :epochDay AND sourceKind = :sourceKind AND sourceScope = :sourceScope
    """)
    suspend fun deleteHourlyDay(epochDay: Long, sourceKind: String, sourceScope: String)

    @Transaction
    suspend fun replaceTotals(days: List<DailyStepEntity>) {
        for (day in days) {
            if (getDaily(day.epochDay, day.sourceKind, day.sourceScope, day.zoneId) == null) {
                deleteHourlyDay(day.epochDay, day.sourceKind, day.sourceScope)
            }
            upsertDaily(day)
        }
    }

    @Transaction
    suspend fun replaceDay(day: DailyStepEntity, hours: List<HourlyStepEntity>) {
        deleteHourlyDay(day.epochDay, day.sourceKind, day.sourceScope)
        upsertDaily(day)
        upsertHourlyBatch(hours)
    }

    @Query(
        """
        SELECT * FROM daily_step_cache
        WHERE epochDay = :epochDay
          AND sourceKind = :sourceKind
          AND sourceScope = :sourceScope
          AND zoneId = :zoneId
        LIMIT 1
        """
    )
    suspend fun getDaily(
        epochDay: Long,
        sourceKind: String,
        sourceScope: String,
        zoneId: String
    ): DailyStepEntity?

    @Query(
        """
        SELECT * FROM daily_step_cache
        WHERE epochDay BETWEEN :startEpochDay AND :endEpochDay
          AND sourceKind = :sourceKind
          AND sourceScope = :sourceScope
          AND zoneId = :zoneId
        ORDER BY epochDay ASC
        """
    )
    fun observeDailyRange(
        startEpochDay: Long,
        endEpochDay: Long,
        sourceKind: String,
        sourceScope: String,
        zoneId: String
    ): Flow<List<DailyStepEntity>>

    @Query(
        """
        SELECT * FROM daily_step_cache
        WHERE epochDay BETWEEN :startEpochDay AND :endEpochDay
          AND sourceKind = :sourceKind
          AND sourceScope = :sourceScope
        ORDER BY epochDay ASC
        """
    )
    suspend fun getDailyRange(
        startEpochDay: Long,
        endEpochDay: Long,
        sourceKind: String,
        sourceScope: String
    ): List<DailyStepEntity>

    @Query(
        """
        SELECT hours.* FROM hourly_step_cache hours
        JOIN daily_step_cache day
            ON day.epochDay = hours.epochDay AND day.sourceKind = hours.sourceKind
            AND day.sourceScope = hours.sourceScope
        WHERE hours.epochDay = :epochDay AND hours.sourceKind = :sourceKind
          AND hours.sourceScope = :sourceScope AND day.zoneId = :zoneId
        ORDER BY hours.hour ASC
        """
    )
    suspend fun getHourlyForDay(
        epochDay: Long,
        sourceKind: String,
        sourceScope: String,
        zoneId: String
    ): List<HourlyStepEntity>

    @Query("DELETE FROM daily_step_cache")
    suspend fun clearAllDaily()

    @Query("DELETE FROM hourly_step_cache")
    suspend fun clearAllHourly()

    @Transaction
    suspend fun clearCache() {
        clearAllHourly()
        clearAllDaily()
    }
}
