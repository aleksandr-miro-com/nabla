package com.miro.demo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(property = "type", use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes(
    value = [
        JsonSubTypes.Type(value = PingMessage::class, name = PingMessage.TYPE),
    ],
)
sealed class DemoInMessage(
    val type: Type,
) {
    enum class Type {
        @JsonProperty(PingMessage.TYPE)
        PING,
    }
}

class PingMessage : DemoInMessage(type = Type.PING) {
    companion object {
        const val TYPE = "ping"
    }
}
