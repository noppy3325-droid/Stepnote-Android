package com.stepnote.app.data.health

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stepnote.app.StepnoteApplication
import com.stepnote.app.domain.model.SourceScope
import com.stepnote.app.domain.model.StepSourceKind
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class PhoneStepSourceTest {
    @Test
    fun healthConnectAcceptsCurrentPhoneOnlyAggregate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = (context.applicationContext as StepnoteApplication).container.healthConnectSource
        val scope = SourceScope.THIS_PHONE_ONLY
        assumeTrue("端末内歩数の対応端末で実施", source.isAvailable(scope))
        assumeTrue(
            "端末で歩数とバックグラウンド読み取りを許可した状態で実施",
            source.hasRequiredPermissions() && source.hasBackgroundPermission()
        )

        val zone = ZoneId.systemDefault()
        val result = withTimeout(12_000) {
            source.fetchDailyTotal(LocalDate.now(zone), zone, 10_000, scope)
        }
        assertTrue("端末の識別と区間集計結果: ${result::class.simpleName}", result is SourceFetchResult.Success)
        val summary = (result as SourceFetchResult.Success).data
        assertEquals(StepSourceKind.HEALTH_CONNECT, summary.dataSource)
        assertEquals(scope, summary.sourceScope)
    }
}
