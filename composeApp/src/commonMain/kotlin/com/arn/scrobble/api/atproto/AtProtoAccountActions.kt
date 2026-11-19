package com.arn.scrobble.api.atproto

import com.arn.scrobble.api.UserAccountSerializable
import kotlinx.coroutines.CancellationException

/** The two preference click boundaries, with an injectable snackbar sink (never raw exception text). */
internal class AtProtoAccountActions(
    private val accounts: PanoAtProtoAccounts,
    private val login: AtProtoLogin,
    private val showError: suspend (String) -> Unit,
) {
    suspend fun remove(reference: UserAccountSerializable) {
        try { login.remove(reference) }
        catch (cause: CancellationException) { throw cause }
        catch (_: Exception) { showError(
            "Account removal could not be finished. Local authorization may already be removed. Retry removing this account.") }
    }

    suspend fun setEnabled(reference: UserAccountSerializable, enabled: Boolean) {
        try { accounts.setEnabled(reference, enabled) }
        catch (cause: CancellationException) { throw cause }
        catch (_: Exception) { showError(
            "The account setting could not be saved. Reopen service preferences and retry; sign in again if the authorization was removed or replaced.") }
    }
}
