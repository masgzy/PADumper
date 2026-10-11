package com.dumper.android.dumper

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.dumper.android.dumper.elf.getArchELF
import com.dumper.android.dumper.elf.isELF
import com.dumper.android.dumper.exception.DumperException
import com.dumper.android.dumper.maps.MapLineParser
import com.dumper.android.dumper.maps.MemoryRegions
import com.dumper.android.dumper.metadata.MetadataFinder
import com.dumper.android.dumper.metadata.MetadataFixer
import com.dumper.android.dumper.metadata.MetadataVerifier
import com.dumper.android.dumper.process.Process
import com.dumper.android.dumper.sofixer.Fixer
import com.dumper.android.utils.Bytes_to_MB
import com.dumper.android.utils.DEFAULT_DIR
import com.dumper.android.utils.FileName
import com.dumper.android.utils.copyToFile
import com.dumper.android.utils.toHex
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

/**
 * Dumper class responsible for dumping memory to a file.
 *
 * @property context The application context.
 * @property config The configuration for the dumper.
 * @property outputHandler The handler for output messages.
 */
class Dumper(private val context: Context, private val config: DumperConfig, private val outputHandler: OutputHandler) {
    private var pid: Int? = null

    /**
     * Dump the memory to a file
     * @return 0 if success, -1 if failed
     */
    @SuppressLint("SdCardPath")
    fun dumpFile(): Int {
        try {
            pid = Process.getProcessID(config.processName) ?: throw DumperException("Process not found!")
            outputHandler.appendLine("==========================")
            outputHandler.appendLine("PROCESS: ${config.processName}")
            outputHandler.appendLine("PID: $pid")
            if (config.dumpAllMemory) {
                outputHandler.appendLine("MODE: dump all memory regions")
            } else {
                outputHandler.appendLine("FILE: ${config.file}")
            }

            val fileOutPath =
                if (Shell.isAppGrantedRoot() == false) {
                    "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/$DEFAULT_DIR/${config.processName}"
                } else {
                    "/sdcard/$DEFAULT_DIR/${config.processName}"
                }

            val outputDir = File(fileOutPath)
            if (!outputDir.exists())
                outputDir.mkdirs()

            if (config.dumpAllMemory) {
                dumpAllMemory(outputDir)
            } else {
                dumpSingle(outputDir)
            }

            if (config.isDumpMetadata) {
                dumpMetadata(outputDir)
            }

            outputHandler.appendSuccess("Output: $outputDir")
            outputHandler.appendLine("==========================")
            outputHandler.finish(0)
            return 0
        } catch (e: Throwable) {
            if (e is DumperException) {
                outputHandler.appendError(e.message ?: "Unknown Error")
            } else {
                outputHandler.appendError(e.stackTraceToString())
            }
            outputHandler.appendLine("==========================")
            outputHandler.finish(-1)
            return -1
        }
    }

    /**
     * Single-region dump: locate the mapping matching [DumperConfig.file]
     * (a library name or an explicit start-end address range) and dump it.
     */
    private fun dumpSingle(outputDir: File) {
        val mem = parseMap()

        outputHandler.appendLine("Start Address: ${mem.getStartAddress().toHex()}")
        if (mem.getStartAddress() < 1L) {
            throw DumperException("Invalid start address!")
        }

        outputHandler.appendLine("End Address: ${mem.getEndAddress().toHex()}")
        if (mem.getEndAddress() < 1L) {
            throw DumperException("Invalid end address!")
        }

        outputHandler.appendLine("Size Memory: ${mem.getSize().Bytes_to_MB}MB (${mem.getSize()})")
        if (mem.getSize() < 1L) {
            throw DumperException("Invalid memory size!")
        }

        // copyToFile() creates the output file itself; the old
        // "if (!outputDir.exists()) outputFile.createNewFile()" checked
        // the freshly-created directory instead of the file, so it was
        // effectively dead code.
        val outputFile = File(outputDir, "${mem.getStartAddress().toHex()}-${mem.getEndAddress().toHex()}-${regionLabel(mem)}")

        dump(mem, context.filesDir.absolutePath, outputFile, outputHandler)
    }

