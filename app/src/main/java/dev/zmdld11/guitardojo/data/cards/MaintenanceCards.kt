package dev.zmdld11.guitardojo.data.cards

internal object MaintenanceCards {
    val cards: List<TrainingCard> =
        listOf(
            TrainingCard(
                id = "maintenance.wipe-strings",
                deck = CardDeck.MAINTENANCE,
                title = "擦弦",
                prompt = "每次练完，弦要怎么擦？",
                answer = "干布从琴桥往琴头擦。",
                detail = "汗和油留在弦上会加速生锈，也会让手感发涩。",
                figure = "figures/e2_maintenance.png",
            ),
            TrainingCard(
                id = "maintenance.fretboard",
                deck = CardDeck.MAINTENANCE,
                title = "指板",
                prompt = "指板多久清洁一次？",
                answer = "换弦时顺手擦，少量柠檬油。",
                detail = "不要泡油；枫木指板通常不需要油。",
            ),
            TrainingCard(
                id = "maintenance.storage",
                deck = CardDeck.MAINTENANCE,
                title = "放琴",
                prompt = "琴不弹时放哪里？",
                answer = "琴架或琴盒，别靠墙斜放。",
                detail = "避免阳光直射、暖气和空调直吹。",
                figure = "figures/e3_storage.png",
            ),
            TrainingCard(
                id = "maintenance.string-change",
                deck = CardDeck.MAINTENANCE,
                title = "该换弦了",
                prompt = "哪些信号说明该换弦了？",
                answer = "音准不稳、锈斑、手感发涩、频繁断弦。",
                detail = "演出或录音前别临时换没拉开的弦。",
            ),
            TrainingCard(
                id = "maintenance.amp-off",
                deck = CardDeck.MAINTENANCE,
                title = "关音箱",
                prompt = "练完关音箱的顺序是什么？",
                answer = "先把音量关小，再断电，最后拔线。",
                detail = "线缆松圈收好，不要死折。",
            ),
            TrainingCard(
                id = "maintenance.humidity",
                deck = CardDeck.MAINTENANCE,
                title = "湿度",
                prompt = "吉他适合的湿度是多少？",
                answer = "40%–60%。",
                detail = "北方冬天远离暖气，南方梅雨季放干燥剂。",
            ),
        )
}
