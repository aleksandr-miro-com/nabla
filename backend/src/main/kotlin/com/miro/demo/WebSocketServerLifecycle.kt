package com.miro.demo

import com.miro.collaboration.engine.transport.ws.server.WebSocketServer
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component

@Component
open class WebSocketServerLifecycle(
    private val server: WebSocketServer,
) : SmartLifecycle {
    override fun start() {
        server.start()
    }

    override fun stop() {
        server.stop()
    }

    override fun isRunning(): Boolean = server.isRunning()

    override fun getPhase(): Int = LifecyclePhases.WEB_SOCKET_SERVER_LIFECYCLE_PHASE
}