package com.miro.nabla.playground

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay

fun main() = application {
    val model = remember { PlaygroundModel(clientCount = 2) }

    // Auto-play: send one queued message per client on each tick while enabled.
    LaunchedEffect(model.playing) {
        while (model.playing) {
            for (i in model.hub.clients.indices) {
                model.stepClient(i)
            }
            delay(900)
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "nabla playground",
        state = rememberWindowState(size = DpSize(1400.dp, 860.dp)),
    ) {
        MaterialTheme {
            App(model)
        }
    }
}
