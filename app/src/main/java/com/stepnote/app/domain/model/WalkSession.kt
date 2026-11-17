package com.stepnote.app.domain.model

import java.time.Instant

data class StepInterval(val start: Instant, val end: Instant) {
    init {
        require(start < end)
    }
}

data class WalkSession(
    val startedAt: Instant,
    val stoppedAt: Instant? = null,
    val scope: SourceScope,
    val source: StepSourceKind,
    val steps: Long? = null
) {
    fun interval(now: Instant): StepInterval? {
        val end = stoppedAt ?: now
        return if (startedAt < end) StepInterval(startedAt, end) else null
    }

    fun stop(now: Instant): WalkSession = if (stoppedAt != null) this else
        copy(stoppedAt = maxOf(startedAt, now))
}
