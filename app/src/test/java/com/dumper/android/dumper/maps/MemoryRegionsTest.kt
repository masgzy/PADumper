package com.dumper.android.dumper.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRegionsTest {

    private val limit = 1000L * 1024 * 1024 // 1000MB, same as the app default

    private fun line(
        start: String,
        end: String,
        perms: String = "r--p",
        inode: String = "456789",
        path: String = "/libtest/libil2cpp.so"
    ): String = "$start-$end $perms 00000000 253:2 $inode $path"

    @Test
    fun `merges contiguous same-inode segments of one library`() {
        // A .so is mapped as r-- / r-x / rw- segments; all three belong to
        // one file and must produce a single dump.
        val maps = listOf(
            line("7f2b1c001000", "7f2b1c005000", perms = "r--p"),
            line("7f2b1c005000", "7f2b1c009000", perms = "r-xp"),
            line("7f2b1c009000", "7f2b1c00a000", perms = "rw-p")
        )

        val regions = MemoryRegions.collect(maps, limit)

        assertEquals(1, regions.size)
        assertEquals(0x7f2b1c001000L, regions[0].getStartAddress())
        assertEquals(0x7f2b1c00a000L, regions[0].getEndAddress())
    }

    @Test
    fun `does not merge different libraries`() {
        val maps = listOf(
            line("7f2b1c001000", "7f2b1c005000", path = "/libtest/libfoo.so"),
            line("7f2b1c005000", "7f2b1c009000", path = "/libtest/libbar.so")
        )

        val regions = MemoryRegions.collect(maps, limit)

        assertEquals(2, regions.size)
    }

    @Test
    fun `does not merge libraries with a gap between them`() {
        val maps = listOf(
            line("7f2b1c001000", "7f2b1c005000", path = "/libtest/libfoo.so"),
            line("7f2b1c006000", "7f2b1c009000", path = "/libtest/libfoo.so")
        )

        val regions = MemoryRegions.collect(maps, limit)

        assertEquals(2, regions.size)
    }

    @Test
    fun `merges contiguous anonymous regions with the same perms`() {
        val anon = { s: String, e: String -> line(s, e, perms = "rw-p", inode = "0", path = "") }
        val maps = listOf(anon("7f2b1c001000", "7f2b1c005000"), anon("7f2b1c005000", "7f2b1c00b000"))

        val regions = MemoryRegions.collect(maps, limit)

        assertEquals(1, regions.size)
        assertEquals(0xa000L, regions[0].getSize())
    }

    @Test
    fun `does not merge adjacent anonymous regions with different perms`() {
        val maps = listOf(
            line("7f2b1c001000", "7f2b1c005000", perms = "rw-p", inode = "0", path = ""),
            line("7f2b1c005000", "7f2b1c00b000", perms = "r--p", inode = "0", path = "")
        )

        val regions = MemoryRegions.collect(maps, limit)

        assertEquals(2, regions.size)
    }

    @Test
    fun `skips unreadable and pseudo regions`() {
        val maps = listOf(
            line("7f2b1c001000", "7f2b1c005000", perms = "--xp"),                       // not readable
            line("7f2b1c005000", "7f2b1c006000", inode = "0", path = "[vvar]"),         // kernel pseudo
            line("7f2b1c006000", "7f2b1c007000", inode = "0", path = "[vdso]"),
            line("7f2b1c007000", "7f2b1c00b000", inode = "0", path = "/dev/mali0"),     // device memory
            line("7f2b1c00b000", "7f2b1c00f000")                                        // readable, kept
        )

        val regions = MemoryRegions.collect(maps, limit)

        assertEquals(1, regions.size)
        assertEquals(0x7f2b1c00b000L, regions[0].getStartAddress())
    }

    @Test
    fun `skips regions over the size limit`() {
        val skipped = mutableListOf<String>()
        val maps = listOf(
            line("7f2b1c001000", "7f3b1c001000"),                                        // 4GB - over the limit
            line("7f3b1c001000", "7f3b1c002000", inode = "456790", path = "/libtest/libbar.so") // 4KB - kept
        )

        val regions = MemoryRegions.collect(maps, limitMaxSize = 8192, onSkip = { skipped.add(it) })

        assertEquals(1, regions.size)          // only the small one survives
        assertEquals(1, skipped.size)          // the big one is reported
        assertTrue(skipped[0].contains("exceeds limit"))
    }

    @Test
    fun `unsorted input is sorted before merging`() {
        val maps = listOf(
            line("7f2b1c005000", "7f2b1c009000", perms = "r-xp"),
            line("7f2b1c001000", "7f2b1c005000", perms = "r--p")
        )

        val regions = MemoryRegions.collect(maps, limit)

        assertEquals(1, regions.size)
        assertEquals(0x7f2b1c001000L, regions[0].getStartAddress())
    }

    @Test
    fun `empty maps produce no regions`() {
        assertTrue(MemoryRegions.collect(emptyList(), limit).isEmpty())
    }
}
