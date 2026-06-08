package com.miro.nabla

/**
 * Identity that links a "cut" ([Delete] with a [bufferId]) to the "paste" ([Insert] of a
 * [BufferElement]) that re-introduces its content elsewhere, turning the pair into a single move.
 *
 * The id only needs to be globally unique and totally ordered (the ordering breaks ties between
 * concurrent moves of the same range). How uniqueness is produced is the caller's concern.
 */
data class BufferId(val value: Long) : Comparable<BufferId> {
    override fun compareTo(other: BufferId): Int = value.compareTo(other.value)
}
