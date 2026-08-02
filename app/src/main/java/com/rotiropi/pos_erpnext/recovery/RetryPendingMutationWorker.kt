package com.rotiropi.pos_erpnext.recovery

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executor

class RetryPendingMutationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_TRANSACTION_ID) ?: return Result.failure()
        val app = applicationContext as? com.rotiropi.pos_erpnext.MobilePosApplication ?: return Result.failure()
        return app.retryPendingMutationAfterColdBootstrap(id)?.let(::resultFor) ?: Result.success()
    }

    companion object {
        internal fun resultFor(outcome: RecoveryExecution): Result = Result.success()

        private const val KEY_TRANSACTION_ID = "transaction_id"
        private val SCHEDULER_EXECUTOR = Executor { runnable -> runnable.run() }
        fun schedule(
            context: Context,
            transactionId: String,
            nextEligibleAtMillis: Long = System.currentTimeMillis(),
            completion: (Throwable?) -> Unit = {},
        ) {
            val delayMillis = (nextEligibleAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            val work = OneTimeWorkRequestBuilder<RetryPendingMutationWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_TRANSACTION_ID to transactionId))
                .build()
            try {
                val result = WorkManager.getInstance(context)
                    .enqueueUniqueWork("pending-mutation-$transactionId", ExistingWorkPolicy.REPLACE, work)
                    .result
                result.addListener({
                    completion(runCatching { result.get() }.exceptionOrNull())
                }, SCHEDULER_EXECUTOR)
            } catch (error: RuntimeException) {
                completion(error)
            }
        }
    }
}
