package com.rotiropi.pos_erpnext.recovery

import androidx.work.ListenableWorker
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPendingMutationWorkerTest {
    @Test
    fun allDurableCoordinatorOutcomesCompleteWorkWithoutWorkManagerRetry() {
        listOf<RecoveryExecution>(
            RecoveryExecution.NotStartedOffline,
            RecoveryExecution.AuthRequired,
            RecoveryExecution.BlockedIdentity,
            RecoveryExecution.Completed("123e4567-e89b-42d3-a456-426614174000"),
            RecoveryExecution.Rejected("123e4567-e89b-42d3-a456-426614174000"),
            RecoveryExecution.WaitingRetry("123e4567-e89b-42d3-a456-426614174000"),
            RecoveryExecution.RetrySchedulingFailed("123e4567-e89b-42d3-a456-426614174000"),
            RecoveryExecution.ManualRecovery("123e4567-e89b-42d3-a456-426614174000"),
        ).forEach { outcome ->
            assertTrue(RetryPendingMutationWorker.resultFor(outcome) is ListenableWorker.Result.Success)
        }
    }
}
