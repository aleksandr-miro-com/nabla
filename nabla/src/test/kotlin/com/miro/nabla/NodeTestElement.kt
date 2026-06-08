package com.miro.nabla

/**
 * A tree node for tests: a single element (length 1) with a [label] for identification and a nested
 * document of child nodes. Nodes never merge (no [merge] override), so siblings stay distinct.
 */
data class NodeTestElement(
    val label: String,
    override val children: NablaBuilder = NablaBuilder(),
) : Element {
    override val length: Int get() = 1

    override fun subElement(offset: Int, length: Int): Element {
        OperationUtils.checkRange(offset, offset + length, this.length)
        return this
    }

    override fun withChildren(children: NablaBuilder): Element = copy(children = children)
}
