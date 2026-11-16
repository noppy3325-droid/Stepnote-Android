package com.stepnote.app.data.health

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class StepDayWindow(val start: Instant, val end: Instant)

internal fun stepDayWindow(date: LocalDate, zone: ZoneId, asOf: Instant): StepDayWindow? {
    val start = date.atStartOfDay(zone).toInstant()
    val end = minOf(asOf, date.plusDays(1).atStartOfDay(zone).toInstant())
    return if (start < end) StepDayWindow(start, end) else null
}
