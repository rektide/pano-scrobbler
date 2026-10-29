package com.arn.scrobble.api.atproto

import com.arn.scrobble.atproto.oauth.AuthorizationLaunch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.test.*

/** Actual controller, authority, on-disk vault and preference action boundaries; synthetic network only. */
class AtProtoLoginUiTest {
    private suspend fun idle(login: AtProtoLogin) = withTimeout(5_000) { login.state.first { !it.busy } }
    private suspend fun launched(login: AtProtoLogin): AuthorizationLaunch =
        withTimeout(5_000) { login.state.first { it.launch != null && !it.busy } }.launch!!

    @Test fun unrelatedCallbackCannotStealPausedBeginAndItsValidCallbackStillCompletes(): Unit = runBlocking {
        AccountFixture().use { f ->
            f.parEntered = CompletableDeferred(); f.parRelease = CompletableDeferred()
            val login = AtProtoLogin(f.accounts, CoroutineScope(coroutineContext))
            login.begin("alice.example.com")
            withTimeout(5_000) { f.parEntered!!.await() }
            login.completeCallback("example.client:/callback?state=stale&code=old&iss=https%3A%2F%2Fpds.example")
            assertTrue(login.state.value.busy)
            f.parRelease!!.complete(Unit)
            val launch = launched(login)
            assertNotNull(login.state.value.error)
            assertEquals(0, f.tokens)
            assertTrue(login.consumeBrowserLaunch(launch))
            login.completeCallback(f.callback())
            assertNotNull(idle(login).completed)
            assertEquals(1, f.tokens)
        }
    }

    @Test fun distinctValidDeliveryBehindMalformedInputIsProcessedWithoutRedelivery(): Unit = runBlocking {
        AccountFixture().use { f ->
            f.authority.beginAuthorization("alice.example.com")
            val login = AtProtoLogin(f.accounts, CoroutineScope(coroutineContext))
            login.completeCallback("not-a-callback")
            login.completeCallback(f.callback())
            val result = idle(login)
            assertNotNull(result.completed)
            assertNull(result.error)
            assertEquals(1, f.tokens)
            assertEquals(result.completed.session, f.prefs.data.value.scrobbleAccounts.single().atProtoSession)
        }
    }

    @Test fun callbackQueueIsBoundedDeduplicatesIdenticalInputsAndLateErrorsDoNotEraseCompletion(): Unit = runBlocking {
        AccountFixture().use { f ->
            f.authority.beginAuthorization("alice.example.com")
            f.tokenEntered = CompletableDeferred(); f.tokenRelease = CompletableDeferred()
            val login = AtProtoLogin(f.accounts, CoroutineScope(coroutineContext))
            login.completeCallback(f.callback())
            f.tokenEntered!!.await()
            repeat(20) { login.completeCallback(f.callback()) }
            repeat(4) { login.completeCallback("distinct-invalid-$it") }
            login.completeCallback("overflow-invalid")
            assertTrue(login.state.value.error!!.contains("paste the current callback again"))
            f.tokenRelease!!.complete(Unit)
            val completed = idle(login).completed!!
            assertNull(login.state.value.error)
            assertEquals(1, f.tokens)
            assertTrue(login.consumeCompleted(completed.session))
            login.completeCallback(f.callback())
            assertNull(login.state.value.completed)
            assertEquals(1, f.tokens)
            assertFalse(login.state.value.toString().contains("synthetic-access-secret"))
        }
    }

    @Test fun automaticAndManualBrowserCommandsHonorExpiryAndExplicitLogout(): Unit = runBlocking {
        AccountFixture().use { f ->
            var now = System.currentTimeMillis()
            val login = AtProtoLogin(f.accounts, CoroutineScope(coroutineContext)) { now }
            login.begin("alice.example.com")
            val launch = launched(login)
            val opens = List(2) { async { login.consumeBrowserLaunch(launch) } }.map { it.await() }
            assertEquals(1, opens.count { it })
            assertFalse(login.consumeBrowserLaunch(launch))
            assertTrue(login.reopenBrowserLaunch(launch))
            now = launch.expiresAt
            assertFalse(login.reopenBrowserLaunch(launch))
            assertNull(login.state.value.launch)
            assertFalse(login.consumeBrowserLaunch(launch))

            now = System.currentTimeMillis()
            login.begin("alice.example.com")
            val pending = launched(login)
            now = pending.expiresAt
            assertFalse(login.consumeBrowserLaunch(pending)) // Never automatically dispatched, but expired.
            assertNull(login.state.value.launch)
            now = System.currentTimeMillis()
            login.begin("alice.example.com")
            val beforeLogout = launched(login)
            login.logout()
            assertNull(login.state.value.launch)
            assertFalse(login.state.value.browserPending)
            assertFalse(login.consumeBrowserLaunch(beforeLogout))
            assertFalse(login.reopenBrowserLaunch(beforeLogout))
            assertNull(f.authority.currentAccount())
        }
    }

