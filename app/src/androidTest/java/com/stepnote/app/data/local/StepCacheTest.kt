package com.stepnote.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StepCacheTest {
    private lateinit var database: StepDatabase
    private lateinit var dao: StepDao
    private val first = DailyStepEntity(
        epochDay = 20_773,
        sourceKind = "HEALTH_CONNECT",
        sourceScope = "THIS_PHONE_ONLY",
        steps = 300,
        goal = 10_000,
        fetchedAtEpochMillis = 10,
        status = "MEASURED",
        zoneId = "Asia/Tokyo"
    )

    @Before
    fun openDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, StepDatabase::class.java).build()
        dao = database.stepDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun repeatedSyncReplacesDayAndDoesNotMixSources() = runBlocking {
        val hour = HourlyStepEntity(first.epochDay, 12, first.sourceKind, first.sourceScope, 300, 10)
        val updated = first.copy(steps = 350, fetchedAtEpochMillis = 20)
        dao.replaceDay(first, listOf(hour))
        dao.replaceDay(updated, listOf(hour.copy(steps = 350)))
        dao.replaceDay(updated, listOf(hour.copy(steps = 350)))
        dao.replaceDay(first.copy(sourceKind = "LOCAL_RECORDING", steps = 51), emptyList())
        dao.replaceDay(first.copy(sourceScope = "ALL_HEALTH_CONNECT", steps = 930), emptyList())

        assertEquals(350L, dao.getDaily(first.epochDay, first.sourceKind, first.sourceScope, first.zoneId)?.steps)
        assertEquals(350L, dao.getHourlyForDay(first.epochDay, first.sourceKind, first.sourceScope, first.zoneId).single().steps)
        assertEquals(51L, dao.getDaily(first.epochDay, "LOCAL_RECORDING", first.sourceScope, first.zoneId)?.steps)
        assertEquals(930L, dao.getDaily(first.epochDay, first.sourceKind, "ALL_HEALTH_CONNECT", first.zoneId)?.steps)
    }

    @Test
    fun timezoneChangeAndClearInvalidateIncompatibleHourlyRecords() = runBlocking {
        val hour = HourlyStepEntity(first.epochDay, 12, first.sourceKind, first.sourceScope, 300, 10)
        dao.replaceDay(first, listOf(hour))
        val updated = first.copy(zoneId = "America/New_York")
        dao.replaceTotals(listOf(updated))

        assertNull(dao.getDaily(first.epochDay, first.sourceKind, first.sourceScope, first.zoneId))
        assertEquals(emptyList<HourlyStepEntity>(), dao.getHourlyForDay(first.epochDay, first.sourceKind, first.sourceScope, updated.zoneId))
        dao.clearCache()
        assertNull(dao.getDaily(first.epochDay, first.sourceKind, first.sourceScope, updated.zoneId))
    }
}
