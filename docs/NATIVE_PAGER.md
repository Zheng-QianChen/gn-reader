# Native pagination (native-v3)

## Goal
E-ink-friendly page mode that does **not** rely on WebView `translateY` clipping.

## Split
| Layer | Role |
|-------|------|
| `data/reader/PageModels.kt` | UI-agnostic page model (`ContentBlock` → `PageContent`) |
| `data/reader/AndroidPageLayoutEngine.kt` | Reference engine: measure lines + pack by line count |
| `ui/reader/NativePagePager.kt` | Compose display + tap zones (no animation) |
| WebView `pager-v5` | Still used for **scroll** mode only |

## native-v3 fixes
- Each `PageLine` is drawn with `maxLines=1` / `softWrap=false` so Compose cannot reflow and create orphan characters
- First-line indent is two ideographic spaces baked into the string (same for measure + paint)
- Measure width uses a safety margin + optional badge gutter
- Chrome / bottom bar backgrounds are fully opaque so text does not show through

## native-v2 (kept)
- Pack pages by line count; do not soft-break at commas; merge empty Lofter `<p>` gaps

## Later (cross e-ink / C++)
Replace `AndroidPageLayoutEngine` with a C++/CRE implementation of `PageLayoutEngine`; keep the same `PageContent` contract so Compose / other shells stay thin.

## User-facing
- Default `pageMode` = `page` (native)
- Settings shows `引擎 native-v3`
- Bottom page label shows `· v3`
- Left / right tap flips page; center toggles chrome or TTS pause
