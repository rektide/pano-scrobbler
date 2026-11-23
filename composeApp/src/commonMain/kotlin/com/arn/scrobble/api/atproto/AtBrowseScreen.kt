package com.arn.scrobble.api.atproto

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
internal fun AtBrowseScreen(defaultDid: String?, modifier: Modifier) {
    var target by remember { mutableStateOf(AtTarget.TEAL) }
    var actor by remember { mutableStateOf(defaultDid.orEmpty()) }
    var from by remember { mutableStateOf("") }
    var through by remember { mutableStateOf("") }
    var query by remember { mutableStateOf<AtHistoryQuery?>(null) }
    var pager by remember { mutableStateOf<AtPageWalk<String, AtListen>?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var content by remember { mutableStateOf<AtReadPage<String, AtListen>?>(null) }
    var status by remember { mutableStateOf<AtListen?>(null) }
    var profile by remember { mutableStateOf<AtPublicProfile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var auxError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: AtFailure) { error = e.problem.text }
            catch (_: java.io.IOException) { error = AtProblem.NETWORK.text }
            catch (_: java.time.DateTimeException) { error = "Enter dates as YYYY-MM-DD." }
            finally { busy = false }
        }
    }

    fun load(source: AtTarget) = run {
        val start = from.takeIf(String::isNotBlank)?.let { date(it, false) }
        val end = through.takeIf(String::isNotBlank)?.let { date(it, true) }
        if (start != null && end != null && start >= end) throw java.time.DateTimeException("date range")
        val resolved = AtHistoryQuery(source, atPublic.resolve(actor), start, end)
        val walk = atPublic.history(resolved)
        content = walk.page(1)
        query = resolved
        pager = walk
        page = 1
        auxError = null
        status = null
        profile = null
        if (start == null && end == null) {
            status = try { atPublic.live(resolved) } catch (e: java.io.IOException) { auxError = "Live status unavailable."; null }
        }
        profile = try { atPublic.profile(resolved) } catch (e: java.io.IOException) {
            auxError = listOfNotNull(auxError, "Public profile unavailable.").joinToString(" "); null
        }
    }

    LaunchedEffect(defaultDid) { if (!defaultDid.isNullOrEmpty()) load(target) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AtTarget.entries.forEach { source ->
                FilterChip(target == source, onClick = { target = source; if (actor.isNotBlank()) load(source) },
                    enabled = !busy, label = { Text(source.label) })
            }
        }
        OutlinedTextField(actor, { actor = it }, enabled = !busy, singleLine = true, label = { Text("Public handle or DID") })
        OutlinedTextField(from, { from = it }, enabled = !busy, singleLine = true, label = { Text("From date (UTC, optional)") })
        OutlinedTextField(through, { through = it }, enabled = !busy, singleLine = true, label = { Text("Through date (UTC, optional)") })
        Button(enabled = !busy && actor.isNotBlank(), onClick = { load(target) }) { Text("Load / refresh") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        auxError?.let { Text(it) }
        val loaded = query
        if (loaded != null) {
            val title = if (loaded.target == AtTarget.TEAL) "Teal · records on this account’s PDS" else "Rocksky · indexed history (may lag the PDS)"
            Text(title, style = MaterialTheme.typography.titleMedium)
            profile?.let { p ->
                ListItem(headlineContent = { Text(p.displayName ?: p.handle ?: loaded.identity.did) },
                    supportingContent = { Text(p.source) }, leadingContent = { AtArtwork(p.avatar) })
            }
            if (page == 1) status?.let { AtListenRow(it, "Now playing · PDS") }
            content?.let { result ->
                if (result.values.isEmpty()) Text(if (result.next != null) "No matching plays on this page. More pages are available." else "No plays on this page.")
                result.values.forEach { AtListenRow(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy && page > 1, onClick = { run { content = pager?.page(page - 1); page-- } }) { Text("Previous") }
                    Text("Page $page", Modifier.padding(top = 12.dp))
                    OutlinedButton(enabled = !busy && result.next != null && page < 400, onClick = {
                        run { content = pager?.page(page + 1); page++ }
                    }) { Text("Next") }
                }
            }
        }
    }
}

private fun date(raw: String, followingDay: Boolean): Long = LocalDate.parse(raw.trim()).let {
    if (followingDay) it.plusDays(1) else it
}.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

@Composable
internal fun AtArtwork(url: String?) {
    if (url != null) AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(42.dp))
}

@Composable
internal fun AtListenRow(item: AtListen, label: String? = null) {
    ListItem(headlineContent = { Text(item.title) }, supportingContent = {
        Text(listOfNotNull(label, item.artist.takeIf(String::isNotBlank), item.album, item.time?.let { Instant.ofEpochMilli(it).toString() }).joinToString(" · "))
    }, leadingContent = { AtArtwork(item.image) })
}
