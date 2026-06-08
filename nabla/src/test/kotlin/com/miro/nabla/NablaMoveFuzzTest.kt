package com.miro.nabla

import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Randomized convergence (TP1) oracle for moves. Each case builds a base document, a random move,
 * and a random plain edit, then asserts that the two apply-orders converge:
 *
 *     doc ∘ a ∘ transform(a, b)  ==  doc ∘ b ∘ transform(b, a)
 *
 * The plain edit is currently constrained to NOT intersect the moved range; this locks in the v1
 * guarantee. The intersecting variants (partial delete, insert-travels, overlapping moves) become
 * additional generators once the redirect/chase engine lands.
 */
class NablaMoveFuzzTest {

    private val composer = NablaComposer()
    private val transformer = NablaTransformer()

    @Test
    fun `moves converge with disjoint edits across many random cases`() {
        var checked = 0
        for (seed in 0 until 1000) {
            val rng = Random(seed.toLong())
            val n = 4 + rng.nextInt(8)
            val base = (0 until n).map { ('a' + rng.nextInt(26)) }.joinToString("")

            val start = rng.nextInt(n - 1)
            val end = start + 1 + rng.nextInt(n - start)
            val dropChoices = (0 until start) + ((end + 1)..n)
            if (dropChoices.isEmpty()) continue
            val dropAt = dropChoices[rng.nextInt(dropChoices.size)]
            val length = end - start
            val id = BufferId(1)
            val move = if (dropAt <= start) {
                NablaBuilder().retain(dropAt).paste(length, id).retain(start - dropAt).cut(length, id)
            } else {
                NablaBuilder().retain(start).cut(length, id).retain(dropAt - end).paste(length, id)
            }

            val edit = disjointEdit(rng, base, start, end) ?: continue

            assertConverges(base, move, edit, seed)
            checked++
        }
        assertTrue(checked > 200, "expected a healthy number of generated cases, got $checked")
    }

    @Test
    fun `moves converge with a delete intersecting the range across many random cases`() {
        var checked = 0
        for (seed in 0 until 1000) {
            val rng = Random(seed.toLong())
            val n = 4 + rng.nextInt(8)
            val base = (0 until n).map { ('a' + rng.nextInt(26)) }.joinToString("")

            val start = rng.nextInt(n - 1)
            val end = start + 1 + rng.nextInt(n - start)
            val dropChoices = (0 until start) + ((end + 1)..n)
            if (dropChoices.isEmpty()) continue
            val dropAt = dropChoices[rng.nextInt(dropChoices.size)]
            val length = end - start
            val id = BufferId(1)
            val move = if (dropAt <= start) {
                NablaBuilder().retain(dropAt).paste(length, id).retain(start - dropAt).cut(length, id)
            } else {
                NablaBuilder().retain(start).cut(length, id).retain(dropAt - end).paste(length, id)
            }

            // A delete of a random span anywhere, free to overlap (or sit inside) the moved range.
            val c = rng.nextInt(n)
            val deleteLength = 1 + rng.nextInt(n - c)
            val delete = NablaBuilder().retain(c).delete(deleteLength).retain(n - c - deleteLength)

            assertConverges(base, move, delete, seed)
            checked++
        }
        assertTrue(checked > 200, "expected a healthy number of generated cases, got $checked")
    }

    /** A plain insert/delete that never touches the open moved range `(start, end)`. */
    private fun disjointEdit(rng: Random, base: String, start: Int, end: Int): NablaBuilder? {
        val n = base.length
        return if (rng.nextBoolean()) {
            val positions = (0..start) + (end..n)
            val p = positions[rng.nextInt(positions.size)]
            NablaBuilder().retain(p).insert(StringElement("X")).retain(n - p)
        } else {
            val indices = (0 until start) + (end until n)
            if (indices.isEmpty()) return null
            val c = indices[rng.nextInt(indices.size)]
            NablaBuilder().retain(c).delete(1).retain(n - c - 1)
        }
    }

    private fun assertConverges(base: String, a: NablaBuilder, b: NablaBuilder, seed: Int) {
        val doc = NablaBuilder().insert(StringElement(base))
        val aPrime = transformer.transform(b, a, priority = false)
        val bPrime = transformer.transform(a, b, priority = true)
        val left = plainText(composer.compose(composer.compose(doc, a), bPrime))
        val right = plainText(composer.compose(composer.compose(doc, b), aPrime))
        assertEquals(right, left, "diverged: seed=$seed base=$base a=${a.ops} b=${b.ops}")
    }
}
