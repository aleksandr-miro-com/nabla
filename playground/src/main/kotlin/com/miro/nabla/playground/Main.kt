package com.miro.nabla.playground

import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay

fun main() = application {
    val textModel = remember { PlaygroundModel(clientCount = 2) }
    val treeModel = remember { TreeModel(clientCount = 2) }
    var tab by remember { mutableStateOf(0) }

    // Auto-play: send one queued message per client on each tick while enabled (per playground).
    LaunchedEffect(textModel.playing) {
        while (textModel.playing) {
            for (i in textModel.hub.clients.indices) {
                textModel.stepClient(i)
            }
            delay(900)
        }
    }
    LaunchedEffect(treeModel.playing) {
        while (treeModel.playing) {
            for (i in treeModel.hub.clients.indices) {
                treeModel.stepClient(i)
            }
            delay(900)
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "nabla playground",
        state = rememberWindowState(size = DpSize(1400.dp, 900.dp)),
    ) {
        MaterialTheme {
            Column {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Text") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Tree") })
                }
                when (tab) {
                    0 -> App(textModel)
                    else -> TreeAppView(treeModel)
                }
            }
        }
    }
}
