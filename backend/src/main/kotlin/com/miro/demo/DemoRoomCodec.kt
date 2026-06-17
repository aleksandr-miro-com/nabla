package com.miro.demo

import com.fasterxml.jackson.databind.ObjectMapper
import com.miro.collaboration.engine.api.RoomCodec
import com.miro.collaboration.engine.api.TransportMessage
import org.springframework.stereotype.Component
import java.nio.ByteBuffer

@Component
class DemoRoomCodec(
    private val objectMapper: ObjectMapper,
) : RoomCodec<DemoInMessage, OutMessage> {
    override fun decode(input: TransportMessage): DemoInMessage {
        return objectMapper.readValue(input.asString(), DemoInMessage::class.java)
    }

    override fun encode(message: OutMessage): TransportMessage {
        val byteBuf =
            ByteBuffer.wrap(
                objectMapper.writeValueAsBytes(message),
            )
        return TransportMessage.Binary(byteBuf)
    }
}
