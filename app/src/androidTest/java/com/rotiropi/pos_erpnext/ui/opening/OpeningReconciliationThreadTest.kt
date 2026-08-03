package com.rotiropi.pos_erpnext.ui.opening

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rotiropi.pos_erpnext.data.CurrentSessionResult
import com.rotiropi.pos_erpnext.data.OpeningSession
import com.rotiropi.pos_erpnext.data.OpeningStatus
import com.rotiropi.pos_erpnext.recovery.RecoveryExecution
import com.rotiropi.pos_erpnext.recovery.RecoveryIdentity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpeningReconciliationThreadTest {
    @Test
    fun recoveredSuccessReconcilesOffAndroidMainThread() {
        val complete = CountDownLatch(1)
        var reconciliationThread: Thread? = null
        val runner = OpeningReconciliationRunner(
            flow = OpeningFlowCoordinator(
                currentSession = {
                    reconciliationThread = Thread.currentThread()
                    CurrentSessionResult.Success(opening())
                },
                submitOpening = { RecoveryExecution.WaitingRetry("unused") },
                refreshCapabilities = {},
            ),
            dispatch = { action -> CoroutineScope(Dispatchers.IO).launch { action() } },
            isCurrent = { true },
            onResult = { _, _ -> complete.countDown() },
        )

        runner.recovered(
            RecoveredOpeningTerminal.Completed(
                RecoveryIdentity("cashier@example.test", "https://example.test", "client"),
                7L,
                "transaction-1",
            ),
        )

        assertTrue(complete.await(5, TimeUnit.SECONDS))
        assertNotSame(Looper.getMainLooper().thread, reconciliationThread)
    }

    private fun opening() = OpeningSession(
        name = "OPENING-EXAMPLE-0001",
        posProfile = "PROFILE-EXAMPLE",
        company = "Example Company",
        user = "cashier@example.test",
        status = OpeningStatus.OPEN,
        postingDate = "2026-08-03",
        periodStartDate = "2026-08-03T08:00:00+07:00",
        openingBalances = emptyList(),
        warnings = emptyList(),
    )
}
