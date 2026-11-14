package dev.zmdld11.guitardojo.ui.screens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 谱面几何与字号。手机竖屏用紧凑档；横屏与平板放大到中档/大档，
 * 同一个乐句在窄屏和宽屏上都能占满可用宽度。
 */
internal data class ScoreMetrics(
    val beatWidth: Dp,
    val stringGap: Dp,
    val header: Dp,
    val stem: Dp,
    val leading: Dp,
    val bottomPadding: Dp,
    val tupletTop: Dp,
    val noteBoxWidth: Dp,
    val noteBoxHeight: Dp,
    val fretFont: Int,
    val nameFont: Int,
    val legendFont: Int,
    val barNumFont: Int,
    val signatureFont: Int,
    val tupletFont: Int,
    /** 符干偏移、符杠间距等小记号的整体缩放，保持与弦距成比例。 */
    val glyphScale: Float,
) {
    val staffTop: Dp get() = header + stem

    /** 小记号尺寸：紧凑档基准值 × glyphScale。 */
    fun glyph(base: Float): Dp = (base * glyphScale).dp

    fun glyph(base: Int): Dp = (base * glyphScale).dp

    companion object {
        fun forWidth(widthDp: Int): ScoreMetrics =
            when {
                widthDp >= EXPANDED_WIDTH_DP -> EXPANDED
                widthDp >= MEDIUM_WIDTH_DP -> MEDIUM
                else -> COMPACT
            }

        private const val MEDIUM_WIDTH_DP = 600
        private const val EXPANDED_WIDTH_DP = 840

        private val COMPACT =
            ScoreMetrics(
                beatWidth = 46.dp,
                stringGap = 13.dp,
                header = 40.dp,
                stem = 18.dp,
                leading = 14.dp,
                bottomPadding = 12.dp,
                tupletTop = 28.dp,
                noteBoxWidth = 24.dp,
                noteBoxHeight = 13.dp,
                fretFont = 10,
                nameFont = 8,
                legendFont = 9,
                barNumFont = 9,
                signatureFont = 8,
                tupletFont = 8,
                glyphScale = 1.0f,
            )

        private val MEDIUM =
            ScoreMetrics(
                beatWidth = 62.dp,
                stringGap = 17.dp,
                header = 52.dp,
                stem = 24.dp,
                leading = 18.dp,
                bottomPadding = 14.dp,
                tupletTop = 36.dp,
                noteBoxWidth = 30.dp,
                noteBoxHeight = 17.dp,
                fretFont = 13,
                nameFont = 11,
                legendFont = 11,
                barNumFont = 11,
                signatureFont = 10,
                tupletFont = 10,
                glyphScale = 1.25f,
            )

        private val EXPANDED =
            ScoreMetrics(
                beatWidth = 78.dp,
                stringGap = 22.dp,
                header = 64.dp,
                stem = 30.dp,
                leading = 22.dp,
                bottomPadding = 16.dp,
                tupletTop = 46.dp,
                noteBoxWidth = 36.dp,
                noteBoxHeight = 21.dp,
                fretFont = 15,
                nameFont = 13,
                legendFont = 13,
                barNumFont = 13,
                signatureFont = 12,
                tupletFont = 12,
                glyphScale = 1.5f,
            )
    }
}
