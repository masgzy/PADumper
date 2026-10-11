package com.dumper.android.dumper.metadata

import java.io.File
import java.io.RandomAccessFile

class MetadataFixer {

    /**
     * A dumped global-metadata.dat must start with the global-metadata magic.
     */
    fun isNeedFixer(file: File): Boolean {
        if (!file.exists() || !file.canRead())
            return false

        val magic = MetadataFinder.GLOBAL_METADATA_BYTES
        val header = ByteArray(magic.size)
        try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < magic.size.toLong())
                    return true // too small to hold a valid header
                raf.readFully(header)
            }
        } catch (e: Exception) {
            return true // unreadable -> assume broken
        }

        // NOTE: contentEquals() also compares lengths; the old code compared
        // a 5-byte buffer against the 4-byte magic, which is false for every
        // input, so this check always reported "needs fixing".
        return !header.contentEquals(magic)
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