    @Test fun externalSupersessionAndLogoutAreCheckedAtBothBrowserDispatchBoundaries(): Unit = runBlocking {
        AccountFixture().use { f ->
            val login = AtProtoLogin(f.accounts, CoroutineScope(coroutineContext))
            login.begin("alice.example.com")
            val old = launched(login)
            f.authority.beginAuthorization("alice.example.com") // Another controller/process supersedes it.
            assertFalse(login.reopenBrowserLaunch(old))
            assertNull(login.state.value.launch)
            login.begin("alice.example.com")
            val next = launched(login)
            f.accounts.logout() // Even a non-UI caller cannot dispatch a stale local command.
            assertFalse(login.consumeBrowserLaunch(next))
            assertNull(login.state.value.launch)
        }
    }

    @Test fun browserReadAlreadyInFlightCannotDispatchOrClearCommandsAfterLogoutOrANewerBegin(): Unit = runBlocking {
        for (logout in listOf(false, true)) AccountFixture().use { f ->
            val account = f.authorize(); f.accounts.publish(account.session)
            val login = AtProtoLogin(f.accounts, CoroutineScope(coroutineContext))
            login.begin("alice.example.com")
            val old = launched(login)
            f.prefs.entered = CompletableDeferred(); f.prefs.release = CompletableDeferred()
            val held = launch { f.accounts.publish(account.session) } // Hold the real OS vault lock.
            f.prefs.entered!!.await()
            val read = async(start = CoroutineStart.UNDISPATCHED) { login.consumeBrowserLaunch(old) }
            val removal = if (logout) launch(start = CoroutineStart.UNDISPATCHED) { login.logout() }
                else { login.begin("alice.example.com"); null }
            f.prefs.release!!.complete(Unit)
            held.join()
            assertFalse(read.await())
            removal?.join()
            if (logout) assertEquals(AtProtoLogin.State(), login.state.value)
            else {
                val fresh = launched(login)
                assertNotSame(old, fresh)
                assertTrue(login.consumeBrowserLaunch(fresh))
            }
        }
    }

    @Test fun removalInvalidatesMatchingPendingLaunchButStaleAndOtherClientReferencesDoNot(): Unit = runBlocking {
        AccountFixture().use { f ->
            val first = f.authorize(); f.accounts.publish(first.session)
            val stale = f.prefs.data.value.scrobbleAccounts.single()
            val replacement = f.authorize(); f.accounts.publish(replacement.session)
            val current = f.prefs.data.value.scrobbleAccounts.single()
            val login = AtProtoLogin(f.accounts, CoroutineScope(coroutineContext))
            login.begin("alice.example.com")
            val launch = launched(login)
            login.remove(stale)
            assertEquals(launch, login.state.value.launch)
            assertTrue(login.reopenBrowserLaunch(launch))
            login.remove(stale.copy(atProtoSession = stale.atProtoSession!!.copy(clientId = "https://other.example/metadata.json")))
            assertTrue(login.reopenBrowserLaunch(launch))
            login.remove(stale.copy(atProtoSession = stale.atProtoSession.copy(did = "did:plc:zzzzzzzzzzzzzzzzzzzzzzzz")))
            assertTrue(login.reopenBrowserLaunch(launch))
            login.remove(current)
            assertNull(login.state.value.launch)
            assertFalse(login.consumeBrowserLaunch(launch))
            assertFalse(login.reopenBrowserLaunch(launch))
            assertNull(f.authority.currentAccount())
        }
    }

