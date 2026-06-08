package com.miro.nabla

import kotlin.math.max

interface AttributeMap {
    val size: Int

    val keys: Set<String>
    val values: Collection<Any?>
    val entries: Set<Map.Entry<String, Any?>>

    val isEmpty: Boolean

    operator fun get(key: String): Any?

    operator fun contains(key: String): Boolean

    companion object {
        fun empty(): AttributeMap = EmptyAttributeMap

        fun of(pair: Pair<String, Any?>): AttributeMap {
            return NonEmptyAttributeMap(mapOf(pair))
        }

        fun of(vararg pairs: Pair<String, Any?>): AttributeMap {
            return if (pairs.isNotEmpty()) NonEmptyAttributeMap(mapOf(*pairs)) else EmptyAttributeMap
        }

        private data object EmptyAttributeMap : AttributeMap {
            override val size: Int get() = 0

            override val keys: Set<String> get() = emptySet()
            override val values: Collection<Any?> get() = emptyList()
            override val entries: Set<Map.Entry<String, Any?>> get() = emptySet()

            override val isEmpty: Boolean get() = true

            override fun get(key: String): Any? = null

            override fun contains(key: String): Boolean = false
        }

        private data class NonEmptyAttributeMap(private val attributes: Map<String, Any?> = emptyMap()) : AttributeMap {
            init {
                require(attributes.isNotEmpty())
            }

            override val size: Int get() = attributes.size

            override val keys: Set<String> get() = attributes.keys
            override val values: Collection<Any?> = attributes.values
            override val entries: Set<Map.Entry<String, Any?>> get() = attributes.entries

            override val isEmpty: Boolean get() = attributes.isEmpty()

            override operator fun get(key: String): Any? = attributes[key]

            override operator fun contains(key: String): Boolean = attributes.containsKey(key)
        }

        fun compose(
            target: AttributeMap,
            source: AttributeMap,
            keepNull: Boolean = false,
        ): AttributeMap {
            val result: MutableMap<String, Any?> = if (source.isEmpty) {
                if (target.isEmpty || keepNull || target.values.none { it == null }) {
                    return target
                }
                LinkedHashMap(target.size)
            } else {
                if (target.isEmpty && (keepNull || source.values.none { it == null })) {
                    return source
                }
                LinkedHashMap(max(target.size, source.size))
            }
            for (entry in target.entries) {
                if (keepNull || entry.value != null) {
                    result[entry.key] = entry.value
                }
            }
            for (entry in source.entries) {
                if (keepNull || entry.value != null) {
                    result[entry.key] = entry.value
                } else {
                    result.remove(entry.key)
                }
            }
            return if (result.isEmpty()) EmptyAttributeMap else NonEmptyAttributeMap(result)
        }

        fun diff(source: AttributeMap, target: AttributeMap): AttributeMap {
            if (source.isEmpty) {
                return target
            }
            val result = LinkedHashMap<String, Any?>()
            for (entry in target.entries) {
                if (entry.value != source[entry.key]) {
                    result[entry.key] = entry.value
                }
            }
            for (key in source.keys) {
                if (!target.contains(key)) {
                    result[key] = null
                }
            }
            if (result.isEmpty()) {
                // maps are effectively equal
                return EmptyAttributeMap
            }
            return NonEmptyAttributeMap(result)
        }

        fun invert(attributes: AttributeMap, base: AttributeMap): AttributeMap {
            if (attributes.isEmpty) {
                return EmptyAttributeMap
            }
            val result = LinkedHashMap<String, Any?>(attributes.size)
            for (entry in attributes.entries) {
                val baseValue = base[entry.key]
                if (entry.value != baseValue) {
                    if (!base.contains(entry.key)) {
                        result[entry.key] = null
                    } else {
                        result[entry.key] = baseValue
                    }
                }
            }
            if (result.isEmpty()) {
                // attributes match
                return EmptyAttributeMap
            }
            return NonEmptyAttributeMap(result)
        }

        fun transform(
            attributes: AttributeMap,
            target: AttributeMap,
            attributesPriority: Boolean = false,
        ): AttributeMap {
            if (attributes.isEmpty || target.isEmpty || !attributesPriority) {
                return target
            }
            val result = LinkedHashMap<String, Any?>(target.size)
            for (entry in target.entries) {
                if (!attributes.contains(entry.key)) {
                    result[entry.key] = entry.value // null is a valid value
                }
            }
            return if (result.isEmpty()) EmptyAttributeMap else NonEmptyAttributeMap(result)
        }
    }
}
