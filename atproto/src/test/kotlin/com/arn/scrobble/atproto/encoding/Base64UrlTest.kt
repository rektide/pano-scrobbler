package com.arn.scrobble.atproto.encoding

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Base64UrlTest {
    @Test
    fun `encodes without padding and decodes`() {
        val input = byteArrayOf(0xfb.toByte(), 0xff.toByte(), 0xef.toByte(), 0xfa.toByte())

        val encoded = input.encodeBase64Url()

        assertEquals("-__v-g", encoded)
        assertContentEquals(input, encoded.decodeBase64Url())
    }
}
