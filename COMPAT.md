# GN Reader

独立公开阅读器工程，目录：`gn-reader/`。

- 主格式：`.gnbook`（见 `gn-reader/docs/GNBOOK_FORMAT.md`）
- getter 导出：`lofter-getter/utils/gnbook.py`，由 `export_reader_package` 优先写出
- 默认书库：`Documents/get_norvels`（与手机抓取 App 对齐）

构建与云端 APK：见 `gn-reader/README.md` 与 `.github/workflows/android.yml`。
