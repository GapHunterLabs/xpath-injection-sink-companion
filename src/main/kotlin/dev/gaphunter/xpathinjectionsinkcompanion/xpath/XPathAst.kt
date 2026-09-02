package dev.gaphunter.xpathinjectionsinkcompanion.xpath

/**
 * A hand-built AST for a real, useful subset of XPath 1.0 -- the
 * THIRD full custom grammar in this catalog (after the regex grammar
 * in ReDoS Catastrophic-Backtracking Companion and the SpEL grammar in
 * SpEL Injection Sink Companion).
 */
sealed class XPathNode

data class XPathLiteral(val text: String) : XPathNode()
data class XPathNumber(val text: String) : XPathNode()

/** `$name` -- an XPath variable reference. */
data class XPathVariableRef(val name: String) : XPathNode()

/** `name(args)` -- covers both node-type tests (`text()`, `node()`, `comment()`) and general functions (`contains(...)`, `starts-with(...)`). */
data class XPathFunctionCall(val name: String, val args: List<XPathNode>) : XPathNode()

/** One step of a location path: `.`/`..`/`@name`/`name`/`*`, each with zero or more `[predicate]` filters. */
data class XPathStep(val kind: StepKind, val name: String?, val predicates: List<XPathNode>) : XPathNode() {
    enum class StepKind { SELF, PARENT, ATTRIBUTE, NAMED, WILDCARD, FUNCTION_TEST }
}

/** A real location path: `/a/b[@id='x']//c`, or a relative one without the leading `/`. */
data class XPathLocationPath(val isAbsolute: Boolean, val steps: List<XPathStep>) : XPathNode()

data class XPathUnary(val operator: String, val operand: XPathNode) : XPathNode()
data class XPathBinary(val left: XPathNode, val operator: String, val right: XPathNode) : XPathNode()
data class XPathGrouping(val inner: XPathNode) : XPathNode()
