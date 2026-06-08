package com.miro.nabla

import kotlin.math.min

class NablaComposer {

    fun compose(a: NablaBuilder, b: NablaBuilder): NablaBuilder {
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
                } else if (otherOp is Delete && thisIsRetain) {
                    // Other op is a delete over our retain (or implicit retain): keep it.
                    // An insert + delete cancels out, so an insert `thisOp` is simply dropped.
                    builder.push(otherOp)
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
}
