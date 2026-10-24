package dev.zmdld11.guitardojo.core.audio.pitch

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * YIN 与 MPM 的选型对比（issue #11）。
 *
 * 不追求统计显著性，只求在「电吉他真实会出现的信号」上把倍频错误暴露出来：
 * 弱基频 + 强泛音、失真感的密集谐波、加噪、高把位、静音。
 * 对比结果记录在 issue 里，运行时只用 MPM。
 */
class PitchDetectorComparisonTest {
    private data class Case(
        val name: String,
        val samples: FloatArray,
        val expectedHz: Double?,
    )

    @Test
    fun `MPM 的倍频错误不劣于 YIN`() {
        val yin = YinPitchDetector(Signals.SAMPLE_RATE)
        val mpm = MpmPitchDetector(Signals.SAMPLE_RATE)

        val cases =
            listOf(
                Case("纯正弦 E2", Signals.tone(82.41), 82.41),
                Case(
                    "弱基频 E2（拾音器削弱基频）",
                    Signals.tone(82.41, harmonics = listOf(0.3, 1.0, 0.7, 0.4, 0.2)),
                    82.41,
                ),
                Case(
                    "失真感 A2（密集谐波）",
                    Signals.tone(110.0, harmonics = listOf(1.0, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3)),
                    110.0,
                ),
                Case("加噪 G3（15% 白噪）", Signals.tone(196.0, noiseAmount = 0.15), 196.0),
                Case("高把位 E5", Signals.tone(659.26), 659.26),
                Case("高把位 E6", Signals.tone(1318.51), 1318.51),
                Case("静音", Signals.silence(), null),
            )

        var yinOctaveErrors = 0
        var mpmOctaveErrors = 0
        var yinMisses = 0
        var mpmMisses = 0

        val report = StringBuilder()
        report.appendLine("| 用例 | 期望 Hz | YIN | MPM |")
        report.appendLine("| --- | --- | --- | --- |")

        cases.forEach { case ->
            val yinResult = yin.detect(case.samples)
            val mpmResult = mpm.detect(case.samples)

            if (case.expectedHz == null) {
                if (yinResult != null) yinMisses++
                if (mpmResult != null) mpmMisses++
                report.appendLine("| ${case.name} | 无 | $yinResult | $mpmResult |")
                return@forEach
            }

            val yinVerdict = verdict(yinResult, case.expectedHz)
            val mpmVerdict = verdict(mpmResult, case.expectedHz)
            if (yinVerdict == Verdict.OCTAVE) yinOctaveErrors++
            if (mpmVerdict == Verdict.OCTAVE) mpmOctaveErrors++
            if (yinVerdict == Verdict.MISS) yinMisses++
            if (mpmVerdict == Verdict.MISS) mpmMisses++

            report.appendLine(
                "| ${case.name} | ${case.expectedHz} | ${describe(yinResult)} ${yinVerdict.label} | " +
                    "${describe(mpmResult)} ${mpmVerdict.label} |",
            )
        }

        println(report)
        println("YIN：倍频错误 $yinOctaveErrors，未识别 $yinMisses")
        println("MPM：倍频错误 $mpmOctaveErrors，未识别 $mpmMisses")

        assertTrue("MPM 的倍频错误（$mpmOctaveErrors）应不多于 YIN（$yinOctaveErrors）", mpmOctaveErrors <= yinOctaveErrors)
        assertTrue("MPM 未识别数不应超过 1 个，实际 $mpmMisses", mpmMisses <= 1)
    }

    private fun verdict(
        result: PitchResult?,
        expected: Double,
    ): Verdict {
        if (result == null) return Verdict.MISS
        val cents = NoteNaming.centsBetween(expected, result.frequencyHz)
        return when {
            abs(cents) <= 50.0 -> Verdict.OK
            // 与期望相差约一个八度（±3 音分）即视为倍频错误
            abs(abs(cents) - 1200.0) <= 60.0 -> Verdict.OCTAVE
            else -> Verdict.WRONG
        }
    }

    private fun describe(result: PitchResult?): String =
        result?.let { "${"%.1f".format(it.frequencyHz)}Hz/%.2f".format(it.clarity) } ?: "null"

    private enum class Verdict(
        val label: String,
    ) {
        OK("✓"),
        OCTAVE("倍频✗"),
        WRONG("✗"),
        MISS("未识别✗"),
    }
}
