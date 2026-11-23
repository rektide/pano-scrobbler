package com.arn.scrobble

import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import com.arn.scrobble.work.AtDeliveryWorker
import com.arn.scrobble.work.PendingScrobblesWorker
import com.arn.scrobble.work.PlatformWorker
import com.arn.scrobble.work.atWorkRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtAndroidWorkerTest {
    @Test fun pendingPlayAppendsNetworkConstrainedAtWorkButExplicitRetryReplaces() {
        val (policy, request) = atWorkRequest(force = false)
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, policy)
        assertEquals(AtDeliveryWorker.NAME, request.workSpec.input.getString(PlatformWorker.WORK_NAME_KEY))
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertTrue(AtDeliveryWorker.NAME in request.tags)
        assertFalse(PendingScrobblesWorker.NAME in request.tags)
        assertEquals(ExistingWorkPolicy.REPLACE, atWorkRequest(force = true).first)
    }
}
