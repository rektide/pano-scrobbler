package com.arn.scrobble.work

import com.arn.scrobble.api.atproto.atOutbox

internal class AtDeliveryWorker(
    override val setProgress: suspend (CommonWorkProgress) -> Unit,
    private val drain: suspend () -> Boolean = { atOutbox.drain() },
) : CommonWorker {
    override suspend fun doWork(): CommonWorkerResult =
        if (drain()) CommonWorkerResult.Retry else CommonWorkerResult.Success

    companion object { const val NAME = "at_protocol_delivery" }
}
