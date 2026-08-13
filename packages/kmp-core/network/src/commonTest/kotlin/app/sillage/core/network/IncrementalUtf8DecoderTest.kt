package app.sillage.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class IncrementalUtf8DecoderTest {
    @Test
    fun decodesEveryPossibleTwoChunkBoundary() {
        val expected = "ASCII, \u4e2d\u6587, emoji \ud83d\ude80, accents cafe\u0301"
        val bytes = expected.encodeToByteArray()

        for (boundary in 0..bytes.size) {
            val decoder = IncrementalUtf8Decoder()
            val decoded = buildString {
                append(decoder.append(bytes.copyOfRange(0, boundary)))
                append(decoder.append(bytes.copyOfRange(boundary, bytes.size)))
                append(decoder.finish())
            }

            assertEquals(expected, decoded, "boundary=$boundary")
        }
    }

    @Test
    fun retainsPartialCodePointAcrossSeveralChunks() {
        val bytes = "\ud83d\ude80".encodeToByteArray()
        val decoder = IncrementalUtf8Decoder()

        assertEquals("", decoder.append(bytes.copyOfRange(0, 1)))
        assertEquals("", decoder.append(bytes.copyOfRange(1, 2)))
        assertEquals("", decoder.append(bytes.copyOfRange(2, 3)))
        assertEquals("\ud83d\ude80", decoder.append(bytes.copyOfRange(3, 4)))
        assertEquals("", decoder.finish())
    }

    @Test
    fun rejectsInvalidContinuationByte() {
        val decoder = IncrementalUtf8Decoder()

        assertFails {
            decoder.append(byteArrayOf(0xe2.toByte(), 0x28, 0xa1.toByte()))
        }
    }

    @Test
    fun rejectsOverlongEncoding() {
        val decoder = IncrementalUtf8Decoder()

        assertFails {
            decoder.append(byteArrayOf(0xe0.toByte(), 0x80.toByte(), 0x80.toByte()))
        }
    }

    @Test
    fun rejectsUtf16SurrogateEncoding() {
        val decoder = IncrementalUtf8Decoder()

        assertFails {
            decoder.append(byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()))
        }
    }

    @Test
    fun rejectsCodePointAboveUnicodeRange() {
        val decoder = IncrementalUtf8Decoder()

        assertFails {
            decoder.append(byteArrayOf(0xf4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()))
        }
    }

    @Test
    fun rejectsIncompleteCodePointAtEndOfStream() {
        val decoder = IncrementalUtf8Decoder()

        assertEquals("", decoder.append(byteArrayOf(0xf0.toByte(), 0x9f.toByte())))
        assertFails { decoder.finish() }
    }
}
