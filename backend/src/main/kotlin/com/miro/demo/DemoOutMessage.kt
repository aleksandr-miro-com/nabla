package com.miro.demo

import com.fasterxml.jackson.annotation.JsonProperty

sealed class OutMessage(
    val type: Type,
) {
    enum class Type {
        @JsonProperty(PongMessage.TYPE)
        PONG,
    }
}

data object PongMessage : OutMessage(type = Type.PONG) {
    const val TYPE = "pong"
}
