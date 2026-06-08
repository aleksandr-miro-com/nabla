package com.miro.nabla

internal class OperationIterator(private val ops: List<Operation>) : Iterator<Operation> {
    private var index = 0
    private var offset = 0

    override fun hasNext(): Boolean {
        val op = ops.getOrNull(index) ?: return false

        return offset < op.length
    }

    override fun next(): Operation = advance()

    fun next(limit: Int): Operation = advance(limit)

    fun peek(): Operation? = ops.getOrNull(index)

    /** Remaining length of the current op, or [Int.MAX_VALUE] when the iterator is exhausted. */
    fun peekLength(): Int {
        val op = ops.getOrNull(index) ?: throw NoSuchElementException()
        return op.length - offset
    }

    private fun advance(limit: Int = Int.MAX_VALUE): Operation {
        require(limit >= 0) { "length must be non-negative" }
        val nextOp = ops.getOrNull(index) ?: throw NoSuchElementException()

        val offset = this.offset
        val opLength = nextOp.length - offset
        val length = if (limit >= opLength) {
            index += 1
            this.offset = 0
            opLength
        } else {
            this.offset += limit
            limit
        }

        if (length == opLength && offset == 0) {
            return nextOp
        }
        return nextOp.subOperation(offset, length)
    }

    fun remaining(): List<Operation> {
        if (!hasNext()) {
            return emptyList()
        }
        if (offset == 0) {
            return ops.subList(index, ops.size)
        }
        val size = ops.size - index

        val remaining = ArrayList<Operation>(size)

        val op = ops[index]

        val first = op.subOperation(offset, op.length - offset)
        remaining.add(first)

        val iterator = ops.listIterator(index + 1)
        while (iterator.hasNext()) {
            remaining.add(iterator.next())
        }
        return remaining
    }
}
