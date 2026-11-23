package com.arn.scrobble.db

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Entity(tableName = "AtLiveOwners", primaryKeys = ["did", "target"])
data class AtLiveOwner(val did: String, val target: String, val playback: String, val cid: String, val expires: Long)

@Dao
interface AtLiveDao {
    @Query("SELECT * FROM AtLiveOwners WHERE did = :did AND target = :target")
    suspend fun owner(did: String, target: String): AtLiveOwner?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(owner: AtLiveOwner)

    @Query("DELETE FROM AtLiveOwners WHERE did = :did AND target = :target AND cid = :cid")
    suspend fun forget(did: String, target: String, cid: String)
}
