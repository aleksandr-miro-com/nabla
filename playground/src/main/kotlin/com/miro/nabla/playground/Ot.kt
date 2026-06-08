package com.miro.nabla.playground

import com.miro.nabla.NablaBuilder
import com.miro.nabla.NablaComposer
import com.miro.nabla.NablaTransformer

/**
 * Thin operational-transformation facade over the nabla library, used by the sync engine.
 */
object Ot {
    private val composer = NablaComposer()
    private val transformer = NablaTransformer()

    /** Applies change [b] on top of document/delta [a]. */
    fun compose(a: NablaBuilder, b: NablaBuilder): NablaBuilder = composer.compose(a, b)

    /**
     * Transforms two concurrent changes [a] and [b] (both based on the same document) against each
     * other and returns `(aPrime, bPrime)` such that `a.compose(bPrime) == b.compose(aPrime)`.
     *
     * Ties (e.g. concurrent inserts at the same index) are broken in favour of [a].
     */
    fun transformPair(a: NablaBuilder, b: NablaBuilder): Pair<NablaBuilder, NablaBuilder> {
        val aPrime = transformer.transform(b, a, priority = false) // a rebased after b
        val bPrime = transformer.transform(a, b, priority = true)  // b rebased after a
        return aPrime to bPrime
    }

    /**
     * Maps a caret [index] through [delta] (an applied change). The local caret keeps priority, so a
     * remote insert exactly at the caret leaves it in place rather than shoving it right.
     */
    fun transformPosition(delta: NablaBuilder, index: Int): Int =
        transformer.transformPosition(delta, index, priority = true)
}
