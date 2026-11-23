package com.arn.scrobble.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.arn.scrobble.api.atproto.AtIdentity
import com.arn.scrobble.api.atproto.AtSettings
import kotlinx.serialization.json.Json
import java.util.UUID

@Entity(tableName = "AtEnrollment")
data class AtEnrollmentRow(@PrimaryKey val slot: Int = 1, val actor: String?, val settings: String, val stamp: String)

internal data class AtEnrollment(val identity: AtIdentity?, val settings: AtSettings = AtSettings(), val stamp: String = UUID.randomUUID().toString()) {
    fun row() = AtEnrollmentRow(actor = identity?.let { Json.encodeToString(it) }, settings = Json.encodeToString(settings), stamp = stamp)
}

internal interface AtEnrollmentStore {
    suspend fun read(): AtEnrollment?
    suspend fun save(value: AtEnrollment, resume: Boolean = false)
}

internal class RoomAtEnrollment(private val dao: () -> AtDeliveryDao = { PanoDb.db.atDeliveryDao() }) : AtEnrollmentStore {
    override suspend fun read() = dao().enrollment()?.let {
        AtEnrollment(it.actor?.let { body -> Json.decodeFromString<AtIdentity>(body) }, Json.decodeFromString<AtSettings>(it.settings), it.stamp)
    }

    override suspend fun save(value: AtEnrollment, resume: Boolean) =
        dao().enroll(value.row(), value.identity?.did.takeIf { resume })
}

@Entity(tableName = "AtEventClock")
data class AtEventClock(@PrimaryKey val slot: Int = 1, val lastMicros: Long)
