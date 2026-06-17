package com.miro.demo

import com.miro.collaboration.engine.core.controller.RoomDeploymentController
import com.miro.collaboration.engine.core.room.DefaultRoomAuthorizationManager
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(
    RoomsConfiguration::class,
    RoomDeploymentController::class,
    DefaultRoomAuthorizationManager::class,
)
open class RoomServerConfiguration
