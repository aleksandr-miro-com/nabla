package com.miro.nabla.playground

import com.miro.nabla.Element
import com.miro.nabla.NablaBuilder

/**
 * A tree node: a single element (length 1) with a stable [id] (for the UI to track it) and a nested
 * document of child nodes. The node's colour lives in the surrounding Insert/Retain attributes.
 * Nodes never merge, so siblings stay distinct.
 */
data class NodeElement(
    val id: Long,
    override val children: NablaBuilder = NablaBuilder(),
) : Element {
    override val length: Int get() = 1

    override fun subElement(offset: Int, length: Int): Element = this

    override fun withChildren(children: NablaBuilder): Element = copy(children = children)
}
