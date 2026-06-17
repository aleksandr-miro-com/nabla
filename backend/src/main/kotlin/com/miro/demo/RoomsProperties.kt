package com.miro.demo

import com.miro.collaboration.engine.core.room.RoomServiceProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties(prefix = "miro.collaboration.rooms")
data class RoomsProperties(
    @NestedConfigurationProperty
    val service: RoomServiceProperties = RoomServiceProperties(),
)
