package dev.zmdld11.guitardojo.data.cards

internal object SetupCards {
    val cards: List<TrainingCard> =
        listOf(
            TrainingCard(
                id = "setup.amp-volume",
                deck = CardDeck.SETUP,
                title = "Volume",
                prompt = "音箱上的 Volume 控制什么？开机前应该怎么设？",
                answer = "控制整体响度；开机前先关小。",
                detail = "插好线、确认音量最小，再开电源。",
            ),
            TrainingCard(
                id = "setup.amp-gain",
                deck = CardDeck.SETUP,
                title = "Gain",
                prompt = "Gain 旋钮控制什么？清音和失真怎么设？",
                answer = "控制前级失真度；清音小，失真中等。",
                detail = "Gain 拉满不一定更好听，先让每个音清楚。",
            ),
            TrainingCard(
                id = "setup.amp-eq",
                deck = CardDeck.SETUP,
                title = "三段均衡",
                prompt = "Bass / Middle / Treble 先从什么位置开始调？",
                answer = "先都放 12 点，再微调。",
                detail = "小音量下可稍微减 Bass，避免声音发闷。",
                figure = "figures/d4_amp_panel.png",
            ),
            TrainingCard(
                id = "setup.pickup-switch",
                deck = CardDeck.SETUP,
                title = "拾音器拨杆",
                prompt = "拨杆的三个位置音色有什么不同？",
                answer = "琴颈圆厚，琴桥明亮，中间更均衡。",
                detail = "清音练习用中间或琴颈，riff 可试琴桥。",
                figure = "figures/d2_pickup_switch.png",
            ),
            TrainingCard(
                id = "setup.guitar-knobs",
                deck = CardDeck.SETUP,
                title = "琴上四个旋钮",
                prompt = "琴上的四个旋钮分别是什么？",
                answer = "两个音量、两个音色。",
                detail = "稍微收一点琴上音量，失真会变干净。",
                figure = "figures/d3_knobs_layout.png",
            ),
            TrainingCard(
                id = "setup.clean",
                deck = CardDeck.SETUP,
                title = "清音设置",
                prompt = "清音练习怎么设音箱？",
                answer = "Gain 低、Volume 适中、Middle 稍多。",
                detail = "目标是听清每次拨弦和按弦的杂音。",
                figure = "figures/d5_today_setting_template.png",
            ),
            TrainingCard(
                id = "setup.dirty",
                deck = CardDeck.SETUP,
                title = "失真设置",
                prompt = "失真练习怎么设音箱？",
                answer = "Gain 中等、Bass 稍减、Treble 稍加。",
                detail = "失真太大容易糊成一片，听不清换弦和时值。",
                figure = "figures/d5_today_setting_template.png",
            ),
            TrainingCard(
                id = "setup.noise",
                deck = CardDeck.SETUP,
                title = "嗡嗡声",
                prompt = "手离开弦就有嗡嗡声，碰弦就消失，可能是什么？",
                answer = "多半是接地或干扰。",
                detail = "换一个插座、远离屏幕和充电器再试。",
            ),
        )
}
