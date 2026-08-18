# GN Reader

独立 Android 阅读器，对标 Moon+ Reader 的文件浏览 / 多格式打开 / 文本编码 / 朗读，并原生支持 **`.gnbook`**（段评 + 章评）。

## 功能

- **文件浏览器**：本地目录、排序、新建/重命名/删除；WebDAV
- **`.gnbook` / EPUB**：分页（原生）与滚动、主题/字号/字体、目录、段评、TTS
- **实体键映射**：音量键翻页；可检测并绑定手机全部可达实体键（侧键等）
- **触控屏蔽**：短时连按实体键开启/解除，防口袋误触
- **文本**：多编码、可编辑保存；Office/MOBI 抽取预览
- **其它**：PDF（PdfRenderer）

## 构建

需要 JDK 17+、Android SDK（compileSdk 34）。

```bash
cd gn-reader   # 或本仓库根目录
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleRelease
# APK: app/build/outputs/apk/release/app-release-unsigned.apk
# 若未配置签名，AGP 可能产出 app-release.apk（debug 密钥签名，仅供测试）
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

当前版本见 `app/build.gradle.kts` 中 `versionName`。

## 文档

| 文档 | 说明 |
|------|------|
| [docs/GNBOOK_FORMAT.md](docs/GNBOOK_FORMAT.md) | gnbook 格式 |
| [docs/NATIVE_PAGER.md](docs/NATIVE_PAGER.md) | 原生分页 |
| [docs/HARDWARE_KEYS.md](docs/HARDWARE_KEYS.md) | 实体键与触控屏蔽 |
| [COMPAT.md](COMPAT.md) | 兼容说明 |

## 实体键速览

默认 **音量+** 上一页、**音量-** 下一页；**音量- 连按 3 次** 切换触控屏蔽。  
详情见 [docs/HARDWARE_KEYS.md](docs/HARDWARE_KEYS.md)。

## 许可

见仓库 LICENSE（若尚未添加，默认与上游工具集一致；独立公开库建议标明 MIT/Apache-2.0）。