    /**
     * Dump every readable memory region of the process.
     *
     * Regions are collected from /proc/[pid]/maps by [MemoryRegions]
     * (unreadable/pseudo regions skipped, adjacent same-allocation mappings
     * merged), then dumped one by one. A single failing region never aborts
     * the whole run - it is logged and skipped.
     */
    private fun dumpAllMemory(outputDir: File) {
        val maps = File("/proc/$pid/maps")
        if (!maps.exists()) {
            throw DumperException("Failed to open : ${maps.path}")
        }

        val regions = MemoryRegions.collect(
            lines = maps.readLines(),
            limitMaxSize = config.limitMaxDumpSize,
            onSkip = { outputHandler.appendWarning(it) }
        )
        if (regions.isEmpty()) {
            throw DumperException("No readable memory regions found!")
        }

        val totalSize = regions.sumOf { it.getSize() }
        outputHandler.appendLine("Regions: ${regions.size} (total ${totalSize.Bytes_to_MB}MB)")
        checkFreeSpace(outputDir, totalSize)

        val fixerPath = context.filesDir.absolutePath
        var dumped = 0
        var failed = 0

        RandomAccessFile("/proc/$pid/mem", "r").use { memFile ->
            val channel = memFile.channel
            for ((index, mem) in regions.withIndex()) {
                val label = regionLabel(mem)
                val outputFile =
                    File(outputDir, "${mem.getStartAddress().toHex()}-${mem.getEndAddress().toHex()}-$label")
                outputHandler.appendLine(
                    "[${index + 1}/${regions.size}] ${mem.getStartAddress().toHex()}-" +
                            "${mem.getEndAddress().toHex()} $label (${mem.getSize().Bytes_to_MB}MB)"
                )
                try {
                    channel.copyToFile(mem.getStartAddress(), mem.getSize(), outputFile)
                    dumped++

                    // The fixer rebuilds ELF program headers from memory;
                    // only attempt it on real ELF libraries.
                    if (config.autoFix && mem.getPath().endsWith(".so") && isELF(channel, mem.getStartAddress())) {
                        runCatching { fixDump(channel, mem, outputFile, fixerPath, outputHandler) }
                            .onFailure { outputHandler.appendWarning("Fix failed: ${it.message}") }
                    }
                } catch (e: Throwable) {
                    failed++
                    outputFile.delete()
                    outputHandler.appendWarning("Failed: ${e.message ?: e.toString()}")
                }
            }
        }

        if (dumped == 0) {
            throw DumperException("All $failed regions failed to dump!")
        }

        val summary = "Dumped $dumped/${regions.size} regions"
        if (failed > 0) {
            outputHandler.appendWarning("$summary, $failed failed")
        } else {
            outputHandler.appendSuccess(summary)
        }
    }

    /**
     * Dump global-metadata.dat and repair its header.
     *
     * Candidates are pre-verified by [MetadataFinder]; every verified copy
     * is dumped (protected games can hold more than one), and each dump is
     * re-checked afterwards so encrypted/corrupt results are called out
     * instead of silently reported as success.
     */
    private fun dumpMetadata(outputDir: File) {
        val candidates = MetadataFinder(pid!!).findUnityMetadata(outputHandler)
        if (candidates.isEmpty()) {
            outputHandler.appendError("Unable to find global-metadata.dat")
            return
        }

        candidates.forEachIndexed { index, candidate ->
            val name = if (index == 0) "global-metadata.dat" else "global-metadata-${index + 1}.dat"
            outputHandler.appendInfo(
                "Dumping $name from ${candidate.region.getStartAddress().toHex()} (${candidate.region.getSize().Bytes_to_MB}MB)..."
            )
            val metadataFile = File(outputDir, name)
            // Header repair is handled by fixMetadataHeader(); the ELF
            // fixer must not touch the metadata file (fix = false).
            dump(candidate.region, outputFile = metadataFile, outLog = outputHandler, fix = false)
            fixMetadataHeader(metadataFile)
            verifyDumpedMetadata(metadataFile, name)
        }
    }

