package com.miro.nabla

import kotlin.math.min

class NablaTransformer {

    fun transform(a: NablaBuilder, b: NablaBuilder, priority: Boolean = false): NablaBuilder {
        val thisIter = OperationIterator(a.ops)
        val otherIter = OperationIterator(b.ops)
        val builder = NablaBuilder()

        while (thisIter.hasNext() || otherIter.hasNext()) {
            if (thisIter.peek() is Insert && (priority || otherIter.peek() !is Insert)) {
                builder.retain(thisIter.next().length)
            } else if (otherIter.peek() is Insert) {
                builder.push(otherIter.next())
            } else {
                val length = min(
                    if (thisIter.hasNext()) thisIter.peekLength() else Int.MAX_VALUE,
                    if (otherIter.hasNext()) otherIter.peekLength() else Int.MAX_VALUE,
                )
                // A `null` op stands for an exhausted side, i.e. an implicit attribute-less retain.
                val thisOp = if (thisIter.hasNext()) thisIter.next(length) else null
                val otherOp = if (otherIter.hasNext()) otherIter.next(length) else null

                if (thisOp is Delete) {
                    // Our delete makes their delete redundant or removes their retain.
                    continue
                } else if (otherOp is Delete) {
                    builder.push(otherOp)
                } else {
                    // We retain over their retain, transforming the attributes.
                    builder.retain(
                        length,
                        AttributeMap.transform(attributesOf(thisOp), attributesOf(otherOp), priority),
                    )
                }
            }
        }

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
