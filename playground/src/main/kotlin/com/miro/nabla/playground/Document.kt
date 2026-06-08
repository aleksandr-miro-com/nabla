package com.miro.nabla.playground

import com.miro.nabla.BufferId
import com.miro.nabla.Delete
import com.miro.nabla.Element
import com.miro.nabla.Insert
import com.miro.nabla.NablaBuilder

/** A run of text inside an [Insert]. The playground only deals with plain-text documents. */
data class TextElement(val text: String) : Element {
    init {
        require(text.isNotEmpty()) { "text element must be non-empty" }
    }

    override val length: Int get() = text.length

    override fun subElement(offset: Int, length: Int): Element =
        TextElement(text.substring(offset, offset + length))

    override fun merge(other: Element): Element? =
        if (other is TextElement) TextElement(text + other.text) else null
}

/** Renders a document delta (a sequence of text inserts) back to a plain string. */
fun NablaBuilder.text(): String = buildString {
    for (op in ops) {
        if (op is Insert) {
            val element = op.element
            if (element is TextElement) append(element.text)
        }
    }
}

/** A change that inserts [text] at character [at]. */
fun insertChange(at: Int, text: String): NablaBuilder =
    NablaBuilder().retain(at).insert(TextElement(text))

/** A change that deletes [count] characters starting at [at]. */
fun deleteChange(at: Int, count: Int): NablaBuilder =
    NablaBuilder().retain(at).delete(count)

/** A change that replaces `[start, end)` with [text] (either side may be empty). */
fun replaceChange(start: Int, end: Int, text: String): NablaBuilder {
    val change = NablaBuilder().retain(start)
    if (end > start) change.delete(end - start)
    if (text.isNotEmpty()) change.insert(TextElement(text))
    return change
}

/**
 * A *single* change that moves the selection `[start, end)` to [dropAt], as a linked cut + paste
 * sharing [bufferId]. Unlike a plain delete + re-insert of a text snapshot, this is a first-class
 * move: the composer resolves the paste from whatever the cut removes, and the transformer carries
 * concurrent edits to the moved range along with it. Requires [dropAt] to fall outside `[start, end)`.
 */
fun moveChange(start: Int, end: Int, dropAt: Int, bufferId: BufferId): NablaBuilder {
    val length = end - start
    val change = NablaBuilder()
    if (dropAt <= start) {
        change.retain(dropAt).paste(length, bufferId)
        change.retain(start - dropAt).cut(length, bufferId)
    } else {
        change.retain(start).cut(length, bufferId)
        change.retain(dropAt - end).paste(length, bufferId)
    }
    return change
}

/** Whether a change actually inserts or deletes content (vs. a no-op retain). */
fun NablaBuilder.hasEffect(): Boolean = ops.any { it is Insert || it is Delete }

/**
 * Derives a change turning [old] into [new] as a single contiguous edit (common-prefix /
 * common-suffix diff). Returns `null` when the strings are equal.
 */
fun diffChange(old: String, new: String): NablaBuilder? {
    if (old == new) return null

    var prefix = 0
    val maxPrefix = minOf(old.length, new.length)
    while (prefix < maxPrefix && old[prefix] == new[prefix]) prefix++

    var suffix = 0
    val maxSuffix = minOf(old.length, new.length) - prefix
    while (suffix < maxSuffix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++

    val deleted = old.length - prefix - suffix
    val inserted = new.substring(prefix, new.length - suffix)

    val change = NablaBuilder().retain(prefix)
    if (deleted > 0) change.delete(deleted)
    if (inserted.isNotEmpty()) change.insert(TextElement(inserted))
    return change
}
