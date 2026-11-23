package com.arn.scrobble.work

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.arn.scrobble.utils.AndroidStuff
import java.util.concurrent.TimeUnit

actual object PendingScrobblesWork : CommonWorkImpl(PendingScrobblesWorker.NAME) {
    private const val RETRY_DELAY_HOURS = 1L

    private var scheduledFromBg = false

    actual fun scheduleAtDelivery(force: Boolean) {
        val context = AndroidStuff.applicationContext
        val name = AtDeliveryWorker.NAME
        if (!AndroidStuff.isMainProcess) {
            WorkEnqueuerReceiver.broadcast(context, name, force)
            return
        }
        val (policy, request) = atWorkRequest(force)
        WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request)
    }

    actual fun schedule(force: Boolean) {
        if (!AndroidStuff.isMainProcess) {
            // todo find a better way to schedule from other processes
            if (force || !scheduledFromBg) {
                scheduledFromBg = true
                WorkEnqueuerReceiver.broadcast(
                    AndroidStuff.applicationContext,
                    name,
                    force
                )
            }

            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<PlatformWorker>()
            .setInputData(
                workDataOf(
                    PlatformWorker.WORK_NAME_KEY to name
                )
            )
            .addTag(name)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                RETRY_DELAY_HOURS,
                TimeUnit.HOURS
            )
            .apply {
                // works fine in fg without expedited
                if (!force) {
                    setInitialDelay(30, TimeUnit.SECONDS)
                }
            }
            .build()

        WorkManager.getInstance(AndroidStuff.applicationContext)
            .enqueueUniqueWork(
                name,
                if (force)
                    ExistingWorkPolicy.REPLACE
                else
                    ExistingWorkPolicy.KEEP,
                workRequest
            )
    }
}

internal fun atWorkRequest(force: Boolean): Pair<ExistingWorkPolicy, OneTimeWorkRequest> {
    val name = AtDeliveryWorker.NAME
    val request = OneTimeWorkRequestBuilder<PlatformWorker>()
        .setInputData(workDataOf(PlatformWorker.WORK_NAME_KEY to name))
        .addTag(name)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1L, TimeUnit.MINUTES)
        .build()
    // KEEP could drop a newly staged event just as the prior worker returns Success.
    val policy = if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE
    return policy to request
}
