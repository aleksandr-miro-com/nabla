package com.miro.demo

import com.miro.collaboration.engine.api.Room
import com.miro.collaboration.engine.api.RoomClient
import com.miro.collaboration.engine.api.RoomClientLifecycleHandler
import com.miro.collaboration.engine.api.RoomLifecycleHandler
import com.miro.collaboration.engine.api.TypedMessageHandler
import org.springframework.stereotype.Component

@Component
class DemoRoomHandler() : TypedMessageHandler<DemoInMessage>, RoomClientLifecycleHandler, RoomLifecycleHandler {
    override fun onCreated(room: Room) {
    }

    override fun onDestroyed(room: Room) {
    }

    override fun onConnected(client: RoomClient) {
    }

    override fun onClose(client: RoomClient) {
    }

    override fun onMessage(
        message: DemoInMessage,
        client: RoomClient,
    ) {
        when (message) {
            is PingMessage -> client.send(PongMessage)
        }
    }
}
