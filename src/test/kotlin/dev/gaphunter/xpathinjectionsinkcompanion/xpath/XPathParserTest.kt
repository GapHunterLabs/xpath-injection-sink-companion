package dev.gaphunter.xpathinjectionsinkcompanion.xpath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XPathParserTest {

    @Test
    fun `a simple absolute location path parses`() {
        val node = XPathParser.parse("/root/items/item") as XPathLocationPath
        assertTrue(node.isAbsolute)
        assertEquals(listOf("root", "items", "item"), node.steps.map { it.name })
    }

    @Test
    fun `a location path with a predicate comparing an attribute parses`() {
        val node = XPathParser.parse("//user[@name='admin']") as XPathLocationPath
        assertTrue(node.isAbsolute)
        val step = node.steps.single()
        assertEquals("user", step.name)
        val predicate = step.predicates.single() as XPathBinary
        assertEquals("=", predicate.operator)
    }

    @Test
    fun `a classic injection payload with or 1=1 parses as a well-formed binary chain`() {
        // The grammar is deliberately flat (no operator precedence --
        // see XPathParser's own doc: this plugin never evaluates an
        // expression, only confirms it parses, so which operator ends
        // up at the tree's root doesn't matter). What matters here is
        // that the whole classic injection payload parses at all
        // (confirming the skeleton-validation use case works on a
        // real payload shape) and that both real operators appear
        // somewhere in the resulting flat chain.
        val node = XPathParser.parse("//user[username='admin' or '1'='1']") as XPathLocationPath
        val predicate = node.steps.single().predicates.single() as XPathBinary
        val operatorsInChain = generateSequence(predicate as XPathNode?) { (it as? XPathBinary)?.left }
            .filterIsInstance<XPathBinary>()
            .map { it.operator }
            .toSet()
        assertEquals(setOf("or", "="), operatorsInChain)
    }

    @Test
    fun `function calls with string arguments parse, e_g_ contains and starts-with`() {
        val node = XPathParser.parse("//user[contains(name, 'adm')]") as XPathLocationPath
        val predicate = node.steps.single().predicates.single() as XPathFunctionCall
        assertEquals("contains", predicate.name)
        assertEquals(2, predicate.args.size)

        assertNotNull(XPathParser.parse("//user[starts-with(name, 'adm')]"))
    }

    @Test
    fun `node-type tests text and node parse as steps, not function calls`() {
        val node = XPathParser.parse("//user/text()") as XPathLocationPath
        assertEquals(XPathStep.StepKind.FUNCTION_TEST, node.steps.last().kind)
        assertEquals("text", node.steps.last().name)
    }

    @Test
    fun `a bare variable reference and root-only path parse`() {
        assertNotNull(XPathParser.parse("\$userId"))
        assertNotNull(XPathParser.parse("/"))
    }

    @Test
    fun `an unterminated predicate fails to parse`() {
        assertNull(XPathParser.parse("//user[@name='admin'"))
    }

    @Test
    fun `trailing garbage after a valid expression fails to parse`() {
        assertNull(XPathParser.parse("//user )"))
    }

    @Test
    fun `an unrecognized character fails to parse`() {
        assertNull(XPathParser.parse("//user[@name=~admin]"))
    }
}