    /**
     * Check the dumped metadata file and log an honest verdict.
     *
     * A file that fails verification is most likely the game's encrypted
     * metadata asset (protected titles keep the encrypted asset mapped
     * while the decrypted copy only exists under protection-specific
     * conditions).
     */
    private fun verifyDumpedMetadata(file: File, name: String) {
        runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val length = raf.length()
                if (length < MetadataVerifier.HEADER_SIZE) {
                    outputHandler.appendWarning("$name is only $length bytes - not a usable metadata file")
                    return
                }
                val header = ByteArray(MetadataVerifier.HEADER_SIZE)
                raf.readFully(header)
                val result = MetadataVerifier.check(header, length)
                if (result.valid) {
                    outputHandler.appendSuccess("$name verified (metadata version ${result.version}, ${length.Bytes_to_MB}MB)")
                } else {
                    outputHandler.appendWarning("$name header check failed: ${result.reason}")
                    outputHandler.appendWarning(
                        "$name may be encrypted by the game and might not open in Il2CppDumper. " +
                                "Try dumping right after the loading screen, or use a hook-based dumper for protected games."
                    )
                }
            }
        }.onFailure {
            outputHandler.appendWarning("Metadata verification failed: ${it.message}")
        }
    }

    /**
     * Fix the dumped metadata file.
     *
     * A metadata dump may not start with the expected magic (e.g. when the
     * region was located by scanning instead of by file path). Repair the
     * header in place; a failure here must never fail an otherwise
     * successful dump.
     */
    private fun fixMetadataHeader(metadataFile: File) {
        runCatching {
            val fixer = MetadataFixer()
            if (fixer.isNeedFixer(metadataFile)) {
                outputHandler.appendInfo("Fixing global-metadata.dat header...")
                if (fixer.fixHeader(metadataFile, checkFile = false)) {
                    outputHandler.appendSuccess("global-metadata.dat header fixed")
                } else {
                    outputHandler.appendWarning("Failed to fix global-metadata.dat header")
                }
            }
        }.onFailure {
            outputHandler.appendWarning("Metadata header check failed: ${it.message}")
        }
    }

    /**
     * Output file label for a region: the mapped file name for file-backed
     * regions, "anon-<perms>" for anonymous ones.
     */
    private fun regionLabel(mem: MapLineParser): String {
        return mem.getPath().FileName.ifEmpty { "anon-${mem.getPerms()}" }
    }

    /**
     * Abort early when the output volume cannot possibly hold the dump.
     */
    private fun checkFreeSpace(outputDir: File, required: Long) {
        val available = StatFs(outputDir.path).availableBytes
        if (available < required) {
            throw DumperException(
                "Not enough free space: need ${required.Bytes_to_MB}MB, available ${available.Bytes_to_MB}MB"
            )
        }
    }

    /**
     * Dump the memory to a file.
     *
     * @param mem The memory map line parser.
     * @param fixerPath The path to the fixer.
     * @param outputFile The output file.
     * @param outLog The output handler.
     * @param fix Whether to run the ELF fixer after dumping (metadata dumps
     *        pass false; everything else follows [DumperConfig.autoFix]).
     */
    private fun dump(
        mem: MapLineParser,
        fixerPath: String? = null,
        outputFile: File,
        outLog: OutputHandler,
        fix: Boolean = config.autoFix
    ) {
        RandomAccessFile("/proc/$pid/mem", "r").use { memFile ->
            val channel = memFile.channel
            channel.copyToFile(mem.getStartAddress(), mem.getSize(), outputFile)
            if (fix) {
                fixDump(channel, mem, outputFile, fixerPath, outLog)
            }
        }
    }

    /**
     * Fix the dumped memory file.
     *
     * @param channel The file channel.
     * @param mem The memory map line parser.
     * @param outputFile The output file.
     * @param fixerPath The path to the fixer.
     * @param outLog The output handler.
     */
    private fun fixDump(channel: FileChannel, mem: MapLineParser, outputFile: File, fixerPath: String?, outLog: OutputHandler) {
        if (fixerPath == null) {
            outLog.appendError("Fixer path is null, skipping...")
            return
        }

        val archELF = getArchELF(channel, mem)
        Fixer(fixerPath).fixELFFile(mem.getStartAddress(), archELF, outputFile, outLog)
    }

    /**
     * Parse the memory map.
     *
     * @return The parsed memory map line parser.
     * @throws FileNotFoundException If the /proc/{pid}/maps file is not found.
     * @throws RuntimeException If the start or end address is not found.
     */
    private fun parseMap(): MapLineParser {
        if (pid == null) {
            throw DumperException("Please made sure you already run your process!")
        }

        val files = File("/proc/$pid/maps")
        if (!files.exists()) {
            throw DumperException("Failed to open : ${files.path}")
        }

        // Custom range ("7f2b1c001000-7f2b1c0b7000") dumps that exact range
        // instead of a named library - this is the only way to grab
        // anonymous regions.
        MapLineParser.parseCustomRangeOrNull(config.file)?.let { (start, end) ->
            return parseCustomMap(files, start, end)
        }

        val parsedMaps = files.readLines()
            .filter { it.contains(config.file) }
            .map { MapLineParser(it) }

        val map = findCorrectFiles(parsedMaps = parsedMaps)
        if (map == null || !map.isValid())
            throw DumperException("start or end address not found!")

        return map
    }

    /**
     * Build the region for a custom address range.
     *
     * The start address must fall inside a readable mapping - that validates
     * the input and gives the dump file a meaningful name. A range spanning
     * several mappings is fine: /proc/pid/mem reads any mapped page.
     */
    private fun parseCustomMap(mapsFile: File, start: Long, end: Long): MapLineParser {
        val containing = mapsFile.readLines()
            .mapNotNull { runCatching { MapLineParser(it) }.getOrNull() }
            .firstOrNull {
                it.getStartAddress() <= start && start < it.getEndAddress() && it.isReadable()
            } ?: throw DumperException("Address ${start.toHex()} is not mapped (or not readable) in the target process!")

        outputHandler.appendInfo("Custom range ${start.toHex()}-${end.toHex()} (${(end - start).Bytes_to_MB}MB)")
        return MapLineParser(start, end, containing.getPath())
    }

    /**
     * Find the correct files in the parsed memory maps.
     *
     * @param startIndex The start index.
     * @param parsedMaps The list of parsed memory maps.
     * @return The correct memory map line parser.
     */
    private fun findCorrectFiles(startIndex: Int = 0, parsedMaps : List<MapLineParser>) : MapLineParser? {
        // Open /proc/pid/mem once and reuse it for every isELF() probe;
        // the old code re-opened the file per candidate line.
        RandomAccessFile("/proc/$pid/mem", "r").use { memFile ->
            val memChannel = memFile.channel
            var map: MapLineParser? = null
            for (i in startIndex until parsedMaps.size) {
                val mapLineParser = parsedMaps[i]
                if (map == null) {
                    val path = mapLineParser.getPath()
                    if (!path.contains(".so")) {
                        map = mapLineParser // For all other files
                    } else if (isELF(memChannel, mapLineParser.getStartAddress())) {
                        map = mapLineParser // Must be valid .so ELF files
                    }
                } else {
                    if (map.getInode() == mapLineParser.getInode()) {
                        map.setEndAddress(mapLineParser.getEndAddress())

                        if (map.getSize() > config.limitMaxDumpSize) {
                            return findCorrectFiles(startIndex + 1, parsedMaps)
                        }
                    }
                }
            }
            return map
        }
    }
}
