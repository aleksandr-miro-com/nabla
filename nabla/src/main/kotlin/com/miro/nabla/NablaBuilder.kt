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

    fun retain(length: Int, attributes: AttributeMap = AttributeMap.empty()): NablaBuilder {
        require(length >= 0) { "length must be non-negative" }
        if (length == 0) {
            return this
        }
        return push(Retain(length, attributes))
    }

    fun push(op: Operation): NablaBuilder {
        var index = _ops.size
        var lastOp = _ops.getOrNull(index - 1)

        if (op is Delete && lastOp is Delete) {
            _ops[index - 1] = Delete(lastOp.length + op.length)
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
        } else if (op is Retain && lastOp is Retain && op.attributes == lastOp.attributes) {
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
}
