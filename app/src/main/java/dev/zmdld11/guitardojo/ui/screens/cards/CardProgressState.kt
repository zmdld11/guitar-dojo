package dev.zmdld11.guitardojo.ui.screens.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.zmdld11.guitardojo.data.cards.CardProgress
import dev.zmdld11.guitardojo.data.cards.CardProgressStore

/** 读卡片进度；从卡片页返回时在 ON_RESUME 重新读一次，避免首页显示旧数据。 */
@Composable
fun rememberCardProgress(): Map<String, CardProgress> {
    val context = LocalContext.current
    val store = remember(context) { CardProgressStore(context) }
    var progress by remember(store) { mutableStateOf(store.load()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, store) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) progress = store.load()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return progress
}
