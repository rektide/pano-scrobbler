package com.arn.scrobble.api.atproto

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
    var enrich by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(AtSection.HISTORY) }
    var period by remember { mutableStateOf(AtPeriod.MONTH) }
    var results by remember { mutableStateOf<AtReadPage<String, AtInsight>?>(null) }
    var insightWalk by remember { mutableStateOf<AtPageWalk<String, AtInsight>?>(null) }
    var insightPage by remember { mutableIntStateOf(1) }
    var actionAccount by remember { mutableStateOf<AtAccount?>(null) }
    var selected by remember { mutableStateOf<AtListen?>(null) }
    var confirmDelete by remember { mutableStateOf<AtRef?>(null) }
    var subject by remember { mutableStateOf<AtRef?>(null) }
    var loved by remember { mutableStateOf<Boolean?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
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
        section = AtSection.HISTORY
        selected = null
        notice = null
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

    fun loadSection(view: AtSection, window: AtPeriod) {
        val current = query ?: return
        section = view
        period = window
        selected = null
        notice = null
        results = null
        insightPage = 1
        insightWalk = null
        if (view == AtSection.HISTORY || current.target.limitation(view) != null ||
            (current.target == AtTarget.ROCKSKY && view == AtSection.ACTIVITY && window == AtPeriod.ALL)) return
        run {
            val walk = atPublic.insights(current, view, window)
            results = walk.page(1)
            insightWalk = walk
        }
    }

    LaunchedEffect(defaultDid) { if (!defaultDid.isNullOrEmpty()) load(target) }
    LaunchedEffect(status) {
        val expires = status?.expiry ?: return@LaunchedEffect
        kotlinx.coroutines.delay((expires - System.currentTimeMillis()).coerceAtLeast(0))
        status = null
    }

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
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AtSection.entries.forEach { view ->
                    FilterChip(section == view, onClick = { loadSection(view, period) }, enabled = !busy, label = { Text(view.label) })
                }
            }
            notice?.let { Text(it) }
            if (section == AtSection.HISTORY) {
                if (page == 1) status?.let { AtListenRow(it, "Now playing · PDS") }
                if (loaded.target == AtTarget.TEAL) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(enrich, { enrich = it })
                    Text("Look up album artwork from Cover Art Archive using the release’s MusicBrainz ID")
                }
                content?.let { result ->
                if (result.values.isEmpty()) Text(if (result.next != null) "No matching plays on this page. More pages are available." else "No plays on this page.")
                result.values.forEach { item ->
                    val art = item.image ?: item.releaseMbid?.takeIf { enrich }?.let { "https://coverartarchive.org/release/$it/front-250" }
                    AtListenRow(item.copy(image = art), action = {
                        TextButton(enabled = !busy, onClick = {
                            selected = item
                            subject = null
                            loved = null
                            notice = null
                            // Anonymous scrolling never opens the credential vault. Only explicit record actions do.
                            run { actionAccount = try { atAuthority.account() } catch (_: AtFailure) { null } }
                        }) { Text("Actions") }
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy && page > 1, onClick = { run { selected = null; content = pager?.page(page - 1); page-- } }) { Text("Previous") }
                    Text("Page $page", Modifier.padding(top = 12.dp))
                    OutlinedButton(enabled = !busy && result.next != null && page < 400, onClick = {
                        run { selected = null; content = pager?.page(page + 1); page++ }
                    }) { Text("Next") }
                }
                }
            } else {
                if (section in setOf(AtSection.ARTISTS, AtSection.RELEASES, AtSection.ACTIVITY)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AtPeriod.entries.forEach { window ->
                            FilterChip(window == period, onClick = { loadSection(section, window) }, enabled = !busy, label = { Text(window.label) })
                        }
                    }
                    if (loaded.target == AtTarget.TEAL) Text("Teal’s index supplies up to 50 ranks. History date filters do not apply to this view.")
                }
                val limitation = loaded.target.limitation(section) ?: "Rocksky activity is available for the 7- or 30-day range."
                    .takeIf { loaded.target == AtTarget.ROCKSKY && section == AtSection.ACTIVITY && period == AtPeriod.ALL }
                limitation?.let { Text(it) }
                results?.let { result ->
                    if (result.values.isEmpty()) Text("No ${section.label.lowercase()} entries on this page.")
                    result.values.forEach { item ->
                        ListItem(headlineContent = { Text(item.name) }, supportingContent = { item.detail?.let { Text(it) } },
                            leadingContent = { AtArtwork(item.image) }, trailingContent = {
                                item.actor?.let { did -> TextButton(enabled = !busy, onClick = { actor = did; load(target) }) { Text("Browse") } }
                            })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy && insightPage > 1, onClick = { run { results = insightWalk?.page(insightPage - 1); insightPage-- } }) { Text("Previous") }
                        Text("Page $insightPage", Modifier.padding(top = 12.dp))
                        OutlinedButton(enabled = !busy && result.next != null && insightPage < 400, onClick = { run { results = insightWalk?.page(insightPage + 1); insightPage++ } }) { Text("Next") }
                    }
                }
            }
        }
    }
    val current = query
    val item = selected
    if (current != null && item != null && confirmDelete == null) {
        val member = actionAccount?.takeIf { it.identity?.did == current.identity.did && it.method != null }
        val deleteEnabled = current.target in member?.settings?.delete.orEmpty() && member?.missing?.get(current.target.play)?.contains("delete") != true
        val likesEnabled = member?.settings?.likes == true && member.missing[ROCKSKY_LIKE].isNullOrEmpty()
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(item.title) }, text = {
            Column {
                if (!deleteEnabled) Text("Delete requires the owning DID and the delete permission in AT Protocol account settings.")
                else Button(enabled = !busy, onClick = { run { confirmDelete = atActions.ownRecord(current, item) } }) { Text("Review ${current.target.label} deletion") }
                when {
                    current.target == AtTarget.TEAL -> Text(current.target.limitation(AtSection.LOVED).orEmpty())
                    !likesEnabled -> Text("Rocksky love actions require the owning DID and the love permissions in account settings.")
                    item.catalogUri == null -> Text("Rocksky does not provide a catalog song for this listen.")
                    subject == null -> OutlinedButton(enabled = !busy, onClick = { run {
                        val ref = atActions.rockskySubject(item)
                        loved = atActions.matchingLikes(current.identity, ref).isNotEmpty()
                        subject = ref
                    } }) { Text("Check my Rocksky love state") }
                    else -> OutlinedButton(enabled = !busy, onClick = { run {
                        loved = atActions.setLoved(current.identity, subject!!, loved != true)
                        notice = "Love state confirmed on your PDS. Rocksky’s index may take time to update."
                    } }) { Text(if (loved == true) "Unlove on Rocksky" else "Love on Rocksky") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Done") } })
    }
    confirmDelete?.let { ref ->
        if (current != null) AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("Delete this ${current.target.label} listen?") }, text = {
            Text("Only this record revision will be deleted:\n${ref.address.uri}\n${ref.cid}")
        }, confirmButton = { TextButton(enabled = !busy, onClick = { run {
            confirmDelete = null
            selected = null
            atActions.delete(ref, current.target)
            content = content?.let { it.copy(values = it.values.filterNot { row -> row.id == ref.address.uri }) }
            notice = "Removed from your PDS. The service index may take time to update."
        } }) { Text("Delete listen") } }, dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } })
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
internal fun AtListenRow(item: AtListen, label: String? = null, action: (@Composable () -> Unit)? = null) {
    ListItem(headlineContent = { Text(item.title) }, supportingContent = {
        Text(listOfNotNull(label, item.artist.takeIf(String::isNotBlank), item.album, item.time?.let { Instant.ofEpochMilli(it).toString() }).joinToString(" · "))
    }, leadingContent = { AtArtwork(item.image) }, trailingContent = action)
}