    @Test fun logoutDuringParOrExchangeCannotPublishStaleUiAndAnExplicitNewAttemptRemainsUsable(): Unit = runBlocking {
        for (exchange in listOf(false, true)) AccountFixture().use { f ->
            val login = AtProtoLogin(f.accounts, CoroutineScope(coroutineContext))
            if (exchange) {
                f.authority.beginAuthorization("alice.example.com")
                f.tokenEntered = CompletableDeferred(); f.tokenRelease = CompletableDeferred()
                login.completeCallback(f.callback())
                withTimeout(5_000) { f.tokenEntered!!.await() }
            } else {
                f.parEntered = CompletableDeferred(); f.parRelease = CompletableDeferred()
                login.begin("alice.example.com")
                withTimeout(5_000) { f.parEntered!!.await() }
            }
            login.logout()
            assertEquals(AtProtoLogin.State(), login.state.value)
            f.parRelease?.complete(Unit); f.tokenRelease?.complete(Unit)
            f.parRelease = null; f.tokenRelease = null
            login.begin("alice.example.com")
            val fresh = launched(login)
            assertTrue(login.consumeBrowserLaunch(fresh))
            login.completeCallback(f.callback())
            assertNotNull(idle(login).completed)
        }
    }

    @Test fun realAuthorityParTimeoutReleasesBusyWithOriginalCancellationAndAllowsNewAttempt(): Unit = runBlocking {
        AccountFixture().use { f ->
            f.parEntered = CompletableDeferred(); f.parRelease = CompletableDeferred()
            val owner = SupervisorJob(coroutineContext[Job])
            val login = AtProtoLogin(f.accounts, CoroutineScope(coroutineContext + owner))
            try {
                login.begin("alice.example.com")
                withTimeout(5_000) { f.parEntered!!.await() }
                val work = owner.children.single()
                var original: Throwable? = null
                work.invokeOnCompletion { original = it }
                withTimeout(45_000) { work.join() }
                assertIs<TimeoutCancellationException>(original)
                assertFalse(login.state.value.busy)
                assertTrue(login.state.value.error!!.contains("timed out"))
                f.parRelease!!.complete(Unit); f.parRelease = null
                login.begin("alice.example.com")
                assertNotNull(launched(login))
            } finally { f.parRelease?.complete(Unit); owner.cancel() }
        }
    }

    @Test fun callbackAndPublicationCancellationReleaseOnlyTheirCurrentOperationAndRetainExplicitRecovery(): Unit = runBlocking {
        for (publication in listOf(false, true)) AccountFixture().use { f ->
            val owner = SupervisorJob(coroutineContext[Job])
            val login = AtProtoLogin(f.accounts, CoroutineScope(coroutineContext + owner))
            try {
                f.authority.beginAuthorization("alice.example.com")
                if (publication) {
                    f.prefs.fail = true
                    login.completeCallback(f.callback())
                    idle(login)
                    assertNotNull(login.state.value.publication)
                    f.prefs.fail = false
                    f.prefs.entered = CompletableDeferred(); f.prefs.release = CompletableDeferred()
                    login.retryPublication()
                    withTimeout(5_000) { f.prefs.entered!!.await() }
                } else {
                    f.tokenEntered = CompletableDeferred(); f.tokenRelease = CompletableDeferred()
                    login.completeCallback(f.callback())
                    withTimeout(5_000) { f.tokenEntered!!.await() }
                }
                val work = owner.children.single()
                val cancellation = CancellationException("controller-cancellation-sentinel")
                var original: Throwable? = null
                work.invokeOnCompletion { original = it }
                work.cancel(cancellation); work.join()
                assertSame(cancellation, original)
                assertFalse(login.state.value.busy)
                if (publication) {
                    val saved = login.state.value.publication!!
                    f.prefs.release!!.complete(Unit); f.prefs.release = null
                    login.retryPublication()
                    assertEquals(saved, idle(login).completed)
                    assertEquals(1, f.tokens) // Never replay code exchange for projection recovery.
                } else {
                    f.tokenRelease!!.complete(Unit); f.tokenRelease = null
                    login.begin("alice.example.com")
                    assertNotNull(launched(login))
                    login.completeCallback(f.callback())
                    assertNotNull(idle(login).completed)
                }
            } finally { f.tokenRelease?.complete(Unit); f.prefs.release?.complete(Unit); owner.cancel() }
        }
    }

