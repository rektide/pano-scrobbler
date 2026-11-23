package com.arn.scrobble.work

import com.arn.scrobble.utils.Stuff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

actual object PendingScrobblesWork : CommonWorkImpl(PendingScrobblesWorker.NAME) {
    const val RETRY_DELAY_HOURS = 1L

    actual fun scheduleAtDelivery(force: Boolean) { AtDesktopDelivery.wake.trySend(Unit) }

    actual fun schedule(force: Boolean) {
        val retryPolicy = RetryPolicy(
            initialDelayMillis = RETRY_DELAY_HOURS * 60 * 60 * 1000,
            backoffFactor = 4.0,
        )

        if (force) {
            DesktopWorkManager.cancelWork(uniqueName)
        }

        DesktopWorkManager.scheduleWork(
            uniqueName,
            5 * 1000,
            { PendingScrobblesWorker(it) },
            retryPolicy,
            replace = force,
        )
    }
}

// The legacy desktop manager neither repeats Result.Retry nor removes completed workMap jobs.
private object AtDesktopDelivery {
    val wake = Channel<Unit>(Channel.CONFLATED)

    init {
        Stuff.appScope.launch(Dispatchers.IO) {
            for (ignored in wake) {
                do {
                    val result = try { AtDeliveryWorker({}).doWork() }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { CommonWorkerResult.Retry }
                    if (result != CommonWorkerResult.Retry) break
                    withTimeoutOrNull(60_000) { wake.receive() }
                } while (true)
            }
        }
    }
}
