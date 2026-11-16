package com.stepnote.app.data.repository

import com.stepnote.app.data.local.DailyStepEntity
import com.stepnote.app.data.health.stepDayWindow
import com.stepnote.app.domain.model.DailyStepSummary
import com.stepnote.app.domain.model.DataStatus
import com.stepnote.app.domain.model.SourceScope
import com.stepnote.app.domain.model.StepSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StepDateBoundaryTest {

    @Test
    fun `dst spring forward day uses local midnight boundaries rather than fixed 86400s`() {
        val nyZone = ZoneId.of("America/New_York")
        val dstDate = LocalDate.of(2026, 3, 8) // Spring forward: 23-hour day
        val window = requireNotNull(stepDayWindow(dstDate, nyZone, Instant.parse("2026-03-10T00:00:00Z")))
        assertEquals(23L, Duration.between(window.start, window.end).toHours())
        val autumn = requireNotNull(stepDayWindow(LocalDate.of(2026, 11, 1), nyZone, Instant.parse("2026-11-03T00:00:00Z")))
        assertEquals(25L, Duration.between(autumn.start, autumn.end).toHours())
    }

    @Test
    fun `today ends at request time and future days have no range`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val date = LocalDate.of(2026, 11, 16)
        val now = Instant.parse("2026-11-16T03:00:00Z")
        val window = requireNotNull(stepDayWindow(date, tokyo, now))
        assertEquals(Instant.parse("2026-11-15T15:00:00Z"), window.start)
        assertEquals(now, window.end)
        assertNull(stepDayWindow(date.plusDays(1), tokyo, now))
    }

    @Test
    fun `leap year and year-end transitions advance local date cleanly`() {
        val leapEve = LocalDate.of(2024, 2, 28)
        assertEquals(LocalDate.of(2024, 2, 29), leapEve.plusDays(1))

        val yearEve = LocalDate.of(2026, 12, 31)
        assertEquals(LocalDate.of(2027, 1, 1), yearEve.plusDays(1))
    }

    @Test
    fun `room composite keys separate sources and scopes to prevent mixing`() {
        val date = LocalDate.of(2026, 11, 14)
        val hcThisPhone = DailyStepEntity.fromDomain(
            DailyStepSummary(
                localDate = date,
                steps = 5_000L,
                goal = 10_000,
                dataSource = StepSourceKind.HEALTH_CONNECT,
                sourceScope = SourceScope.THIS_PHONE_ONLY,
                fetchedAt = Instant.EPOCH,
                status = DataStatus.MEASURED
            ),
            zoneId = "Asia/Tokyo"
        )
        val hcAll = DailyStepEntity.fromDomain(
            DailyStepSummary(
                localDate = date,
                steps = 7_500L,
                goal = 10_000,
                dataSource = StepSourceKind.HEALTH_CONNECT,
                sourceScope = SourceScope.ALL_HEALTH_CONNECT,
                fetchedAt = Instant.EPOCH,
                status = DataStatus.MEASURED
            ),
            zoneId = "Asia/Tokyo"
        )
        val recording = DailyStepEntity.fromDomain(
            DailyStepSummary(
                localDate = date,
                steps = 5_000L,
                goal = 10_000,
                dataSource = StepSourceKind.LOCAL_RECORDING,
                sourceScope = SourceScope.THIS_PHONE_ONLY,
                fetchedAt = Instant.EPOCH,
                status = DataStatus.MEASURED
            ),
            zoneId = "Asia/Tokyo"
        )

        assertNotEquals(hcThisPhone.sourceScope, hcAll.sourceScope)
        assertNotEquals(hcThisPhone.sourceKind, recording.sourceKind)
    }
}
