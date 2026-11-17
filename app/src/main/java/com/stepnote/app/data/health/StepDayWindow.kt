package com.stepnote.app.data.health

import com.stepnote.app.domain.model.StepInterval
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun stepDayWindow(date: LocalDate, zone: ZoneId, asOf: Instant): StepInterval? {
    val start = date.atStartOfDay(zone).toInstant()
    val end = minOf(asOf, date.plusDays(1).atStartOfDay(zone).toInstant())
    return if (start < end) StepInterval(start, end) else null
}
