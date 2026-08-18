package com.gnreader.app.data.reader

/**
 * 与 UI 解耦的分页数据模型。
 *
 * 后续可把 [PageLayoutEngine] 换成 C++/CRE 实现，Compose / 墨水屏壳只消费 [PageContent]。
 */
enum class BlockKind {
    TITLE,
    META,
    BODY,
    IMAGE_PLACEHOLDER,
    EMPTY,
}

data class ContentBlock(
    val id: String?,
    val text: String,
    val kind: BlockKind,
    val commentCount: Int = 0,
    /** 首行缩进（em，相对正文字号） */
    val firstLineIndentEm: Float = 0f,
)

data class PageLine(
    val blockId: String?,
    val text: String,
    val kind: BlockKind,
    val showBadge: Boolean = false,
    val badgeCount: Int = 0,
)

data class PageContent(
    val lines: List<PageLine>,
    val blockIds: Set<String>,
)

data class PageLayoutMetrics(
    /** 正文可用宽度（已扣左右边距） */
    val contentWidthPx: Float,
    /** 正文可用高度（已扣上下边距与底栏预留） */
    val contentHeightPx: Float,
    val bodyFontSizePx: Float,
    val titleFontSizePx: Float,
    val bodyLineHeightPx: Float,
    val titleLineHeightPx: Float,
    val firstLineIndentPx: Float,
    /** 右侧段评徽章预留宽度 */
    val badgeGutterPx: Float = 0f,
    /** default | xin_song | times | serif | sans | mono */
    val fontFamilyId: String = "default",
    /** 自定义字体（Liberation / Noto）；为空则按 fontFamilyId 回退系统族 */
    val typeface: android.graphics.Typeface? = null,
)

fun interface PageLayoutEngine {
    fun paginate(blocks: List<ContentBlock>, metrics: PageLayoutMetrics): List<PageContent>
}
