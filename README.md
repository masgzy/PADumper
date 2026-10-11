# PADumper (Process Android Dumper)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

English | [简体中文](README.md)

Android 进程内存转储工具，主要用于在运行时转储 il2cpp 游戏。

## 功能特性

- [x] 支持 ELF 转储修复（Fix ELF）
- [x] 支持搜索 global-metadata.dat
- [x] 支持自动检测 ELF 架构
- [x] 支持 Root 与非 Root（虚拟空间）两种模式
- [x] 支持中文 / English 应用内切换

## 使用方法

1. 运行目标游戏
2. 打开 [PADumper](https://github.com/masgzy/PADumper/releases)
3. 手动输入进程名，或点击 `选择应用` 选择正在运行的应用
4. 输入 ELF 文件名，留空则使用默认值 `libil2cpp.so`
5. 【可选】如需修复 ELF，请勾选 `修复 ELF`
6. 【可选】如需从内存转储 Unity 元数据，请勾选 `转储 global-metadata.dat`
7. 点击开始转储并等待完成
8. 输出路径：
   - Root 模式：`/sdcard/PADumper/[进程名]/[起始地址-结束地址-文件名]`
   - 非 Root 模式：`{HOME}/Download/PADumper/[进程名]/[起始地址-结束地址-文件名]`

## 致谢

- [**libsu**](https://github.com/topjohnwu/libsu)
- [**SoFixer**](https://github.com/F8LEFT/SoFixer)
- 上游项目：[**BryanGIG/PADumper**](https://github.com/BryanGIG/PADumper)

## 许可证

本项目基于 [GPL-3.0](LICENSE) 许可证开源。
