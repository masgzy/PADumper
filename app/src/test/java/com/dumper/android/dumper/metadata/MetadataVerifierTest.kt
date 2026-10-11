package com.dumper.android.dumper.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MetadataVerifierTest {

    private val totalSize = 0x100000L // 1MB candidate

    private val magic = byteArrayOf(0xAF.toByte(), 0x1B.toByte(), 0xB1.toByte(), 0xFA.toByte())
    private val customContainerMagic = byteArrayOf(0x45, 0x4E, 0x43, 0x00) // "ENC\0" ASCII container tag

    /** Builds a well-formed metadata header, with overridable fields. */
    private fun header(
        magic: ByteArray = this.magic,
        version: Int = 29,
        stringLiteralOffset: Int = 0x40, stringLiteralSize: Int = 0x1000,
        stringLiteralDataOffset: Int = 0x1040, stringLiteralDataSize: Int = 0x2000,
        stringOffset: Int = 0x3040, stringSize: Int = 0x3000,
        methodsOffset: Int = 0x6040, methodsSize: Int = 0x5000
    ): ByteArray {
        val buf = ByteBuffer.allocate(MetadataVerifier.HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(magic)
        buf.putInt(version)
        buf.putInt(stringLiteralOffset).putInt(stringLiteralSize)
        buf.putInt(stringLiteralDataOffset).putInt(stringLiteralDataSize)
        buf.putInt(stringOffset).putInt(stringSize)
        buf.putInt(0x11).putInt(0x22)          // events (unchecked)
        buf.putInt(0x33).putInt(0x44)          // properties (unchecked)
        buf.putInt(methodsOffset).putInt(methodsSize)
        return buf.array()
    }

    @Test
    fun `valid header passes`() {
        val result = MetadataVerifier.check(header(), totalSize)
        assertTrue(result.reason, result.valid)
        assertEquals(29, result.version)
    }

    @Test
    fun `other plausible versions pass`() {
        for (version in intArrayOf(19, 24, 27, 31, 40)) {
            val result = MetadataVerifier.check(header(version = version), totalSize)
            assertTrue("v$version: ${result.reason}", result.valid)
            assertEquals(version, result.version)
        }
    }

    @Test
    fun `custom encrypted container is rejected`() {
        // Encrypted assets carry an ASCII container tag instead of the
        // il2cpp magic; everything after the tag is cipher output. Build one
        // with a plausible-looking (encrypted) tail.
        val asset = header(magic = customContainerMagic, version = 0x6BE9AEB9.toInt())
        val result = MetadataVerifier.check(asset, totalSize)
        assertFalse(result.valid)
        assertTrue(result.reason, result.reason.contains("container"))
    }

    @Test
    fun `bad magic is rejected`() {
        val result = MetadataVerifier.check(header(magic = byteArrayOf(1, 2, 3, 4)), totalSize)
        assertFalse(result.valid)
        assertTrue(result.reason, result.reason.contains("bad magic"))
    }

    @Test
    fun `implausible version is rejected`() {
        // Encrypted garbage usually produces huge version ints
        for (version in intArrayOf(0, 18, 41, 0x6BE9AEB9.toInt())) {
            val result = MetadataVerifier.check(header(version = version), totalSize)
            assertFalse("v$version should fail", result.valid)
            assertTrue(result.reason, result.reason.contains("version"))
        }
    }

    @Test
    fun `section beyond candidate size is rejected`() {
        val result = MetadataVerifier.check(
            header(methodsOffset = 0xFF000, methodsSize = 0x5000), // 0xFF000+0x5000 > 0x100000
            totalSize
        )
        assertFalse(result.valid)
        assertTrue(result.reason, result.reason.contains("exceeds"))
    }

    @Test
    fun `empty or negative section is rejected`() {
        // Encrypted headers commonly decode to negative ints
        val negative = MetadataVerifier.check(header(stringOffset = -0x1234), totalSize)
        assertFalse(negative.valid)

        val zero = MetadataVerifier.check(header(methodsSize = 0), totalSize)
        assertFalse(zero.valid)
    }

    @Test
    fun `short header is rejected`() {
        val result = MetadataVerifier.check(ByteArray(16), totalSize)
        assertFalse(result.valid)
        assertTrue(result.reason, result.reason.contains("too small"))
    }
}
