package com.dumper.android.dumper.maps

import com.dumper.android.dumper.exception.DumperException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLineParserTest {

    private val libLine =
        "7f2b1c001000-7f2b1c007000 r--p 00000000 253:2 456789 /data/app/~~abc==/com.example-1/oat/arm64/base.odex"

    @Test
    fun `parses all fields of a maps line`() {
        val parser = MapLineParser(libLine)

        assertEquals(0x7f2b1c001000L, parser.getStartAddress())
        assertEquals(0x7f2b1c007000L, parser.getEndAddress())
        assertEquals("r--p", parser.getPerms())
        assertEquals(0x0L, parser.getOffset())
        assertEquals("253:2", parser.getDev())
        assertEquals(456789, parser.getInode())
        assertEquals("/data/app/~~abc==/com.example-1/oat/arm64/base.odex", parser.getPath())
    }

    @Test
    fun `size is end minus start`() {
        assertEquals(0x6000L, MapLineParser(libLine).getSize())
    }

    @Test
    fun `collapses runs of whitespace`() {
        val parser = MapLineParser(
            "7c1a2b3000-7c1a2b9000 r-xp    00001000   08:02      123   /libtest/libil2cpp.so"
        )

        assertEquals("r-xp", parser.getPerms())
        assertEquals(0x1000L, parser.getOffset())
        assertEquals("/libtest/libil2cpp.so", parser.getPath())
    }

    @Test
    fun `valid line needs both addresses`() {
        assertTrue(MapLineParser(libLine).isValid())

        // start = 0 with a non-zero end used to pass (||) - it must not
        val zeroStart = MapLineParser("00000000-00001000 r--p 00000000 00:00 0 /libtest/libtest.so")
        assertFalse(zeroStart.isValid())
    }

    @Test
    fun `anonymous region has empty path`() {
        val parser = MapLineParser("7f00000000-7f00001000 rw-p 00000000 00:00 0 ")
        assertEquals("", parser.getPath())
    }

    @Test
    fun `malformed address throws`() {
        // A line with no address separator at all cannot be parsed.
        // (Lines WITH a dash but garbage addresses fail later in
        // toLong(16) with a NumberFormatException, which dumpFile()
        // also catches and reports.)
        assertThrows(DumperException::class.java) { MapLineParser("notamapline") }
    }

    @Test
    fun `setEndAddress extends the region`() {
        val parser = MapLineParser(libLine)
        parser.setEndAddress(0x7f2b1c00a000L)

        assertEquals(0x7f2b1c00a000L, parser.getEndAddress())
        assertEquals(0x9000L, parser.getSize())
    }

    @Test
    fun `readable region is detected`() {
        assertTrue(MapLineParser(libLine).isReadable())

        val writeOnly = MapLineParser("7f00000000-7f00001000 -w-p 00000000 00:00 0 /libtest/libtest.so")
        assertFalse(writeOnly.isReadable())
    }

    @Test
    fun `synthetic region from addresses`() {
        val parser = MapLineParser(0x7f2b1c001000L, 0x7f2b1c007000L, "/libtest/libil2cpp.so")

        assertEquals(0x7f2b1c001000L, parser.getStartAddress())
        assertEquals(0x7f2b1c007000L, parser.getEndAddress())
        assertEquals("/libtest/libil2cpp.so", parser.getPath())
        assertTrue(parser.isValid())
    }

    @Test
    fun `custom range parses plain hex`() {
        val range = MapLineParser.parseCustomRangeOrNull("7f2b1c001000-7f2b1c0b7000")
        assertEquals(0x7f2b1c001000L to 0x7f2b1c0b7000L, range)
    }

    @Test
    fun `custom range accepts 0x prefix and uppercase`() {
        val range = MapLineParser.parseCustomRangeOrNull("0x7F2B1C001000-0X7f2b1c0b7000")
        assertEquals(0x7f2b1c001000L to 0x7f2b1c0b7000L, range)
    }

    @Test
    fun `non-range text returns null`() {
        assertNull(MapLineParser.parseCustomRangeOrNull("libil2cpp.so"))
        assertNull(MapLineParser.parseCustomRangeOrNull("lib-il2cpp.so"))
        assertNull(MapLineParser.parseCustomRangeOrNull("7f2b1c001000"))
        assertNull(MapLineParser.parseCustomRangeOrNull(""))
    }

    @Test
    fun `invalid range throws`() {
        // end <= start
        assertThrows(DumperException::class.java) {
            MapLineParser.parseCustomRangeOrNull("7f2b1c0b7000-7f2b1c001000")
        }
        assertThrows(DumperException::class.java) {
            MapLineParser.parseCustomRangeOrNull("7f2b1c001000-7f2b1c001000")
        }
        // > 16 hex digits overflows a Long
        assertThrows(DumperException::class.java) {
            MapLineParser.parseCustomRangeOrNull("1ffffffffffffffff-2ffffffffffffffff")
        }
    }
}
