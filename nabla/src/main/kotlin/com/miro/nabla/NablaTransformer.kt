package com.miro.nabla

import kotlin.math.min

class NablaTransformer {

    fun transform(a: NablaBuilder, b: NablaBuilder, priority: Boolean = false): NablaBuilder {
        // a's moves relocate content; b's edits over that content must follow it. First learn how
        // b's edits land on a's cuts (to replay them at a's paste) and how much of b's own cuts
        // survive a's deletes (to trim b's paste), then emit b'.
        val redirect = mutableMapOf<BufferId, MutableList<Operation>>()
        val survived = mutableMapOf<BufferId, Int>()

        walk(a, b, priority, onAligned = { thisOp, otherOp, length ->
            if (thisOp is Delete && thisOp.bufferId != null) {
                // b's edit lands on content a is moving: replay it at a's paste.
                redirect.getOrPut(thisOp.bufferId) { mutableListOf() }.add(redirectChunk(otherOp, length))
            }
            if (otherOp is Delete && otherOp.bufferId != null) {
                // b's cut survives only where a does not also delete it; accumulate (with 0s) so the
                // length is known even when a deletes the whole range — then b's paste shrinks to it.
                val surviving = if (thisOp is Delete) 0 else length
                survived[otherOp.bufferId] = (survived[otherOp.bufferId] ?: 0) + surviving
            }
        })

        val builder = NablaBuilder()
        walk(
            a, b, priority,
            onThisInsert = { insert ->
                val element = insert.element
                if (element is BufferElement) {
                    // a re-inserts moved content here: replay the edits b made to it at its source.
                    (redirect[element.bufferId] ?: listOf(Retain(insert.length))).forEach(builder::push)
                } else {
                    builder.retain(insert.length)
                }
            },
            onOtherInsert = { insert ->
                val element = insert.element
                if (element is BufferElement) {
                    // b pastes moved content: keep only what survived a's deletes to its source.
                    val length = survived[element.bufferId] ?: element.length
                    if (length > 0) {
                        builder.push(Insert(BufferElement(element.bufferId, length), insert.attributes))
                    }
                } else {
                    builder.push(insert)
                }
            },
            onAligned = { thisOp, otherOp, length ->
                if (thisOp is Delete) {
                    // a deleted or moved this content; b' does nothing here (moves replay at the paste).
                } else if (otherOp is Delete) {
                    builder.push(otherOp)
                } else {
                    builder.retain(
                        length,
                        AttributeMap.transform(attributesOf(thisOp), attributesOf(otherOp), priority),
                    )
                }
            },
        )

        return chop(builder)
    }

    fun transformPosition(a: NablaBuilder, index: Int, priority: Boolean = false): Int {
        var idx = index
        val iter = OperationIterator(a.ops)
        var offset = 0
        while (iter.hasNext() && offset <= idx) {
            val length = iter.peekLength()
            val op = iter.peek()
            iter.next()
            if (op is Delete) {
                idx -= min(length, idx - offset)
                continue
            } else if (op is Insert && (offset < idx || !priority)) {
                idx += length
            }
            offset += length
        }
        return idx
    }

    /**
     * Walks [a] and [b] in lock-step (the shared spine of transform). Each leading insert in [a] or
     * [b] is reported on its own; otherwise both sides advance by the same length and the aligned
     * pair is reported. Used twice per transform: once to analyze, once to emit.
     */
    private fun walk(
        a: NablaBuilder,
        b: NablaBuilder,
        priority: Boolean,
        onThisInsert: (Insert) -> Unit = {},
        onOtherInsert: (Insert) -> Unit = {},
        onAligned: (thisOp: Operation?, otherOp: Operation?, length: Int) -> Unit,
    ) {
        val thisIter = OperationIterator(a.ops)
        val otherIter = OperationIterator(b.ops)

        while (thisIter.hasNext() || otherIter.hasNext()) {
            if (thisIter.peek() is Insert && (priority || otherIter.peek() !is Insert)) {
                onThisInsert(thisIter.next() as Insert)
            } else if (otherIter.peek() is Insert) {
                onOtherInsert(otherIter.next() as Insert)
            } else {
                val length = min(
                    if (thisIter.hasNext()) thisIter.peekLength() else Int.MAX_VALUE,
                    if (otherIter.hasNext()) otherIter.peekLength() else Int.MAX_VALUE,
                )
                // A `null` op stands for an exhausted side, i.e. an implicit attribute-less retain.
                val thisOp = if (thisIter.hasNext()) thisIter.next(length) else null
                val otherOp = if (otherIter.hasNext()) otherIter.next(length) else null
                onAligned(thisOp, otherOp, length)
            }
        }
    }

    /** The op b applied to one stretch of moved content, normalized to [length] for replay at the paste. */
    private fun redirectChunk(otherOp: Operation?, length: Int): Operation = when (otherOp) {
        is Delete -> Delete(length)
        is Retain -> otherOp
        else -> Retain(length)
    }

    private fun attributesOf(op: Operation?): AttributeMap = when (op) {
        is Insert -> op.attributes
        is Retain -> op.attributes
        else -> AttributeMap.empty()
    }

    private fun chop(builder: NablaBuilder): NablaBuilder {
        val last = builder.ops.lastOrNull()
        if (last is Retain && last.attributes.isEmpty) {
            return NablaBuilder(builder.ops.dropLast(1))
        }
        return builder
    }
}
