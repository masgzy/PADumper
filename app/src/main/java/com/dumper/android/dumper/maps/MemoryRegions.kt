package com.dumper.android.dumper.maps

import com.dumper.android.utils.Bytes_to_MB
import com.dumper.android.utils.toHex

/**
 * Turns raw /proc/[pid]/maps lines into the list of regions to dump in
 * "dump all memory" mode.
 *
 * Pure JVM logic (no Android imports) so it is unit-testable.
 */
object MemoryRegions {

    // Kernel pseudo-regions: [vsyscall] is never readable and vvar/vdso have
    // no value in a dump. /dev/* mappings are device memory - reading them
    // can fail or hang, and they are never useful in a dump.
    private val SKIP_PATHS = setOf("[vvar]", "[vdso]", "[vsyscall]", "[vvar_vdso]")

    /**
     * @param lines raw lines of /proc/[pid]/maps
     * @param limitMaxSize regions bigger than this are skipped
     * @param onSkip called with a human-readable reason for every skipped
     *        region
     * @return merged, readable regions sorted by start address
     */
    fun collect(
        lines: List<String>,
        limitMaxSize: Long,
        onSkip: (String) -> Unit = {}
    ): List<MapLineParser> {
        val readable = lines.asSequence()
            .map { runCatching { MapLineParser(it) }.getOrNull() }
            .filterNotNull()
            .filter { it.isValid() && it.isReadable() }
            .filter { it.getPath() !in SKIP_PATHS && !it.getPath().startsWith("/dev/") }
            .sortedBy { it.getStartAddress() }

        val merged = mutableListOf<MapLineParser>()
        for (region in readable) {
            val last = merged.lastOrNull()
            if (last != null && canMerge(last, region)) {
                last.setEndAddress(region.getEndAddress())
            } else {
                merged.add(region)
            }
        }

        return merged.filter { region ->
            if (region.getSize() > limitMaxSize) {
                onSkip(
                    "Skipping ${region.getStartAddress().toHex()}-${region.getEndAddress().toHex()} " +
                            "${region.getPath().ifEmpty { "anon" }}: size ${region.getSize().Bytes_to_MB}MB exceeds limit"
                )
                false
            } else {
                true
            }
        }
    }

    /**
     * Two adjacent mappings merge into one dump file when they belong to the
     * same allocation:
     * - real files: same path + same inode (a .so is mapped as r--/r-x/rw-
     *   segments; merging them reconstructs the whole file, same rule the
     *   single-library dumper uses);
     * - anonymous / kernel-named ([heap], [anon:...], [stack]) regions: same
     *   path (possibly empty) + same permissions.
     */
    private fun canMerge(a: MapLineParser, b: MapLineParser): Boolean {
        if (a.getEndAddress() != b.getStartAddress()) return false
        if (a.getPath() != b.getPath()) return false
        if (a.getInode() != b.getInode()) return false

        val isRealFile = a.getPath().isNotEmpty() && !a.getPath().startsWith("[")
        return isRealFile || a.getPerms() == b.getPerms()
    }
}
