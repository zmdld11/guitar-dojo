package dev.zmdld11.guitardojo.core.plan

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 计划快照（plan_json）与已完成步骤列表的编解码。 */
object PlanCodec {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

    fun decodePlan(text: String): DailyPlan =
        try {
            json.decodeFromString<DailyPlan>(text)
        } catch (error: IllegalArgumentException) {
            throw PlanSnapshotException("计划快照无法解析：${error.message ?: "格式错误"}", error)
        }

    fun encodePlan(plan: DailyPlan): String = json.encodeToString(DailyPlan.serializer(), plan)

    fun decodeCompletedSteps(text: String): List<Int> =
        try {
            json.decodeFromString<List<Int>>(text)
        } catch (error: IllegalArgumentException) {
            emptyList()
        }

    fun encodeCompletedSteps(steps: List<Int>): String = json.encodeToString(steps)

    class PlanSnapshotException(
        message: String,
        cause: Throwable? = null,
    ) : Exception(message, cause)
}
