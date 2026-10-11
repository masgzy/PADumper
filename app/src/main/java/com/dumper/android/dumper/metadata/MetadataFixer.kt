package com.dumper.android.dumper.metadata

import java.io.File
import java.io.RandomAccessFile

class MetadataFixer {

    /**
     * A dumped global-metadata.dat must start with the global-metadata magic.
     *
     * Files carrying a custom container signature (encrypted metadata assets
     * of protected games) are left untouched: rewriting their header would
     * corrupt an encrypted asset that dedicated tools may still be able to
     * use.
     */
    fun isNeedFixer(file: File): Boolean {
        if (!file.exists() || !file.canRead())
            return false

        val magic = MetadataFinder.GLOBAL_METADATA_BYTES
        val header = ByteArray(MetadataVerifier.HEADER_SIZE)
        try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < magic.size.toLong())
                    return true // too small to hold a valid header
                raf.readFully(header)
            }
        } catch (e: Exception) {
            // Unreadable or shorter than a full header - nothing sensible to
            // validate, keep the legacy "repair it" behavior.
            return true
        }

        // Already starts with the correct magic.
        if (header.copyOfRange(0, magic.size).contentEquals(magic))
            return false

        // Repair ONLY when the body proves this is real metadata whose magic
        // got clobbered: splice the expected magic back in and re-run the
        // header verifier. Everything else (custom encrypted containers,
        // random garbage) keeps its original bytes - rewriting them would
        // destroy files that dedicated tools may still be able to handle.
        val repaired = header.copyOf()
        System.arraycopy(magic, 0, repaired, 0, magic.size)
        return MetadataVerifier.check(repaired, file.length()).valid
    }

    /**
     * Overwrite the first bytes of [file] with the global-metadata magic,
     * in place.
     *
     * The old implementation used file.outputStream(), which TRUNCATES the
     * file to zero before writing - it destroyed global-metadata.dat by
     * reducing it to the 4 magic bytes.
     */
    fun fixHeader(file: File, checkFile: Boolean = true): Boolean {
        if (checkFile && !isNeedFixer(file))
            return false

        if (!file.canWrite())
            return false

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(MetadataFinder.GLOBAL_METADATA_BYTES)
        }
        return true
    }
}
