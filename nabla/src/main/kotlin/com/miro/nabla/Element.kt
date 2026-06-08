package com.miro.nabla

interface Element {
    val length: Int

    fun subElement(offset: Int, length: Int): Element

    /**
     * Combines this element with [other], returning the merged element, or `null` when the two
     * cannot be merged (e.g. embeds). The inverse of [subElement].
     */
    fun merge(other: Element): Element? = null

    /**
     * The nested document this element contains, or `null` for a leaf. A non-null value makes the
     * element a parent: composing/transforming a change that retains it recurses into [children].
     */
    val children: NablaBuilder? get() = null

    /** Returns a copy of this element with its [children] replaced; meaningless for leaves. */
    fun withChildren(children: NablaBuilder): Element = this
}
