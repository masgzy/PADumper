package com.dumper.android.dumper.metadata

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Validates that a candidate buffer really is a usable global-metadata.dat.
 *
 * Games with anti-tamper protection ship an ENCRYPTED global-metadata.dat;
 * the encrypted asset may still be mapped in memory and it looks like a
 * perfect candidate to a path- or magic-based scan. Dumping it produces a
 * file Il2CppDumper rejects ("encrypted metadata"). This verifier separates
 * real, decrypted metadata from such assets.
 *
 * Pure JVM logic so it is unit-testable.
 */
object MetadataVerifier {

    /** Bytes needed for the checks below (through methodsOffset/methodsSize). */
    const val HEADER_SIZE = 0x40

    const val MIN_VERSION = 19
    const val MAX_VERSION = 40

    data class Result(val valid: Boolean, val version: Int, val reason: String)

    /**
     * @param header first [HEADER_SIZE] bytes of the candidate
     * @param totalSize size of the whole candidate (region or file); header
     *        sections must fit inside it
     */
    fun check(header: ByteArray, totalSize: Long): Result {
        if (header.size < HEADER_SIZE)
            return Result(false, 0, "header too small (${header.size} bytes)")

        if (!header.copyOfRange(0, 4).contentEquals(MetadataFinder.GLOBAL_METADATA_BYTES)) {
            if (looksLikeCustomContainer(header))
                return Result(false, 0, "custom encrypted metadata container (non-il2cpp header), not a decrypted copy")
            return Result(false, 0, "bad magic ${header[0].toHexString()} ${header[1].toHexString()} ${header[2].toHexString()} ${header[3].toHexString()}")
        }

        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val version = buf.getInt(4)
        if (version < MIN_VERSION || version > MAX_VERSION)
            return Result(false, version, "implausible metadata version $version")

        // The first header entries are identical across every il2cpp version
        // this tool can encounter (v19..v31+): stringLiteral, stringLiteralData,
        // string, then (events, properties,) methods. Encrypted garbage fails
        // these with overwhelming probability.
        // Section pairs that must be present and inside the candidate bounds.
        val requiredSections = listOf(
            0x08 to "stringLiteral",     // offset 0x08, size 0x0C
            0x10 to "stringLiteralData", // offset 0x10, size 0x14
            0x18 to "string",            // offset 0x18, size 0x1C
            0x30 to "methods"            // offset 0x30, size 0x34
        )
        for ((base, name) in requiredSections) {
            val offset = buf.getInt(base)
            val size = buf.getInt(base + 4)
            if (offset <= 0 || size <= 0)
                return Result(false, version, "$name section is empty (offset $offset, size $size)")
            if (offset.toLong() + size.toLong() > totalSize)
                return Result(false, version, "$name section (0x${offset.toHexString()}+0x${size.toHexString()}) exceeds candidate size 0x${totalSize.toHexString()}")
        }

        return Result(true, version, "ok")
    }

    private fun Int.toHexString(): String = Integer.toHexString(this)

    private fun Long.toHexString(): String = java.lang.Long.toHexString(this)

    private fun Byte.toHexString(): String = (toInt() and 0xFF).toString(16).padStart(2, '0')

    /**
     * Encrypted metadata assets usually keep a small ASCII signature in the
     * first bytes (a container tag, often followed by NUL padding) instead
     * of the binary il2cpp magic. Pure cipher output, by contrast, decodes
     * to mostly non-printable bytes.
     */
    private fun looksLikeCustomContainer(header: ByteArray): Boolean {
        for (i in 0 until 3) {
            val b = header[i].toInt() and 0xFF
            if (b < 0x20 || b > 0x7E) return false
        }
        return true
    }
}
