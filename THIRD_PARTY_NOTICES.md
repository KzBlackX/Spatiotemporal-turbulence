# 第三方材料与依赖声明

本项目自身代码以 GNU Lesser General Public License v3.0 or later 发布，见 [LICENSE](LICENSE)。
以下第三方材料各有其自身许可，不受本项目许可证覆盖。

## 1. NeoForge MDK / mod-generator 模板（MIT）

本仓库的构建脚手架派生自 NeoForged 官方模板
（<https://github.com/neoforged/MDK>、<https://github.com/neoforged/mod-generator>），
原始许可为 MIT：

```
MIT License

Copyright (c) 2023 NeoForged project

This license applies to the template files as supplied by github.com/NeoForged/MDK

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

受影响的文件包括但不限于 `build.gradle`、`settings.gradle`、`gradle.properties`、
`gradle/wrapper/*`、`gradlew`、`gradlew.bat`、`src/main/templates/META-INF/neoforge.mods.toml`
以及 `src/main/java` 下的初始脚手架类。MIT 允许在其派生作品上适用不同许可，
因此这些文件在本仓库中随项目一并按 LGPL-3.0-or-later 分发，同时保留上述版权与许可声明。

## 2. NeoForge（LGPL-2.1）

本模组在编译期和运行期依赖 NeoForge，其许可为 LGPL-2.1。
NeoForge 不随本仓库分发，由构建系统从官方 Maven 仓库解析下载。
仅作为依赖使用不要求本模组采用相同许可；但若把 NeoForge 源码复制进本项目，
该部分代码仍受 LGPL-2.1 约束。

## 3. Minecraft（Mojang EULA）

本项目不包含、不分发任何 Minecraft 的代码或资源。使用与分发本模组须同时遵守
Minecraft 最终用户许可协议（<https://www.minecraft.net/eula>）。本项目许可证不授予
任何 Minecraft 相关的额外权利。

## 4. Gradle Wrapper（Apache-2.0）

`gradle/wrapper/gradle-wrapper.jar` 与 `gradlew`、`gradlew.bat` 来自 Gradle 项目，
许可为 Apache License 2.0（<https://github.com/gradle/gradle/blob/master/LICENSE>）。
