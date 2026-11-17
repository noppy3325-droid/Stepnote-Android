package com.stepnote.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class WalkSessionTest {
    private val start = Instant.parse("2026-11-16T14:59:55Z")
    private val running = WalkSession(start, scope = SourceScope.GOOGLE_FIT, source = StepSourceKind.HEALTH_CONNECT)

    @Test
    fun `running interval starts at the tap even when it crosses midnight in Japan`() {
        val now = start.plusSeconds(20)
        assertEquals(StepInterval(start, now), running.interval(now))
        assertNull(running.interval(start))
    }

    @Test
    fun `stop pins the end and keeps the original source during later refreshes`() {
        val stop = start.plusSeconds(20)
        val stopped = running.stop(stop)
        assertEquals(StepInterval(start, stop), stopped.interval(stop.plusSeconds(60)))
        assertEquals(stopped, stopped.stop(stop.plusSeconds(60)))
        assertEquals(SourceScope.GOOGLE_FIT, stopped.scope)
        assertEquals(StepSourceKind.HEALTH_CONNECT, stopped.source)
    }

    @Test
    fun `backward clock change cannot produce a negative interval`() {
        val stopped = running.stop(start.minusSeconds(5))
        assertEquals(start, stopped.stoppedAt)
        assertNull(stopped.interval(start.plusSeconds(60)))
    }
}
