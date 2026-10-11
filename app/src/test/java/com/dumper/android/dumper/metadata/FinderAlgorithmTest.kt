package com.dumper.android.dumper.metadata

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class FinderAlgorithmTest {

    private fun metadataHeader(versionByte: Byte): ByteArray =
        byteArrayOf(0xAF.toByte(), 0x1B.toByte(), 0xB1.toByte(), 0xFA.toByte(), versionByte, 0x00)

    @Test
    fun `global-metadata pattern matches a real header`() {
        // version 29 (0x1D) - a common global-metadata.dat version
        val buffer = metadataHeader(0x1D)
        assertTrue(containsPatternWithWildcard(buffer, MetadataFinder.GLOBAL_METADATA_PATTERN))
    }

    @Test
    fun `wildcard accepts any version byte`() {
        for (v in byteArrayOf(0x00, 0x1D, 0x24, 0x7F.toByte())) {
            assertTrue(
                "version byte 0x%02X should match".format(v),
                containsPatternWithWildcard(metadataHeader(v), MetadataFinder.GLOBAL_METADATA_PATTERN)
            )
        }
    }

    @Test
    fun `fifth byte must be zero`() {
        val bad = byteArrayOf(0xAF.toByte(), 0x1B.toByte(), 0xB1.toByte(), 0xFA.toByte(), 0x1D, 0x05)
        assertFalse(containsPatternWithWildcard(bad, MetadataFinder.GLOBAL_METADATA_PATTERN))
    }

    @Test
    fun `magic bytes must match exactly`() {
        val bad = byteArrayOf(0xAF.toByte(), 0x1B.toByte(), 0xB1.toByte(), 0xFB.toByte(), 0x1D, 0x00)
        assertFalse(containsPatternWithWildcard(bad, MetadataFinder.GLOBAL_METADATA_PATTERN))
    }

    @Test
    fun `one mismatching byte prevents a match`() {
        // Regression test: the old implementation continued the inner loop on
        // mismatch and then returned true unconditionally, so ANY content
        // large enough was reported as a match.
        val wrongMagic = byteArrayOf(0xAF.toByte(), 0x1C.toByte(), 0xB1.toByte(), 0xFA.toByte(), 0x1D, 0x00)
        val wrongReserved = byteArrayOf(0xAF.toByte(), 0x1B.toByte(), 0xB1.toByte(), 0xFA.toByte(), 0x1D, 0x01)
        assertFalse(containsPatternWithWildcard(wrongMagic, MetadataFinder.GLOBAL_METADATA_PATTERN))
        assertFalse(containsPatternWithWildcard(wrongReserved, MetadataFinder.GLOBAL_METADATA_PATTERN))
    }

    @Test
    fun `matches pattern at an offset inside the buffer`() {
        val buffer = ByteArray(64) { 0x11 }
        metadataHeader(0x1D).copyInto(buffer, destinationOffset = 40)
        assertTrue(containsPatternWithWildcard(buffer, MetadataFinder.GLOBAL_METADATA_PATTERN))
    }

    @Test
    fun `buffer smaller than pattern does not match`() {
        val buffer = byteArrayOf(0xAF.toByte(), 0x1B.toByte())
        assertFalse(containsPatternWithWildcard(buffer, MetadataFinder.GLOBAL_METADATA_PATTERN))
    }

    @Test
    fun `finds zeroVector anywhere inside the scan window`() {
        val needle = "zeroVector".toByteArray(Charsets.UTF_8)

        val window = ByteArray(2000)
        needle.copyInto(window, destinationOffset = 1000)
        assertTrue(containsAnyByteArray(ByteBuffer.wrap(window), needle))

        val windowAtStart = ByteArray(2000)
        needle.copyInto(windowAtStart, destinationOffset = 0)
        assertTrue(containsAnyByteArray(ByteBuffer.wrap(windowAtStart), needle))

        val windowNoMatch = ByteArray(2000)
        assertFalse(containsAnyByteArray(ByteBuffer.wrap(windowNoMatch), needle))
    }
}
