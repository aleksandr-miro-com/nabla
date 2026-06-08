package com.miro.nabla

interface Element {
    val length: Int

    fun subElement(offset: Int, length: Int): Element

    /**
     * Combines this element with [other], returning the merged element, or `null` when the two
     * cannot be merged (e.g. embeds). The inverse of [subElement].
     */
    fun merge(other: Element): Element? = null
}
