package com.arn.scrobble.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** Review marker only. Does not change or dequeue the original generic listen. */
@Entity(tableName = "AtLegacyReviews")
data class AtLegacyReview(@PrimaryKey val pendingId: Long, val copiedTo: String?)
