package dev.gaphunter.xpathinjectionsinkcompanion.xpath

import dev.gaphunter.xpathinjectionsinkcompanion.xpath.XPathTokenType.AT
import dev.gaphunter.xpathinjectionsinkcompanion.xpath.XPathTokenType.COMMA
import dev.gaphunter.xpathinjectionsinkcompanion.xpath.XPathTokenType.DOLLAR
import dev.gaphunter.xpathinjectionsinkcompanion.xpath.XPathTokenType.DOT
import dev.gaphunter.xpathinjectionsinkcompanion.xpath.XPathTokenType.DOTDOT
import dev.gaphunter.xpathinjectionsinkcompanion.xpath.XPathTokenType.DOUBLE_SLASH
import dev.gaphunter.xpathinjectionsinkcompanion.xpath.XPathTokenType.EOF
import dev.gaphunter.xpathinjectionsinkcompanion.xpath.XPathTokenType.IDENTIFIER
import dev.gaphunter.xpathinjectionsinkcompanion.xpath.XPathTokenType.LBRACKET
import dev.gaphunter.xpathinjectionsinkcompanion.xpath.XPathTokenType.LPAREN
import dev.gaphunter.xpathinjectionsinkcompanion.xpath.XPathTokenType.NUMBER
import dev.gaphunter.xpathinjectionsinkcompanion.xpath.XPathTokenType.OP
import dev.gaphunter.xpathinjectionsinkcompanion.xpath.XPathTokenType.PIPE
import dev.gaphunter.xpathinjectionsinkcompanion.xpath.XPathTokenType.RBRACKET
import dev.gaphunter.xpathinjectionsinkcompanion.xpath.XPathTokenType.RPAREN
import dev.gaphunter.xpathinjectionsinkcompanion.xpath.XPathTokenType.SLASH
import dev.gaphunter.xpathinjectionsinkcompanion.xpath.XPathTokenType.STAR
import dev.gaphunter.xpathinjectionsinkcompanion.xpath.XPathTokenType.STRING

/**
 * Recursive-descent parser over [XPathLexer]'s tokens. Flat on binary
 * operators (no precedence climbing) -- same reasoning as this
 * catalog's SpEL grammar: this plugin never EVALUATES an expression,
 * only confirms it PARSES as well-formed XPath (used to validate a
 * taint skeleton, see `JavaXPathInjectionSinkFinder`), so operand
 * precedence doesn't change that answer.
 *
 * Every `parseXxx` returns null (never a best-effort partial tree) on
 * a mismatch; [parse] treats that, or trailing unconsumed input, as
 * unparseable.
 */
class XPathParser private constructor(private val tokens: List<XPathToken>) {

    private var pos = 0

    companion object {
        private val KEYWORD_OPERATORS = setOf("and", "or", "div", "mod")
        private val NODE_TYPE_TESTS = setOf("text", "node", "comment", "processing-instruction")

        fun parse(text: String): XPathNode? {
            val tokens = XPathLexer.tokenize(text) ?: return null
            val parser = XPathParser(tokens)
            val node = parser.parseExpr() ?: return null
            return if (parser.check(EOF)) node else null
        }
    }

    private fun peek(): XPathToken = tokens[pos]
    private fun advance(): XPathToken = tokens[pos++]
    private fun check(type: XPathTokenType): Boolean = peek().type == type
    private fun match(type: XPathTokenType): XPathToken? = if (check(type)) advance() else null

    private fun parseExpr(): XPathNode? {
        var left = parseUnary() ?: return null
        while (isOperatorToken()) {
            val operator = advance().text
            val right = parseUnary() ?: return null
            left = XPathBinary(left, operator, right)
        }
        return left
    }

    private fun isOperatorToken(): Boolean {
        if (check(OP) || check(PIPE)) return true
        return check(IDENTIFIER) && peek().text in KEYWORD_OPERATORS
    }

