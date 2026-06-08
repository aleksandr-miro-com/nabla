package com.miro.nabla

/**
 * The placeholder of a "paste": a reference, by [bufferId], to the content removed by the matching
 * cut ([Delete] sharing the same id). It carries only a [length] for position math — the actual
 * content is resolved by [NablaComposer] when the move is composed onto a document, so concurrent
 * edits to the moved range and its formatting travel with it automatically.
 */
data class BufferElement(
    val bufferId: BufferId,
    override val length: Int,
) : Element {
    init {
        require(length > 0) { "buffer length must be positive" }
    }

    override fun subElement(offset: Int, length: Int): Element {
        OperationUtils.checkRange(offset, offset + length, this.length)
        if (offset == 0 && length == this.length) {
            return this
        }
        return BufferElement(bufferId, length)
    }
}
