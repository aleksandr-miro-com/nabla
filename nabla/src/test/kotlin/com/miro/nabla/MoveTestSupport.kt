package com.miro.nabla

/** Flattened text of a delta whose ops are all string inserts (pastes already resolved by compose). */
fun plainText(builder: NablaBuilder): String =
    builder.ops.joinToString("") { ((it as Insert).element as StringElement).value }
