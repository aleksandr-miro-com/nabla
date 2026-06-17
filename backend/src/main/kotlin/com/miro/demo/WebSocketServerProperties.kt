package com.miro.demo

import com.miro.collaboration.engine.transport.ws.handlers.limiting.InboundConnectionsLimitingConfig
import com.miro.collaboration.engine.transport.ws.server.WebSocketServerConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties(prefix = "ws-server")
data class WebSocketServerProperties(
    @NestedConfigurationProperty
    val server: WebSocketServerConfig = WebSocketServerConfig(),
    @NestedConfigurationProperty
    val inboundConnectionsLimiter: InboundConnectionsLimitingConfig = InboundConnectionsLimitingConfig(),
)
