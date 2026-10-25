package dev.zmdld11.guitardojo.data.cards

/** 卡片牌组。id 用于导航与本地进度键，改动会丢失已有进度。 */
enum class CardDeck(
    val id: String,
    val title: String,
    val subtitle: String,
) {
    THEORY(
        id = "theory",
        title = "乐理",
        subtitle = "音名、音程、音阶与节奏",
    ),
    PRACTICE(
        id = "practice",
        title = "练法",
        subtitle = "怎么练，比练多久更重要",
    ),
    SETUP(
        id = "setup",
        title = "设备",
        subtitle = "音箱、拾音器与琴上旋钮",
    ),
    MAINTENANCE(
        id = "maintenance",
        title = "保养",
        subtitle = "练完顺手做的事",
    ),
    ;

    companion object {
        fun fromId(id: String): CardDeck? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 一张微卡片：先给问题，再看答案。
 *
 * 文案约束见 `docs/训练计划设计.md` 第 6 节：一张卡只问一件事，答案 1–2 句，
 * 不写长篇解释，不把卡片做成文章。
 */
data class TrainingCard(
    val id: String,
    val deck: CardDeck,
    val title: String,
    val prompt: String,
    val answer: String,
    val detail: String = "",
    /** 可选插图，路径相对 assets，例如 `figures/d3_knobs_layout.png`。 */
    val figure: String? = null,
)
