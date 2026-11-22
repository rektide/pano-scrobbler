package com.arn.scrobble.atproto.posting

/** Parse the record CIDv1 / DAG-CBOR / SHA-256 profile, not a content or repository verifier. */
internal fun requireRecordCid(value: String) {
    require(value.length == 59 && value.first() == 'b') { "PDS returned an invalid record CID" }
    val bytes = ByteArray(36)
    var accumulator = 0
    var bits = 0
    var offset = 0
    for (char in value.drop(1)) {
        val digit = "abcdefghijklmnopqrstuvwxyz234567".indexOf(char)
        require(digit >= 0) { "PDS returned an invalid record CID encoding" }
        accumulator = (accumulator shl 5) or digit
        bits += 5
        if (bits >= 8) {
            bits -= 8
            bytes[offset++] = (accumulator shr bits).toByte()
            accumulator = accumulator and ((1 shl bits) - 1)
        }
    }
    require(offset == bytes.size && bits == 2 && accumulator == 0) { "PDS returned a noncanonical record CID" }
    require(bytes[0] == 1.toByte() && bytes[1] == 0x71.toByte() && bytes[2] == 0x12.toByte() && bytes[3] == 0x20.toByte()) {
        "PDS returned an unsupported record CID profile"
    }
}
