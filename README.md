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
- [x] 支持自定义地址范围转储（含匿名内存区域）
- [x] 支持一键转储进程全部可读内存区域
- [x] metadata 候选验证：自动识别被游戏加密的 global-metadata.dat（自定义保护容器），优先转储内存中已解密的副本（含匿名内存），并输出每个转储文件的校验结论

## 使用方法

1. 运行目标游戏
2. 打开 [PADumper](https://github.com/masgzy/PADumper/releases)
3. 手动输入进程名，或点击 `选择应用` 选择正在运行的应用
4. 输入 ELF 文件名，留空则使用默认值 `libil2cpp.so`
   - **自定义地址范围**：填入十六进制区间 `起始地址-结束地址`（如 `7f2b1c001000-7f2b1c0b7000`），可直接转储任意内存区域，包括匿名区域；起始地址必须落在目标进程某个可读映射内
5. 【可选】如需修复 ELF，请勾选 `修复 ELF`
6. 【可选】如需从内存转储 Unity 元数据，请勾选 `转储 global-metadata.dat`
7. 【可选】如需转储进程全部内存，请勾选 `转储全部内存`（自动跳过不可读区域、合并相邻同名映射；单个区域超过上限或转储失败会跳过并记录日志，不影响其余区域）
8. 点击开始转储并等待完成
9. 输出路径：
   - Root 模式：`/sdcard/PADumper/[进程名]/[起始地址-结束地址-文件名]`
   - 非 Root 模式：`{HOME}/Download/PADumper/[进程名]/[起始地址-结束地址-文件名]`

## 致谢

- [**libsu**](https://github.com/topjohnwu/libsu)
- [**SoFixer**](https://github.com/F8LEFT/SoFixer)
- 上游项目：[**BryanGIG/PADumper**](https://github.com/BryanGIG/PADumper)

## 许可证

本项目基于 [GPL-3.0](LICENSE) 许可证开源。
