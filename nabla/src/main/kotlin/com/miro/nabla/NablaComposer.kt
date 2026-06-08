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
        // simply skips over, instead of recomposing them op by op. A retain carrying a child edits
        // the element it passes over, so it must not be treated as a plain skip.
        val firstOther = otherIter.peek()
        if (firstOther is Retain && firstOther.attributes.isEmpty && firstOther.child == null) {
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
                    // A retain may carry a nested change for the element's children.
                    val childChange = (otherOp as? Retain)?.child
                    val newOp: Operation = if (thisOp is Insert) {
                        val element = thisOp.element
                        val composed = element.children?.takeIf { childChange != null }
                            ?.let { element.withChildren(composeOps(it, childChange!!, buffers)) }
                            ?: element
                        Insert(composed, attributes)
                    } else {
                        Retain(length, attributes, composeChildren((thisOp as? Retain)?.child, childChange, buffers))
                    }

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

    /** Composes two nested child changes, treating a `null` child as "no change". */
    private fun composeChildren(
        a: NablaBuilder?,
        b: NablaBuilder?,
        buffers: MutableMap<BufferId, MutableList<Operation>>,
    ): NablaBuilder? = when {
        a == null -> b
        b == null -> a
        else -> composeOps(a, b, buffers)
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
        if (last is Retain && last.attributes.isEmpty && last.child == null) {
            return NablaBuilder(builder.ops.dropLast(1))
        }
        return builder
    }

    /**
     * Replaces each resolved paste with the content its cut captured into [buffers], recursing into
     * nested children so a cut and a paste in different subtrees still link up (buffers are global).
     * A paste whose buffer was not fully captured is left as a placeholder, to be resolved when
     * composed onto the missing content.
     */
    private fun expandPastes(
        builder: NablaBuilder,
        buffers: Map<BufferId, List<Operation>>,
    ): NablaBuilder {
        val out = NablaBuilder()
        for (op in builder.ops) {
            when (op) {
                is Insert -> {
                    val element = op.element
                    val captured = if (element is BufferElement) buffers[element.bufferId] else null
                    when {
                        element is BufferElement && captured != null &&
                            captured.sumOf { it.length } == element.length ->
                            captured.forEach(out::push)
                        element.children != null ->
                            out.push(Insert(element.withChildren(expandPastes(element.children!!, buffers)), op.attributes))
                        else -> out.push(op)
                    }
                }
                is Retain ->
                    if (op.child != null) out.push(Retain(op.length, op.attributes, expandPastes(op.child, buffers)))
                    else out.push(op)
                is Delete -> out.push(op)
            }
        }
        return out
    }
}
