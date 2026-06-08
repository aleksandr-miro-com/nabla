package com.miro.nabla

class NablaBuilder() {
    private val _ops = mutableListOf<Operation>()

    val ops: List<Operation> get() = _ops

    constructor(ops: List<Operation>) : this() {
        _ops.addAll(ops)
    }

    constructor(other: NablaBuilder) : this(other.ops)

    fun insert(element: Element, attributes: AttributeMap = AttributeMap.empty()): NablaBuilder {
        require(element.length > 0)
        return push(Insert(element, attributes))
    }

    fun delete(length: Int): NablaBuilder {
        require(length >= 0) { "length must be non-negative" }
        if (length == 0) {
            return this
        }
        return push(Delete(length))
    }

    fun retain(
        length: Int,
        attributes: AttributeMap = AttributeMap.empty(),
        child: NablaBuilder? = null,
    ): NablaBuilder {
        require(length >= 0) { "length must be non-negative" }
        if (length == 0) {
            return this
        }
        return push(Retain(length, attributes, child))
    }

    /** A "cut": deletes [length] characters whose content is carried to a paste with [bufferId]. */
    fun cut(length: Int, bufferId: BufferId): NablaBuilder {
        require(length > 0) { "length must be positive" }
        return push(Delete(length, bufferId))
    }

    /** A "paste": re-inserts the [length] characters removed by the cut sharing [bufferId]. */
    fun paste(
        length: Int,
        bufferId: BufferId,
        attributes: AttributeMap = AttributeMap.empty(),
    ): NablaBuilder {
        require(length > 0) { "length must be positive" }
        return push(Insert(BufferElement(bufferId, length), attributes))
    }

    fun push(op: Operation): NablaBuilder {
        var index = _ops.size
        var lastOp = _ops.getOrNull(index - 1)

        // Deletes coalesce only when they share a buffer id: plain deletes (both null) merge, and the
        // two halves of one cut split by a transform re-merge; distinct cuts stay distinct.
        if (op is Delete && lastOp is Delete && op.bufferId == lastOp.bufferId) {
            _ops[index - 1] = Delete(lastOp.length + op.length, op.bufferId)
            return this
        }

        // Since it does not matter whether we insert before or delete after at the same index,
        // always prefer to insert first.
        if (lastOp is Delete && op is Insert) {
            index -= 1
            lastOp = _ops.getOrNull(index - 1)
            if (lastOp == null) {
                _ops.add(0, op)
                return this
            }
        }

        if (op is Insert && lastOp is Insert) {
            val merged = lastOp.merge(op)
            if (merged != null) {
                _ops[index - 1] = merged
                return this
            }
        } else if (op is Retain && lastOp is Retain && op.attributes == lastOp.attributes &&
            op.child == null && lastOp.child == null
        ) {
            _ops[index - 1] = Retain(lastOp.length + op.length, op.attributes)
            return this
        }

        if (index == _ops.size) {
            _ops.add(op)
        } else {
            _ops.add(index, op)
        }
        return this
    }

    override fun toString(): String {
        return "NablaBuilder(_ops=$_ops, ops=$ops)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NablaBuilder

        return _ops == other._ops
    }

    override fun hashCode(): Int {
        return _ops.hashCode()
    }
}
