package com.dumper.android.dumper.maps

import com.dumper.android.dumper.exception.DumperException
import com.dumper.android.utils.toHex

class MapLineParser private constructor(private val memoryRegion: MemoryRegion) {

    /**
     * Parse a line of /proc/[pid]/maps.
     */
    constructor(line: String) : this(parseLine(line))

    /**
     * Synthetic region built from an explicit address range (custom dump).
     *
     * It has no offset/dev/inode because it does not come from a maps line;
     * the path (if known) is only used for the output file name.
     */
    constructor(startAddress: Long, endAddress: Long, path: String = "", perms: String = "rw-p") :
            this(MemoryRegion("").apply {
                this.startAddress = startAddress
                this.endAddress = endAddress
                this.path = path
                this.perms = perms
            })

    fun getStartAddress(): Long {
        return memoryRegion.startAddress
    }

    fun getEndAddress(): Long {
        return memoryRegion.endAddress
    }

    fun setEndAddress(endAddress: Long) {
        memoryRegion.endAddress = endAddress
    }

    fun getPerms(): String {
        return memoryRegion.perms
    }

    fun getOffset(): Long {
        return memoryRegion.offset
    }

    fun getDev(): String {
        return memoryRegion.dev
    }

    fun getInode(): Int {
        return memoryRegion.inode.toInt()
    }

    fun getPath(): String {
        return memoryRegion.path
    }

    fun getSize(): Long {
        return getEndAddress() - getStartAddress()
    }

    fun isValid(): Boolean {
        // A usable region needs BOTH addresses; a line with only one of them
        // set is corrupt (the old || accepted start=0 garbage).
        return getStartAddress() > 0L && getEndAddress() > 0L
    }

    fun isReadable(): Boolean {
        return getPerms().startsWith("r")
    }

    override fun toString(): String {
        return "startAddress=${getStartAddress().toHex()} || endAddress=${getEndAddress().toHex()} || size=${getSize()}|| path=${getPath()}"
    }

    companion object {
        // "7f2b1c001000-7f2b1c0b7000" - a leading 0x and uppercase digits are
        // both accepted.
        private val CUSTOM_RANGE = Regex("^(?:0[xX])?([0-9a-fA-F]+)-(?:0[xX])?([0-9a-fA-F]+)$")

        /**
         * Parse a user-supplied address range expression, e.g.
         * "7f2b1c001000-7f2b1c0b7000".
         *
         * @return start/end pair, or null when [text] is not a range
         *         expression at all (the caller then treats it as a library
         *         name).
         * @throws DumperException when [text] looks like a range but is not a
         *         valid one (address overflow, end <= start).
         */
        fun parseCustomRangeOrNull(text: String): Pair<Long, Long>? {
            val match = CUSTOM_RANGE.matchEntire(text.trim()) ?: return null
            val start = match.groupValues[1].toLongOrNull(16)
                ?: throw DumperException("Invalid address in range: ${match.groupValues[1]}")
            val end = match.groupValues[2].toLongOrNull(16)
                ?: throw DumperException("Invalid address in range: ${match.groupValues[2]}")
            if (end <= start)
                throw DumperException("Invalid range: end ${end.toHex()} must be greater than start ${start.toHex()}")
            return start to end
        }

        private fun parseLine(line: String): MemoryRegion {
            val memoryRegion = MemoryRegion(line)
            val strp = line.replace("\\s+".toRegex(), " ").split(" ")
            strp.forEachIndexed { index, s ->
                when (index) {
                    0 -> {
                        val lineAddrs = s.split("-")
                        if (lineAddrs.size < 2)
                            throw DumperException("Invalid maps line parsing...")

                        memoryRegion.startAddress = lineAddrs[0].toLong(16)
                        memoryRegion.endAddress = lineAddrs[1].toLong(16)
                    }
                    1 -> memoryRegion.perms = s
                    2 -> memoryRegion.offset = s.toLong(16)
                    3 -> memoryRegion.dev = s
                    4 -> memoryRegion.inode = s.toLong()
                    5 -> memoryRegion.path = s
                }
            }
            return memoryRegion
        }
    }
}
