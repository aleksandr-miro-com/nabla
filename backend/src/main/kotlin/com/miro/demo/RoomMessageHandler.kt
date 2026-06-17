package com.miro.demo

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Controller

/**
 * Marks a Spring bean as a room message controller — a component whose [com.miro.collaboration.engine.api.OnMessage]-annotated
 * methods are registered as inbound message handlers in the room dispatch pipeline.
 *
 * Serves two roles simultaneously:
 * - **Stereotype** (via `@Controller`): registers the annotated class as a Spring bean.
 * - **Qualifier** (via `@Qualifier`): enables [com.miro.collaboration.engine.spring.core.room.RoomsConfiguration]
 *   to collect all such beans and feed them into [com.miro.collaboration.engine.core.room.message.InPlaceMessageDispatcher].
 *
 * Message routing uses the most-specific-type rule. See [com.miro.collaboration.engine.api.OnMessage] for
 * supported method signatures and dispatch semantics.
 *
 * @see com.miro.collaboration.engine.api.OnMessage
 * @see com.miro.collaboration.engine.api.TypedMessageHandler
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Controller
@Qualifier
annotation class RoomMessageHandler
