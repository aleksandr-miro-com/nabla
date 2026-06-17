package com.miro.demo

import com.google.common.util.concurrent.RateLimiter
import com.miro.collaboration.engine.api.RoomAuthorizationManager
import com.miro.collaboration.engine.api.RoomCodec
import com.miro.collaboration.engine.core.auth.session.CredentialProvider
import com.miro.collaboration.engine.core.auth.session.HandshakeCompleteListener
import com.miro.collaboration.engine.core.transport.TransportClientEventsReceiver
import com.miro.collaboration.engine.transport.common.NamedThreadFactory
import com.miro.collaboration.engine.transport.common.logging.ChannelContextLogger
import com.miro.collaboration.engine.transport.common.logging.DefaultChannelContextLogger
import com.miro.collaboration.engine.transport.common.netty.warmUp
import com.miro.collaboration.engine.transport.ws.handlers.SpringShutdownChannelsHandler
import com.miro.collaboration.engine.transport.ws.handlers.feature.NettyTransportChannelHandler
import com.miro.collaboration.engine.transport.ws.handlers.header.HeadersCapturer
import com.miro.collaboration.engine.transport.ws.handlers.header.HttpAuthTokenUtils
import com.miro.collaboration.engine.transport.ws.handlers.limiting.ConnectionsLimiterHandler
import com.miro.collaboration.engine.transport.ws.server.HandshakeAuthHandlersInitializerHandler
import com.miro.collaboration.engine.transport.ws.server.HandshakeInterceptor
import com.miro.collaboration.engine.transport.ws.server.MetricsCredentialProvider
import com.miro.collaboration.engine.transport.ws.server.RoomAuthorizationMetrics
import com.miro.collaboration.engine.transport.ws.server.ServerInChannelInitializer
import com.miro.collaboration.engine.transport.ws.server.WebSocketServer
import com.miro.collaboration.engine.transport.ws.server.WebSocketServerInChannelInitializer
import io.micrometer.core.instrument.MeterRegistry
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 *
 * Dependencies:
 * - [WebSocketServerProperties]
 * - [ChannelContextLogger]
 * - [MeterRegistry] - provided by autoconfiguration
 */
@Configuration
@Import(
//    RoomAuthorizationConfiguration::class,
    WebSocketServerLifecycle::class,
    DefaultChannelContextLogger::class,
    HttpAuthTokenUtils::class,
    HeadersCapturer::class,
)
@EnableConfigurationProperties(WebSocketServerProperties::class)
open class WebSocketServerConfiguration(
    private val properties: WebSocketServerProperties,
) {
    private val serverConfig = properties.server
    private val acceptorGroup: EventLoopGroup =
        NioEventLoopGroup(serverConfig.acceptorThreads, NamedThreadFactory("ws-server-acceptor-"))
            .warmUp()
    private val workerGroup: EventLoopGroup =
        NioEventLoopGroup(serverConfig.workerThreads, NamedThreadFactory("ws-worker-"))
            .warmUp()

    @PreDestroy
    open fun preDestroy() {
        acceptorGroup.shutdownGracefully().sync()
        workerGroup.shutdownGracefully().sync()
    }

    @Bean
    open fun webSocketServerConfig() = properties.server

    @Bean
    open fun webSocketServer(channelInitializer: List<ServerInChannelInitializer>) =
        WebSocketServer(serverConfig, acceptorGroup, workerGroup, channelInitializer)

    @Bean("wsAuthDispatcher")
    open fun wsAuthDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(serverConfig.authThreads)

    @Bean
    open fun nettyTransportChannelHandler(
        roomCodec: RoomCodec<*, *>,
        transportClientEventsReceiver: TransportClientEventsReceiver,
    ): NettyTransportChannelHandler = NettyTransportChannelHandler(roomCodec, transportClientEventsReceiver)

    @Bean
    open fun nonSecuredChannelInitializer(
        meterRegistry: MeterRegistry,
        logger: ChannelContextLogger,
        connectionsLimiterHandler: ConnectionsLimiterHandler,
        @Qualifier("serverShutdownChannelsHandler") shutdownHandler: SpringShutdownChannelsHandler,
        nettyTransportChannelHandler: NettyTransportChannelHandler,
        headerCapturer: HeadersCapturer,
        authHandlersInitializerHandler: HandshakeAuthHandlersInitializerHandler,
        handshakeCompleteListener: HandshakeCompleteListener,
    ): ServerInChannelInitializer =
        WebSocketServerInChannelInitializer(
            serverConfig,
            meterRegistry,
            logger,
            headerCapturer,
            connectionsLimiterHandler,
            shutdownHandler,
            nettyTransportChannelHandler,
            authHandlersInitializerHandler,
            handshakeCompleteListener,
        )

    @Bean
    open fun webSocketAuthHandlersInitializerHandler(
        credentialProvider: CredentialProvider,
        roomAuthorizationManager: RoomAuthorizationManager,
        @Qualifier("wsAuthDispatcher") authDispatcher: CoroutineDispatcher,
        handshakeInterceptor: HandshakeInterceptor? = null,
        meterRegistry: MeterRegistry,
    ): HandshakeAuthHandlersInitializerHandler =
        HandshakeAuthHandlersInitializerHandler(
            serverConfig,
            MetricsCredentialProvider(credentialProvider, meterRegistry),
            roomAuthorizationManager,
            RoomAuthorizationMetrics(meterRegistry),
            authDispatcher,
            handshakeInterceptor,
        )

    @Bean
    open fun connectionsLimiterHandler() = ConnectionsLimiterHandler(properties.inboundConnectionsLimiter)

    /**
     * Server termination handler initiates channel closure during termination
     */
    @Suppress("UnstableApiUsage")
    @Bean("serverShutdownChannelsHandler")
    open fun serverShutdownChannelsHandler(): SpringShutdownChannelsHandler {
        val rateLimiter = RateLimiter.create(100.0)

        return SpringShutdownChannelsHandler(serverConfig.terminationGracePeriod) {
            rateLimiter.acquire()
            close()
        }
    }

    companion object {
        // if you change the name, change it also in the management.endpoint.health.group.readiness.include property
        const val CONNECTIONS_LIMITER_HANDLER_NAME = "connectionsLimit"
    }
}
