package dev.zmdld11.guitardojo.data.cards

/** 内置卡片的内容入口。卡片与乐句解耦，后续可以按同一模型接入本地/用户卡片。 */
object BuiltInCards {
    val all: List<TrainingCard> =
        TheoryCards.cards + PracticeCards.cards + SetupCards.cards + MaintenanceCards.cards

    private val grouped: Map<CardDeck, List<TrainingCard>> = all.groupBy(TrainingCard::deck)

    fun byDeck(deck: CardDeck): List<TrainingCard> = grouped[deck].orEmpty()
}
