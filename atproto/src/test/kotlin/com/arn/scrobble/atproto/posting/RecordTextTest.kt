package com.arn.scrobble.atproto.posting

import kotlin.test.Test
import kotlin.test.assertTrue

class RecordTextTest {
    @Test
    fun `truncation is the maximal complete codepoint prefix within the byte budget`() {
        for (text in listOf("abc", "é".repeat(140), "漢".repeat(100), "🎵".repeat(100), "abé漢🎵e\u0301".repeat(70))) {
            for (budget in 0..512) {
                val truncated = text.truncateUtf8(budget)
                truncated.requireRecordText("fixture")
                assertTrue(text.startsWith(truncated))
                val bytes = truncated.toByteArray(Charsets.UTF_8).size
                assertTrue(bytes <= budget)
                if (truncated.length < text.length) {
                    val nextEnd = truncated.length + Character.charCount(text.codePointAt(truncated.length))
                    assertTrue(text.substring(0, nextEnd).toByteArray(Charsets.UTF_8).size > budget)
                }
            }
        }
    }
}
