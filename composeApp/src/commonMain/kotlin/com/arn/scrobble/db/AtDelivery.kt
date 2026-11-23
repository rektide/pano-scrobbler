package com.arn.scrobble.db

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.arn.scrobble.api.atproto.atTidAt

@Entity(tableName = "AtDeliveries", primaryKeys = ["did", "tid", "target"], indices = [Index("did", "state", "nextAt")])
data class AtDelivery(
    val did: String,
    val tid: String,
    val target: String,
    val playedAt: Long,
    val original: String,
    val payload: String?,
    val state: String = "READY",
    val attempts: Int = 0,
    val nextAt: Long = 0,
    val claim: String? = null,
    val uri: String? = null,
    val cid: String? = null,
)

@Dao
interface AtDeliveryDao {
    @Query("SELECT * FROM PendingScrobbles WHERE event = 'scrobble' AND _id NOT IN (SELECT pendingId FROM AtLegacyReviews) ORDER BY timestamp DESC, _id DESC LIMIT 20")
    suspend fun genericPlaysForReview(): List<PendingScrobble>

    @Query("SELECT * FROM PendingScrobbles WHERE event = 'scrobble' AND _id = :id AND _id NOT IN (SELECT pendingId FROM AtLegacyReviews)")
    suspend fun unreviewedPlay(id: Long): PendingScrobble?

    @Insert suspend fun markReviewed(review: AtLegacyReview)

    @Transaction
    suspend fun reviewGenericPlay(original: PendingScrobble, rows: List<AtDelivery>, stamp: String, micros: Long, clockId: Int): Boolean {
        if (unreviewedPlay(original._id)?.scrobbleData != original.scrobbleData || enrollment()?.stamp != stamp) return false
        if (rows.isNotEmpty()) stageEvent(rows, micros, clockId)
        markReviewed(AtLegacyReview(original._id, rows.firstOrNull()?.did))
        return true
    }

    @Insert
    suspend fun stage(deliveries: List<AtDelivery>)

    @Query("SELECT * FROM AtEnrollment WHERE slot = 1")
    suspend fun enrollment(): AtEnrollmentRow?

    @Upsert suspend fun writeEnrollment(row: AtEnrollmentRow)

    @Transaction
    suspend fun enroll(row: AtEnrollmentRow, resumeDid: String?) {
        writeEnrollment(row)
        if (resumeDid != null) resume(resumeDid)
    }

    @Query("SELECT lastMicros FROM AtEventClock WHERE slot = 1")
    suspend fun lastMicros(): Long?

    @Upsert suspend fun writeClock(clock: AtEventClock)

    @Query("SELECT EXISTS(SELECT 1 FROM AtDeliveries WHERE did = :did AND tid = :tid)")
    suspend fun containsEvent(did: String, tid: String): Boolean

    // Check for pre-clock rows, advance, and stage *all* targets in the same write transaction.
    @Transaction
    suspend fun stageEvent(rows: List<AtDelivery>, observedMicros: Long, clockId: Int): String {
        var micros = maxOf(observedMicros, (lastMicros() ?: 0) + 1)
        var tid = atTidAt(micros, clockId)
        while (containsEvent(rows.first().did, tid)) tid = atTidAt(++micros, clockId)
        writeClock(AtEventClock(lastMicros = micros))
        stage(rows.map { it.copy(tid = tid) })
        return tid
    }

    @Query("SELECT * FROM AtDeliveries WHERE did = :did AND target IN (:targets) AND state IN ('READY','RETRY','SENDING') AND (:tid IS NULL OR tid = :tid) AND nextAt <= :now ORDER BY nextAt, tid LIMIT 30")
    suspend fun actionable(did: String, targets: List<String>, tid: String?, now: Long): List<AtDelivery>

    @Query("UPDATE AtDeliveries SET state = 'SENDING', claim = :newClaim, attempts = attempts + 1, nextAt = :lease WHERE did = :did AND tid = :tid AND target = :target AND attempts = :attempts AND nextAt <= :now AND state IN ('READY','RETRY','SENDING','PAUSED','RECONNECT','PERMISSION')")
    suspend fun claim(did: String, tid: String, target: String, attempts: Int, newClaim: String, now: Long, lease: Long): Int

    @Query("UPDATE AtDeliveries SET payload = :payload WHERE did = :did AND tid = :tid AND target = :target AND claim = :claim AND payload IS NULL")
    suspend fun freezeMatch(did: String, tid: String, target: String, claim: String, payload: String): Int

    @Query("UPDATE AtDeliveries SET state = :state, nextAt = :nextAt, claim = NULL, uri = :uri, cid = :cid WHERE did = :did AND tid = :tid AND target = :target AND claim = :claim")
    suspend fun finish(did: String, tid: String, target: String, claim: String, state: String, nextAt: Long, uri: String?, cid: String?)

    @Query("DELETE FROM AtDeliveries WHERE state = 'ACK'")
    suspend fun pruneConfirmed()

    @Transaction
    suspend fun advance(did: String, tid: String, target: String, claim: String, state: String, nextAt: Long, uri: String?, cid: String?, stamp: String) {
        // If reconnect/settings won the race with a stale worker failure, don't re-pause the newly armed row.
        val stalePause = state in setOf("RECONNECT", "PERMISSION") && enrollment()?.stamp != stamp
        finish(did, tid, target, claim, if (stalePause) "READY" else state, if (stalePause) 0 else nextAt, uri, cid)
        if (state == "ACK") pruneConfirmed()
    }

    @Query("SELECT COUNT(*) FROM AtDeliveries WHERE did = :did AND target IN (:targets) AND state IN ('READY','RETRY','SENDING')")
    suspend fun countRunnable(did: String, targets: List<String>): Int

    @Query("SELECT * FROM AtDeliveries WHERE did = :did AND state != 'ACK' ORDER BY playedAt DESC, tid DESC LIMIT 40")
    suspend fun pending(did: String): List<AtDelivery>

    @Query("UPDATE AtDeliveries SET nextAt = 0 WHERE did = :did AND state IN ('PAUSED','RETRY','RECONNECT','PERMISSION')")
    suspend fun retry(did: String)

    @Query("UPDATE AtDeliveries SET state = 'READY', nextAt = 0 WHERE did = :did AND state IN ('PAUSED','RECONNECT','PERMISSION')")
    suspend fun resume(did: String)
}