    @Test fun cancellationBeforeLazyWorkStartsCannotLeaveBusyOwnership(): Unit = runBlocking {
        AccountFixture().use { f ->
            val owner = Job().also { it.cancel() }
            val login = AtProtoLogin(f.accounts, CoroutineScope(coroutineContext + owner))
            login.begin("alice.example.com")
            assertFalse(login.state.value.busy)
            login.completeCallback("not-a-callback")
            assertFalse(login.state.value.busy)
            assertNotNull(login.state.value.error)
            assertEquals(0, f.tokens)
        }
    }

    @Test fun preferenceRemovalHandlesProjectionFailureWithoutRestoringCredentialsAndRetryIsActionable(): Unit = runBlocking {
        AccountFixture().use { f ->
            val account = f.authorize(); f.accounts.publish(account.session)
            val reference = f.prefs.data.value.scrobbleAccounts.single()
            val errors = mutableListOf<String>()
            val login = AtProtoLogin(f.accounts, CoroutineScope(coroutineContext))
            val actions = AtProtoAccountActions(f.accounts, login) { errors += it }
            login.begin("alice.example.com")
            val launch = launched(login)
            f.prefs.fail = true
            actions.remove(reference)
            assertNull(f.authority.currentAccount())
            assertEquals(reference, f.prefs.data.value.scrobbleAccounts.single())
            assertNull(login.state.value.launch)
            assertFalse(login.reopenBrowserLaunch(launch))
            assertTrue(errors.single().contains("may already be removed"))
            f.prefs.fail = false
            actions.remove(reference)
            assertTrue(f.prefs.data.value.scrobbleAccounts.isEmpty())
            assertEquals(1, errors.size)
        }
    }

    @Test fun preferenceEnableErrorsDoNotModifyReplacementAndExpectedWriteFailureCanBeRetried(): Unit = runBlocking {
        AccountFixture().use { f ->
            val account = f.authorize(); f.accounts.publish(account.session)
            val reference = f.prefs.data.value.scrobbleAccounts.single()
            val errors = mutableListOf<String>()
            val actions = AtProtoAccountActions(f.accounts, AtProtoLogin(f.accounts, CoroutineScope(coroutineContext))) { errors += it }
            f.prefs.failure = IOException("must-not-be-shown-synthetic-secret")
            actions.setEnabled(reference, false)
            assertTrue(f.prefs.data.value.scrobbleAccounts.single().canScrobble)
            assertEquals(account, f.authority.currentAccount())
            assertFalse(errors.single().contains("synthetic-secret"))
            f.prefs.failure = null
            actions.setEnabled(reference, false)
            assertFalse(f.prefs.data.value.scrobbleAccounts.single().canScrobble)
            for (differentDid in listOf(false, true)) {
                if (differentDid) f.did = "did:plc:zzzzzzzzzzzzzzzzzzzzzzzz"
                val replacement = f.authorize(); f.accounts.publish(replacement.session)
                val before = f.prefs.data.value
                actions.setEnabled(reference, true)
                assertEquals(before, f.prefs.data.value)
                assertEquals(replacement, f.authority.currentAccount())
            }
            actions.setEnabled(reference.copy(atProtoSession = null), false)
            assertEquals(4, errors.size)
            assertTrue(errors.last().contains("Reopen service preferences"))
        }
    }

    @Test fun bothPreferenceActionBoundariesRethrowCancellationWithoutSnackbar(): Unit = runBlocking {
        for (removal in listOf(false, true)) AccountFixture().use { f ->
            val account = f.authorize(); f.accounts.publish(account.session)
            val reference = f.prefs.data.value.scrobbleAccounts.single()
            val cancellation = CancellationException("expected-action-cancellation")
            f.prefs.failure = cancellation
            val errors = mutableListOf<String>()
            val actions = AtProtoAccountActions(f.accounts, AtProtoLogin(f.accounts, CoroutineScope(coroutineContext))) { errors += it }
            val thrown = assertFailsWith<CancellationException> {
                if (removal) actions.remove(reference) else actions.setEnabled(reference, false)
            }
            // withContext(IO) may make a stack-trace-recovery copy; it must retain the original cause.
            assertTrue(generateSequence<Throwable>(thrown) { it.cause }.any { it === cancellation })
            assertTrue(errors.isEmpty())
            assertEquals(if (removal) null else account, f.authority.currentAccount())
        }
    }
}
