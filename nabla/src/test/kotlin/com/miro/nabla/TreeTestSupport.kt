package com.miro.nabla

/** A tree node with [label] and optional children. */
fun node(label: String, children: NablaBuilder = NablaBuilder()) = NodeTestElement(label, children)

/** Renders a tree to e.g. `r[a:red,b]` — label, bracketed children, optional `:color`. */
fun renderTree(builder: NablaBuilder): String = builder.ops.joinToString(",") { op ->
    op as Insert
    val element = op.element as NodeTestElement
    val color = op.attributes["color"]?.let { ":$it" } ?: ""
    val children = if (element.children.ops.isEmpty()) "" else "[${renderTree(element.children)}]"
    "${element.label}$children$color"
}
