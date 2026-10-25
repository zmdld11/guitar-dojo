package dev.zmdld11.guitardojo.data

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.zmdld11.guitardojo.BuildConfig
import dev.zmdld11.guitardojo.core.phrase.Phrase
import dev.zmdld11.guitardojo.core.phrase.PhraseCodec
import dev.zmdld11.guitardojo.core.phrase.PhraseException
import dev.zmdld11.guitardojo.core.phrase.PhraseOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "PhraseLoad"

data class PhraseLibrary(
    val phrases: List<Phrase>,
    val errors: List<String>,
)

/**
 * 乐句来源：
 * - assets/phrases 下的内置 JSON（仓库自编内容）；
 * - filesDir/phrases 下的应用内导入；
 * - getExternalFilesDir("phrases")：adb push，方便批量导入本机谱。
 *
 * 同名 id 以后加载的为准（本地覆盖内置），但内置 id 有 builtin. 前缀，正常不会冲突。
 */
class PhraseRepository(
    private val context: Context,
) {
    suspend fun load(): PhraseLibrary =
        withContext(Dispatchers.IO) {
            val startedNanos = System.nanoTime()
            val phrases = LinkedHashMap<String, Phrase>()
            val errors = mutableListOf<String>()

            val assets = assetFiles()
            if (BuildConfig.DEBUG) Log.d(TAG, "load start assets=${assets.size}")
            assets.forEach { name ->
                runCatching {
                    val text =
                        context.assets
                            .open("phrases/$name")
                            .bufferedReader()
                            .use { it.readText() }
                    PhraseCodec.decode(text)
                }.onSuccess { phrases[it.id] = it }
                    .onFailure { errors += "内置 $name：${it.readableMessage()}" }
            }

            listOf(
                File(context.filesDir, "phrases"),
                context.getExternalFilesDir("phrases") ?: File(context.filesDir, "external-phrases"),
            ).forEach { dir ->
                if (BuildConfig.DEBUG) Log.d(TAG, "dir=${dir.absolutePath} isDirectory=${dir.isDirectory}")
                if (!dir.isDirectory) return@forEach
                val files = dir.listFiles { file -> file.extension == "json" }?.sortedBy { it.name }.orEmpty()
                if (BuildConfig.DEBUG) Log.d(TAG, "dir=${dir.absolutePath} json=${files.size}")
                files.forEach { file ->
                    val fileStartedNanos = System.nanoTime()
                    val result = runCatching { PhraseCodec.decode(file.readText()) }
                    result
                        .onSuccess { phrases[it.id] = it }
                        .onFailure { errors += "本地 ${file.name}：${it.readableMessage()}" }
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            TAG,
                            "file=${file.name} bytes=${file.length()} " +
                                "ms=${(System.nanoTime() - fileStartedNanos) / 1_000_000} ok=${result.isSuccess}",
                        )
                    }
                }
            }

            PhraseLibrary(
                phrases =
                    phrases.values.sortedWith(
                        compareBy<Phrase> { it.origin != PhraseOrigin.BUILTIN }
                            .thenBy { it.difficulty }
                            .thenBy { it.title },
                    ),
                errors = errors,
            ).also {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "load done ms=${(System.nanoTime() - startedNanos) / 1_000_000} " +
                            "phrases=${it.phrases.size} errors=${it.errors.size}",
                    )
                }
            }
        }

    suspend fun find(id: String): Phrase? = load().phrases.firstOrNull { it.id == id }

    /** 从 SAF 选中的文件导入；先校验，通过后才落盘，失败不会留下坏文件。 */
    suspend fun import(uri: Uri): Phrase =
        withContext(Dispatchers.IO) {
            val text =
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                } ?: throw PhraseException(listOf("无法读取所选文件"))

            val phrase = PhraseCodec.decode(text)
            if (phrase.origin != PhraseOrigin.LOCAL) {
                throw PhraseException(listOf("这是内置乐句（${phrase.id}），不需要导入"))
            }

            val dir = File(context.filesDir, "phrases").apply { mkdirs() }
            File(dir, "${phrase.id}.json").writeText(text)
            phrase
        }

    private fun assetFiles(): List<String> =
        runCatching {
            context.assets
                .list("phrases")
                ?.filter { it.endsWith(".json") }
                ?.sorted()
                .orEmpty()
        }.getOrDefault(emptyList())

    private fun Throwable.readableMessage(): String =
        when (this) {
            is PhraseException -> issues.joinToString("；")
            else -> message ?: javaClass.simpleName
        }
}