    private fun parseUnary(): XPathNode? {
        if (check(OP) && peek().text == "-") {
            advance()
            val operand = parseUnary() ?: return null
            return XPathUnary("-", operand)
        }
        return parsePrimary()
    }

    private fun parsePrimary(): XPathNode? = when {
        check(DOLLAR) -> {
            advance()
            match(IDENTIFIER)?.text?.let { XPathVariableRef(it) }
        }
        check(LPAREN) -> {
            advance()
            val inner = parseExpr()
            if (inner != null && match(RPAREN) != null) XPathGrouping(inner) else null
        }
        check(STRING) -> XPathLiteral(advance().text)
        check(NUMBER) -> XPathNumber(advance().text)
        check(SLASH) || check(DOUBLE_SLASH) -> {
            advance()
            parseLocationPath(isAbsolute = true)
        }
        check(DOT) || check(DOTDOT) || check(AT) || check(STAR) -> parseLocationPath(isAbsolute = false)
        check(IDENTIFIER) -> parseIdentifierLed()
        else -> null
    }

    /** An identifier can lead a function call (`contains(...)`) OR the first step of a relative location path (`user[...]`) -- disambiguated by a one-token lookahead for `(`, backing off cleanly if it turns out to be a step. */
    private fun parseIdentifierLed(): XPathNode? {
        if (peek().text in KEYWORD_OPERATORS) return null
        val savedPos = pos
        val name = advance().text
        if (check(LPAREN) && name !in NODE_TYPE_TESTS) {
            advance()
            val args = mutableListOf<XPathNode>()
            if (!check(RPAREN)) {
                while (true) {
                    args += parseExpr() ?: return null
                    if (match(COMMA) != null) continue
                    break
                }
            }
            return if (match(RPAREN) != null) XPathFunctionCall(name, args) else null
        }
        pos = savedPos
        return parseLocationPath(isAbsolute = false)
    }

    private fun parseLocationPath(isAbsolute: Boolean): XPathLocationPath? {
        val steps = mutableListOf<XPathStep>()
        if (canStartStep()) {
            steps += parseStep() ?: return null
            while (check(SLASH) || check(DOUBLE_SLASH)) {
                advance()
                steps += parseStep() ?: return null
            }
        }
        return XPathLocationPath(isAbsolute, steps)
    }

    private fun canStartStep(): Boolean = check(DOT) || check(DOTDOT) || check(AT) || check(STAR) || check(IDENTIFIER)

    private fun parseStep(): XPathStep? {
        val base = when {
            check(DOT) -> { advance(); XPathStep(XPathStep.StepKind.SELF, null, emptyList()) }
            check(DOTDOT) -> { advance(); XPathStep(XPathStep.StepKind.PARENT, null, emptyList()) }
            check(AT) -> {
                advance()
                val name = match(IDENTIFIER)?.text ?: (if (match(STAR) != null) "*" else return null)
                XPathStep(XPathStep.StepKind.ATTRIBUTE, name, emptyList())
            }
            check(STAR) -> { advance(); XPathStep(XPathStep.StepKind.WILDCARD, "*", emptyList()) }
            check(IDENTIFIER) -> {
                val name = advance().text
                if (check(LPAREN)) {
                    advance()
                    if (!check(RPAREN)) {
                        parseExpr() ?: return null // e.g. processing-instruction('x') -- one loosely-consumed argument
                    }
                    if (match(RPAREN) == null) return null
                    XPathStep(XPathStep.StepKind.FUNCTION_TEST, name, emptyList())
                } else {
                    XPathStep(XPathStep.StepKind.NAMED, name, emptyList())
                }
            }
            else -> return null
        }

        val predicates = mutableListOf<XPathNode>()
        while (check(LBRACKET)) {
            advance()
            predicates += parseExpr() ?: return null
            if (match(RBRACKET) == null) return null
        }
        return base.copy(predicates = predicates)
    }
}
