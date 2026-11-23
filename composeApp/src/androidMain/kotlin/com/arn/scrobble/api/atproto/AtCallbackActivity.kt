package com.arn.scrobble.api.atproto

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import android.widget.Toast
import com.arn.scrobble.utils.Stuff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AtCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val callback = intent.takeIf { it.action == Intent.ACTION_VIEW }?.dataString
        intent.data = null
        if (callback == null) { finish(); return }
        Stuff.appScope.launch {
            val message = try {
                atAuthority.finishBrowser(callback)
                "AT Protocol account connected. Return to Pano’s account settings."
            } catch (e: CancellationException) { throw e }
            catch (e: AtFailure) { e.problem.text }
            catch (_: Exception) { AtProblem.NETWORK.text }
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
