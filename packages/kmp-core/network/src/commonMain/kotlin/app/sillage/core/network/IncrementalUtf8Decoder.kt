package app.sillage.core.network

internal class IncrementalUtf8Decoder {
    private var trailingBytes = ByteArray(0)

    fun append(chunk: ByteArray): String {
        if (chunk.isEmpty()) return ""

        val bytes = if (trailingBytes.isEmpty()) {
            chunk
        } else {
            trailingBytes + chunk
        }
        val completeLength = completePrefixLength(bytes)
        val decoded = bytes.decodeStrictly(endIndex = completeLength)
        trailingBytes = bytes.copyOfRange(completeLength, bytes.size)
        return decoded
    }

    fun finish(): String {
        if (trailingBytes.isEmpty()) return ""
        throw InvalidUtf8Exception()
    }
}

internal class InvalidUtf8Exception : IllegalArgumentException("Invalid UTF-8 stream.")

private fun completePrefixLength(bytes: ByteArray): Int {
    var index = 0
    while (index < bytes.size) {
        val first = bytes[index].toInt() and 0xff
        val sequenceLength = when {
            first <= 0x7f -> 1
            first in 0xc2..0xdf -> 2
            first in 0xe0..0xef -> 3
            first in 0xf0..0xf4 -> 4
            else -> throw InvalidUtf8Exception()
        }
        val sequenceEnd = index + sequenceLength
        val availableEnd = minOf(sequenceEnd, bytes.size)
        for (continuationIndex in index + 1 until availableEnd) {
            val continuation = bytes[continuationIndex].toInt() and 0xff
            val offset = continuationIndex - index
            val valid = when {
                offset > 1 -> continuation in 0x80..0xbf
                first == 0xe0 -> continuation in 0xa0..0xbf
                first == 0xed -> continuation in 0x80..0x9f
                first == 0xf0 -> continuation in 0x90..0xbf
                first == 0xf4 -> continuation in 0x80..0x8f
                else -> continuation in 0x80..0xbf
            }
            if (!valid) throw InvalidUtf8Exception()
        }
        if (sequenceEnd > bytes.size) return index
        index = sequenceEnd
    }
    return bytes.size
}

private fun ByteArray.decodeStrictly(endIndex: Int): String =
    decodeToString(
        startIndex = 0,
        endIndex = endIndex,
        throwOnInvalidSequence = true,
    )
