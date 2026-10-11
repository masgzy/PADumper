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

## How To Use

- Run Game
- Open [PADumper](https://github.com/masgzy/PADumper/releases)
- Put the process name manually or you can click `Select Apps` to select running apps
- Put the ELF Name or you can leave it with the default name `libil2cpp.so`
- [**Optional**] Check `Fix ELF` if you want to fix the ELF
- [**Optional**] Check `global-metadata.dat` if you want to dump unity metadata from memory
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
