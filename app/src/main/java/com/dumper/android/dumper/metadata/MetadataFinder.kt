package com.dumper.android.dumper.metadata

import android.util.Log
import com.dumper.android.dumper.OutputHandler
import com.dumper.android.dumper.maps.MapLineParser
import com.dumper.android.dumper.maps.MemoryRegions
import com.dumper.android.utils.FileName
import com.dumper.android.utils.TAG
import com.dumper.android.utils.toHex
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class MetadataFinder(private val pid: Int) {

    /**
     * A metadata candidate together with its verification verdict.
     *
     * @param region the memory region to dump
     * @param valid whether [MetadataVerifier] accepted it as real metadata
     * @param version metadata version when known, 0 otherwise
     * @param reason verification verdict / rejection reason
     */
    data class Candidate(
        val region: MapLineParser,
        val valid: Boolean,
        val version: Int,
        val reason: String
    )

    /**
     * Find global-metadata.dat candidates, best first.
     *
     * Protected games map the ENCRYPTED asset (custom protection formats)
     * and hold the decrypted copy in a separate, usually anonymous, buffer.
     * Both are collected here; only buffers whose header verifies as real
     * metadata are returned as usable candidates. When nothing validates,
     * the legacy heuristics still produce one clearly-flagged fallback so
     * the user gets a dump plus an honest verdict instead of silence.
     */
    fun findUnityMetadata(outLog: OutputHandler): List<Candidate> {
        // LinkedHashMap = dedupe by start address, keep priority order:
        // file-backed mapping first, then magic-scan matches.
        val regions = LinkedHashMap<Long, MapLineParser>()
        findByFile(outLog)?.let { regions.putIfAbsent(it.getStartAddress(), it) }
        findByHeader(outLog).forEach { regions.putIfAbsent(it.getStartAddress(), it) }

        val checked = mutableListOf<Candidate>()
        RandomAccessFile("/proc/$pid/mem", "r").use { memFile ->
            for (region in regions.values) {
                verifyRegion(region, memFile.channel)?.let { checked.add(it) }
            }
        }

        val valid = checked.filter { it.valid }
        if (valid.isNotEmpty()) {
            checked.filter { !it.valid }.forEach { candidate ->
                outLog.appendWarning(
                    "Rejected metadata candidate at ${candidate.region.getStartAddress().toHex()}: ${candidate.reason}"
                )
            }
            if (valid.size > MAX_CANDIDATES) {
                outLog.appendInfo("Found ${valid.size} valid copies, dumping the first $MAX_CANDIDATES")
            }
            return valid.take(MAX_CANDIDATES)
        }

        // Nothing validated - fall back so the user still gets a dump,
        // clearly flagged as probably encrypted.
        val fallback = findByFunction(outLog) ?: checked.firstOrNull()?.region
        if (fallback != null) {
            outLog.appendWarning(
                "No candidate has a valid metadata header - the game probably encrypts global-metadata.dat in memory"
            )
            return listOf(Candidate(fallback, valid = false, version = 0, reason = "unverified fallback"))
        }
        return emptyList()
    }

    /**
     * Read the candidate's header from memory and verify it.
     *
     * @return null when the header cannot be read at all (unreadable region)
     */
    private fun verifyRegion(region: MapLineParser, memChannel: FileChannel): Candidate? {
        val header = ByteBuffer.allocate(MetadataVerifier.HEADER_SIZE)
        val bytesRead = try {
            memChannel.read(header, region.getStartAddress())
        } catch (e: Exception) {
            -1
        }
        if (bytesRead < MetadataVerifier.HEADER_SIZE) {
            return null
        }
        val result = MetadataVerifier.check(header.array(), region.getSize())
        return Candidate(region, result.valid, result.version, result.reason)
    }

    private fun findByFile(outLog: OutputHandler): MapLineParser? {
        outLog.appendInfo("Scanning /proc/$pid/maps...")

        val mapsFile = File("/proc/${pid}/maps")
        if (!mapsFile.exists()) {
            throw FileNotFoundException("Failed To Open : ${mapsFile.path}")
        }

        return mapsFile.readLines().find { it.contains("global-metadata.dat") }
            ?.let { MapLineParser(it) }
    }

    /**
     * Scan every readable region for the metadata magic.
     *
     * The old code only probed regions whose maps line contained ".dat" -
     * the decrypted copy of protected games lives in an anonymous buffer
     * with no path at all, so it was structurally invisible to this scan
     * and the encrypted asset got dumped instead.
     */
    private fun findByHeader(outLog: OutputHandler): List<MapLineParser> {
        outLog.appendInfo("Scanning memory regions for the metadata magic (${GLOBAL_METADATA_PATTERN})...")

        val mapsFile = File("/proc/${pid}/maps")
        if (!mapsFile.exists()) {
            throw FileNotFoundException("Failed To Open : ${mapsFile.path}")
        }

        val patternSize = GLOBAL_METADATA_PATTERN.split(" ").size
        val byteHeader = ByteBuffer.allocate(patternSize)
        val matches = mutableListOf<MapLineParser>()

        RandomAccessFile("/proc/${pid}/mem", "r").use { memFile ->
            val memChannel = memFile.channel

            mapsFile.readLines()
                .mapNotNull { runCatching { MapLineParser(it) }.getOrNull() }
                .filter { it.isValid() && it.isReadable() }
                .filter { region ->
                    val path = region.getPath()
                    !path.startsWith("/dev/") && path !in MemoryRegions.SKIP_PATHS &&
                            !filteredPath.any { path.contains(it) }
                }
                .forEach { region ->
                    byteHeader.clear()
                    val bytesRead = try {
                        memChannel.read(byteHeader, region.getStartAddress())
                    } catch (e: Exception) {
                        -1 // unreadable region -> won't match, keep scanning
                    }
                    if (bytesRead == patternSize && containsPatternWithWildcard(byteHeader.array(), GLOBAL_METADATA_PATTERN)) {
                        val path = region.getPath()
                        outLog.appendInfo(
                            "Metadata magic found at ${region.getStartAddress().toHex()} " +
                                    "(${path.ifEmpty { "anonymous" }.FileName}, ${region.getSize().toMB()}MB)"
                        )
                        matches.add(region)
                    }
                }
        }

        return matches
    }

    private val commonFunctionMetadata = "zeroVector"

    private fun findByFunction(outLog: OutputHandler, funcName: String = commonFunctionMetadata): MapLineParser? {
        outLog.appendInfo("Find by using function: $funcName")

        val funcNameBytes = funcName.toByteArray(Charsets.UTF_8)
        val byteAllocate = ByteBuffer.allocate(2000) // 2 KB scan window

        val mapsFile = File("/proc/${pid}/maps")
        if (!mapsFile.exists()) {
            throw FileNotFoundException("Failed To Open : ${mapsFile.path}")
        }

        RandomAccessFile("/proc/${pid}/mem", "r").use { memFile ->
            val memChannel = memFile.channel

            mapsFile.readLines()
                .filter {
                    // mostly on path that has been deleted
                    it.contains("deleted")
                }
                .map { MapLineParser(it) }
                .forEach { region ->
                    var pos = region.getStartAddress()

                    while (pos <= region.getEndAddress()) {

                        // Clear buffer and new position
                        byteAllocate.clear()

                        // A region that cannot be read (unmapped hole,
                        // protected page, process death) must only skip THIS
                        // region - the old code returned null and abandoned
                        // every remaining candidate region.
                        val bytesRead = try {
                            memChannel.read(byteAllocate, pos)
                        } catch (e: Exception) {
                            0
                        }
                        if (bytesRead <= 0)
                            break

                        if (containsAnyByteArray(byteAllocate, funcNameBytes)) {
                            return region
                        }
                        pos += 1024
                    }
                }
        }

        return null
    }

    companion object {
        val filteredPath = listOf("app_process", ".so", ".apk", ".odex", ".ttf", ".oat", ".jar", ".art", ".vdex")

        val GLOBAL_METADATA_BYTES = byteArrayOf(0xAF.toByte(),  0x1B.toByte(), 0xB1.toByte(), 0xFA.toByte())
        const val GLOBAL_METADATA_PATTERN = "AF 1B B1 FA ?? 00"

        /** Upper bound on how many verified copies get dumped. */
        const val MAX_CANDIDATES = 3

        private fun Long.toMB(): Long = this / 1024 / 1024
    }
}
