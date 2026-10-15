package com.cardenaspiero255.jarvis.core

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedImageInputStreamTest {
    @Test fun countsSingleByteReadsAndRejectsOverflow() {
        val stream = BoundedImageInputStream(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), maxBytes = 3)
        assertEquals(1, stream.read())
        assertEquals(2, stream.read())
        assertEquals(3, stream.read())
        assertThrows(IOException::class.java) { stream.read() }
    }

    @Test fun countsBulkReadAndDoesNotPermitHiddenBytesPastLimit() {
        val stream = BoundedImageInputStream(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), maxBytes = 3)
        val output = ByteArray(8)
        assertEquals(3, stream.read(output, 0, output.size))
        assertThrows(IOException::class.java) { stream.read(output, 0, output.size) }
        assertEquals(0, stream.read(output, 0, 0))
    }

    @Test fun skipAlsoConsumesBudgetAndCannotBypassLimit() {
        val stream = BoundedImageInputStream(ByteArrayInputStream(ByteArray(12)), maxBytes = 5)
        assertEquals(3L, stream.skip(3))
        assertEquals(2, stream.read(ByteArray(8)))
        assertThrows(IOException::class.java) { stream.skip(1) }
        assertThrows(IOException::class.java) { stream.read() }
        assertEquals(0L, stream.skip(0))
        assertEquals(0L, stream.skip(-5))
    }

    @Test fun markResetCannotReplenishIoBudget() {
        val stream = BoundedImageInputStream(ByteArrayInputStream(ByteArray(10)), maxBytes = 4)
        stream.mark(5)
        assertEquals(2L, stream.skip(2))
        stream.reset()
        assertEquals(2, stream.read(ByteArray(2)))
        assertThrows(IOException::class.java) { stream.read() }
    }

    @Test fun malformedBulkArgumentsRejectEvenForZeroLength() {
        val stream = BoundedImageInputStream(ByteArrayInputStream(byteArrayOf(1)), maxBytes = 1)
        assertThrows(IndexOutOfBoundsException::class.java) {
            stream.read(ByteArray(1), 2, 0)
        }
        assertEquals(0, stream.read(ByteArray(1), 1, 0))
        assertEquals(1, stream.read())
    }

    @Test fun invalidLimitsNeverProduceUnboundedReader() {
        assertThrows(IllegalArgumentException::class.java) {
            BoundedImageInputStream(ByteArrayInputStream(byteArrayOf(1)), maxBytes = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BoundedImageInputStream(ByteArrayInputStream(byteArrayOf(1)), maxBytes = 16L * 1024 * 1024 + 1)
        }
    }
}
