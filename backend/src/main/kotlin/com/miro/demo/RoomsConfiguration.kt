package com.miro.demo

import com.miro.collaboration.engine.api.RoomClientLifecycleHandler
import com.miro.collaboration.engine.api.RoomKey
import com.miro.collaboration.engine.api.RoomKeyDeserializer
import com.miro.collaboration.engine.api.RoomLifecycleHandler
import com.miro.collaboration.engine.api.TypedMessageHandler
import com.miro.collaboration.engine.core.auth.session.HandshakeCompleteListener
import com.miro.collaboration.engine.core.contract.DefaultRoomKeyDeserializer
import com.miro.collaboration.engine.core.lifecycle.ClusterMemberLifecycleStatus
import com.miro.collaboration.engine.core.registry.ClusterMember
import com.miro.collaboration.engine.core.registry.ClusterMemberId
import com.miro.collaboration.engine.core.registry.CollaborationApplicationRegistry
import com.miro.collaboration.engine.core.room.DefaultRoomService
import com.miro.collaboration.engine.core.room.RoomService
import com.miro.collaboration.engine.core.transport.RoomTransportClientEventsReceiver
import com.miro.collaboration.engine.core.transport.TransportClientEventsReceiver
import kotlinx.coroutines.Job
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(RoomsProperties::class)
open class RoomsConfiguration {

    @Bean
    open fun noOpHandshakeCompleteListener(): HandshakeCompleteListener =
        HandshakeCompleteListener { _, _ -> Job().also { it.complete() } }

    @Bean
    open fun collaborationApplicationRegistry(): CollaborationApplicationRegistry {
        return object : CollaborationApplicationRegistry {
            override fun create(memberId: ClusterMemberId, roomKey: RoomKey) = Unit

            override fun delete(memberId: ClusterMemberId, roomKey: RoomKey) = Unit
        }
    }

    @Bean
    open fun roomService(
        properties: RoomsProperties,
        collaborationApplicationRegistry: CollaborationApplicationRegistry,
        roomHandlers: List<RoomLifecycleHandler>,
        clientHandlers: List<RoomClientLifecycleHandler>,
    ): RoomService {
        val clusterMember = ClusterMember(
            ClusterMemberId("test"),
            "localhost",
            "",
            ClusterMemberLifecycleStatus.Active,
        )
        return DemoRoomService(
            DefaultRoomService(
                properties.service,
                collaborationApplicationRegistry,
                clusterMemberProvider = { clusterMember },
                roomHandlers,
                clientHandlers,
            ),
        )
    }

    @Bean
    open fun roomFeature(
        roomService: RoomService,
        @RoomMessageHandler messageControllers: List<RoomLifecycleHandler>,
        typedMessageHandlers: List<TypedMessageHandler<*>>,
    ): TransportClientEventsReceiver =
        RoomTransportClientEventsReceiver(
            roomService,
            (messageControllers + typedMessageHandlers).toSet(),
        )

    @Bean
    @ConditionalOnMissingBean(RoomKeyDeserializer::class)
    open fun defaultRoomKeyDeserializer(): RoomKeyDeserializer<RoomKey> = DefaultRoomKeyDeserializer()
}
