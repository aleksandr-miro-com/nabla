package com.miro.demo

import org.springframework.boot.web.context.WebServerGracefulShutdownLifecycle

internal object LifecyclePhases {
    // that way we shut down this server first and then Tomcat (actuator)
    const val WEB_SOCKET_SERVER_LIFECYCLE_PHASE = WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE + 1

    // cluster lifecycle handler start/stop should be called after/before WebSocketLifecycleHandler
    const val CLUSTER_LIFECYCLE_PHASE = WEB_SOCKET_SERVER_LIFECYCLE_PHASE + 1
}
