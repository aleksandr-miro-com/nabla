package com.miro.nabla

import kotlin.math.min

class NablaComposer {

    fun compose(a: NablaBuilder, b: NablaBuilder): NablaBuilder {
        val buffers = mutableMapOf<BufferId, MutableList<Operation>>()
        return expandPastes(composeOps(a, b, buffers), buffers)
    }

    private fun composeOps(
        a: NablaBuilder,
        b: NablaBuilder,
        buffers: MutableMap<BufferId, MutableList<Operation>>,
    ): NablaBuilder {
        val thisIter = OperationIterator(a.ops)
        val otherIter = OperationIterator(b.ops)

        val head = mutableListOf<Operation>()

        // Retain-start optimization: copy leading inserts that an attribute-less leading retain
        // simply skips over, instead of recomposing them op by op.
        val firstOther = otherIter.peek()
        if (firstOther is Retain && firstOther.attributes.isEmpty) {
            var firstLeft = firstOther.length
            while (thisIter.peek() is Insert && thisIter.peekLength() <= firstLeft) {
                firstLeft -= thisIter.peekLength()
                head.add(thisIter.next())
            }
            if (firstOther.length - firstLeft > 0) {
                otherIter.next(firstOther.length - firstLeft)
            }
        }

        val builder = NablaBuilder(head)

        while (thisIter.hasNext() || otherIter.hasNext()) {
            if (otherIter.peek() is Insert) {
                builder.push(otherIter.next())
            } else if (thisIter.peek() is Delete) {
                builder.push(thisIter.next())
            } else {
                val length = min(
                    if (thisIter.hasNext()) thisIter.peekLength() else Int.MAX_VALUE,
                    if (otherIter.hasNext()) otherIter.peekLength() else Int.MAX_VALUE,
                )
                // A `null` op stands for an exhausted side, i.e. an implicit attribute-less retain.
                val thisOp = if (thisIter.hasNext()) thisIter.next(length) else null
                val otherOp = if (otherIter.hasNext()) otherIter.next(length) else null

                val thisIsRetain = thisOp == null || thisOp is Retain

                if (otherOp == null || otherOp is Retain) {
                    // Preserve null when composing onto a retain; drop it for an insert.
                    val attributes = AttributeMap.compose(
                        attributesOf(thisOp),
                        attributesOf(otherOp),
                        keepNull = thisIsRetain,
                    )
                    val newOp: Operation =
                        if (thisOp is Insert) thisOp.copy(attributes = attributes)
                        else Retain(length, attributes)

                    builder.push(newOp)

                    // Optimization: if the rest of `other` is just a retain, the remainder of
                    // `this` passes through unchanged.
                    if (!otherIter.hasNext() && builder.ops.lastOrNull() == newOp) {
                        return chop(concat(builder, thisIter.remaining()))
                    }
                } else if (otherOp is Delete) {
                    if (otherOp.bufferId != null && thisOp is Insert) {
                        // A cut over inserted content: capture it for the matching paste to re-emit,
                        // then drop it here (the insert + delete cancels out).
                        buffers.getOrPut(otherOp.bufferId) { mutableListOf() }.add(thisOp)
                    } else if (thisIsRetain) {
                        // A delete over our retain (or implicit retain): keep it. An insert + plain
                        // delete cancels out, so an insert `thisOp` is simply dropped.
                        builder.push(otherOp)
                    }
                }
            }
        }

        return chop(builder)
    }

    private fun attributesOf(op: Operation?): AttributeMap = when (op) {
        is Insert -> op.attributes
        is Retain -> op.attributes
        else -> AttributeMap.empty()
    }

    private fun concat(builder: NablaBuilder, rest: List<Operation>): NablaBuilder {
        if (rest.isEmpty()) {
            return builder
        }
        builder.push(rest[0])
        return NablaBuilder(builder.ops + rest.drop(1))
    }

    private fun chop(builder: NablaBuilder): NablaBuilder {
        val last = builder.ops.lastOrNull()
        if (last is Retain && last.attributes.isEmpty) {
            return NablaBuilder(builder.ops.dropLast(1))
        }
        return builder
    }

    /**
     * Replaces each resolved paste with the content its cut captured into [buffers]. A paste whose
     * buffer was not fully captured (e.g. its cut removed content not present in this compose) is
     * left as a placeholder, to be resolved when composed onto the missing content.
     */
    private fun expandPastes(
        builder: NablaBuilder,
        buffers: Map<BufferId, List<Operation>>,
    ): NablaBuilder {
        if (builder.ops.none { it is Insert && it.element is BufferElement }) {
            return builder
        }
        val out = NablaBuilder()
        for (op in builder.ops) {
            val element = (op as? Insert)?.element
            val captured = if (element is BufferElement) buffers[element.bufferId] else null
            if (element is BufferElement && captured != null &&
                captured.sumOf { it.length } == element.length
            ) {
                captured.forEach(out::push)
            } else {
                out.push(op)
            }
        }
        return out
    }
}
