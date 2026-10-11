package com.dumper.android.utils

import com.anggrayudi.storage.extension.closeStreamQuietly
import com.dumper.android.dumper.exception.DumperException
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

private const val COPY_BUFFER_SIZE = 256 * 1024 // 256 KB

/**
 * Copy [byteSize] bytes of process memory starting at [startPos] into [outputFile].
 */
fun FileChannel.copyToFile(startPos: Long, byteSize: Long, outputFile: File) {
    val outputStream = outputFile.outputStream()

    // A 256 KB buffer cuts the syscall count by orders of magnitude compared
    // to the old 1 KB one (a 100 MB dump is ~400 read+write pairs instead of
    // ~200 000).
    var bytesWritten = 0L
    val buffer = ByteBuffer.allocate(COPY_BUFFER_SIZE)
    try {
        while (bytesWritten < byteSize) {
            buffer.clear()
            val bytesRead = read(buffer, startPos + bytesWritten)
            if (bytesRead <= 0) {
                // -1: position beyond EOF (e.g. the target region vanished
                // because the process died mid-dump); 0: no progress is
                // possible. Throw instead of writing garbage or looping
                // forever (the old code passed -1 to write() and crashed
                // with a misleading IndexOutOfBounds).
                throw DumperException(
                    "Unexpected end of memory at offset $bytesWritten (requested $byteSize bytes)"
                )
            }
            outputStream.write(buffer.array(), 0, bytesRead)
            bytesWritten += bytesRead
        }
        outputStream.flush()
    } finally {
        // The old code only closed the stream on success, leaking the fd
        // whenever the dump failed halfway through.
        outputStream.closeStreamQuietly()
    }
}
