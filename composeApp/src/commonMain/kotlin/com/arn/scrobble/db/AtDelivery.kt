package com.arn.scrobble.db

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.Query

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
    @Insert
    suspend fun stage(deliveries: List<AtDelivery>)

    @Query("SELECT * FROM AtDeliveries WHERE did = :did AND target IN (:targets) AND state IN ('READY','RETRY','SENDING','PAUSED','RECONNECT','PERMISSION') AND (:tid IS NULL OR tid = :tid) AND nextAt <= :now ORDER BY nextAt, tid LIMIT 30")
    suspend fun actionable(did: String, targets: List<String>, tid: String?, now: Long): List<AtDelivery>

    @Query("UPDATE AtDeliveries SET state = 'SENDING', claim = :newClaim, attempts = attempts + 1, nextAt = :lease WHERE did = :did AND tid = :tid AND target = :target AND attempts = :attempts AND nextAt <= :now AND state IN ('READY','RETRY','SENDING','PAUSED','RECONNECT','PERMISSION')")
    suspend fun claim(did: String, tid: String, target: String, attempts: Int, newClaim: String, now: Long, lease: Long): Int

    @Query("UPDATE AtDeliveries SET state = :state, nextAt = :nextAt, claim = NULL, uri = :uri, cid = :cid WHERE did = :did AND tid = :tid AND target = :target AND claim = :claim")
    suspend fun finish(did: String, tid: String, target: String, claim: String, state: String, nextAt: Long, uri: String?, cid: String?)

    @Query("SELECT COUNT(*) FROM AtDeliveries WHERE did = :did AND target IN (:targets) AND state IN ('READY','RETRY','SENDING')")
    suspend fun countRunnable(did: String, targets: List<String>): Int

    @Query("SELECT * FROM AtDeliveries WHERE did = :did AND state != 'ACK' ORDER BY playedAt DESC, tid DESC LIMIT 40")
    suspend fun pending(did: String): List<AtDelivery>

    @Query("UPDATE AtDeliveries SET nextAt = 0 WHERE did = :did AND state IN ('PAUSED','RETRY','RECONNECT','PERMISSION')")
    suspend fun retry(did: String)
}
