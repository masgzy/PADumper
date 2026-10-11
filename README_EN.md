# PADumper (Process Android Dumper)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

English | [简体中文](README.md)

Android process memory dumper, especially for the il2cpp game at runtime.

## Feature

- [x] Support Fix ELF dump
- [x] Support global-metadata.dat searching
- [x] Support detects ELF arch
- [x] Support root or non-root with virtual space
- [x] In-app language switching (English / 简体中文)
- [x] Custom address range dumping (including anonymous memory regions)
- [x] One-click dump of every readable memory region of the process
- [x] Metadata candidate verification: detects game-encrypted global-metadata.dat (custom protection containers), prefers the decrypted copy in memory (including anonymous buffers), and logs a verification verdict for every dumped file

## How To Use

- Run Game
- Open [PADumper](https://github.com/masgzy/PADumper/releases)
- Put the process name manually or you can click `Select Apps` to select running apps
- Put the ELF Name or you can leave it with the default name `libil2cpp.so`
  - **Custom address range**: type a hex range `startAddress-endAddress` (e.g. `7f2b1c001000-7f2b1c0b7000`) to dump any region, including anonymous ones; the start address must fall inside a readable mapping of the target process
- [**Optional**] Check `Fix ELF` if you want to fix the ELF
- [**Optional**] Check `global-metadata.dat` if you want to dump unity metadata from memory
- [**Optional**] Check `Dump all memory` to dump every readable region (unreadable regions are skipped automatically, adjacent mappings of the same allocation are merged; regions over the size limit or failing to dump are logged and skipped without affecting the rest)
- Dump and wait until the dumping finish
- Output:
  - Root Method: `/sdcard/PADumper/[Process]/[startAddress-endAddress-file]`
  - Non Root Method: `{HOME}/Download/PADumper/[Process]/[startAddress-endAddress-file]`

## Credits

- [**libsu**](https://github.com/topjohnwu/libsu)
- [**SoFixer**](https://github.com/F8LEFT/SoFixer)
- Upstream project: [**BryanGIG/PADumper**](https://github.com/BryanGIG/PADumper)

## License

This project is licensed under the [GPL-3.0](LICENSE).
