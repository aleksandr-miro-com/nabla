package com.miro.demo

import com.miro.collaboration.engine.api.Room
import com.miro.collaboration.engine.api.RoomKey
import com.miro.collaboration.engine.core.room.RoomService
import com.miro.collaboration.engine.core.room.internal.RoomClientControl
import com.miro.collaboration.engine.core.transport.TransportClient

class DemoRoomService(private val delegate: RoomService) : RoomService {
    override fun deploy(roomKey: RoomKey): Room {
       return delegate.deploy(roomKey)
    }

    override fun enter(client: TransportClient): RoomClientControl {
        deploy(client.roomKey)
        return delegate.enter(client)
    }
}