# GN Book 格式说明（.gnbook）

主阅读格式：**合法 EPUB3 包 + 扩展批注**。

- **其它阅读器**：把 `.gnbook` 改名为 `.epub`（或直接打开同目录导出的 `.epub` 副本）即可读正文；正文 XHTML **不含段评控件**。
- **gn-reader**：识别包内 `gnbook.json`，按设置决定是否显示段评/章评。

由 `lofter-getter` / get_norvels 导出（`export_reader_package` / `make_reader.py`）。

## 包结构

```
mimetype                 # application/epub+zip（STORE，其它阅读器可识别）
META-INF/container.xml   # 首选 rootfile = OEBPS/content.opf；另挂 gnbook.json
gnbook.json              # 扩展清单（元数据 + spine + 段评/章评）——仅本阅读器使用
OEBPS/content.opf
OEBPS/nav.xhtml
OEBPS/style/reader.css
OEBPS/chapters/*.xhtml   # 干净正文（段落带 id，不含段评 UI）
OEBPS/media/*            # 本地图片等
```

## 显示职责分离

| 层 | 职责 |
|----|------|
| **gnbook.json** | 只描述「哪些段有评、评论内容是什么」 |
| **阅读器设置** | 是否显示段评徽章 / 章评入口（`showParagraphComments` / `showChapterComments`） |

格式不强制 UI；关掉开关后正文与其它 EPUB 阅读体验一致。

## gnbook.json 要点

- `format`: `"gnbook"`，`version`: `1`
- `spine[]`: `{ id, title, kind, href }`
- `chapters[id].paragraphs[]`: `{ id, text, html, empty, hasImage }`
- `chapters[id].paragraphComments[pid]`: `{ count, comments[] }`
- `chapters[id].chapterComments`: `{ hot[], general[] }`

## 与 getter 协作

- 导出同时生成 `.gnbook` 与同内容 `.epub` 副本（进 `result/`）；二者均为混合包（含 `gnbook.json`）
- **gn-reader 打开 `.epub`**：若 ZIP 内有 `gnbook.json`，按 gn 格式解析并显示段评；否则当普通 EPUB
- 段评挂载：导出时把 Lofter pid 对齐到正文段落 id（HTML `id` → `p_i{N}` 索引 → quote 兜底）
- 已有 EPUB → gnbook：`convert_epub_to_gnbook.py`，或 App 文件页「转gnbook」
- 同目录 `reader_bundle.json` 仍可被转换逻辑优先合并段评

## 格式变更时请同步

- `lofter-getter/utils/gnbook.py`
- `mobile/app/src/main/python/utils/gnbook.py`
- `gn-reader` 解析器（`GnbookParser` / `EpubParser`）
