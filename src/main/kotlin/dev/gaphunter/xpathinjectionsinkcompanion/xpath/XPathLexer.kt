package dev.gaphunter.xpathinjectionsinkcompanion.xpath

enum class XPathTokenType {
    IDENTIFIER, NUMBER, STRING, DOT, DOTDOT, SLASH, DOUBLE_SLASH, AT,
    LBRACKET, RBRACKET, LPAREN, RPAREN, COMMA, DOLLAR, STAR, PIPE, OP, EOF,
}

data class XPathToken(val type: XPathTokenType, val text: String)

/**
 * Hand-rolled tokenizer for the XPath 1.0 subset [XPathParser]
 * consumes. Returns null (never a partial token list) on any
 * unrecognized character or unterminated string -- same "never guess"
 * discipline as this catalog's other hand-rolled lexers.
 *
 * **NCName lexing, stated honestly:** a hyphen continues an
 * identifier only when immediately followed by a letter/underscore
 * (`starts-with` lexes as one identifier) -- a hyphen followed by a
 * digit or whitespace is the subtraction/unary-minus operator instead
 * (`a-1` lexes as `a`, `-`, `1`). Real XPath NCNames can also contain
 * `.` and digits after the first letter; this lexer's v0.1 subset
 * supports letters/digits/underscore plus that one hyphen rule, which
 * covers every real function name and element/attribute name this
 * plugin's own test suite and realistic Java-embedded XPath strings
 * use.
 */
object XPathLexer {

    private val OPERATOR_CHARS = "+-<>=!".toSet()

    fun tokenize(input: String): List<XPathToken>? {
        val tokens = mutableListOf<XPathToken>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c == '/' && i + 1 < input.length && input[i + 1] == '/' -> { tokens += XPathToken(XPathTokenType.DOUBLE_SLASH, "//"); i += 2 }
                c == '/' -> { tokens += XPathToken(XPathTokenType.SLASH, "/"); i++ }
                c == '.' && i + 1 < input.length && input[i + 1] == '.' -> { tokens += XPathToken(XPathTokenType.DOTDOT, ".."); i += 2 }
                c == '.' && i + 1 < input.length && input[i + 1].isDigit() -> {
                    val end = scanNumber(input, i)
                    tokens += XPathToken(XPathTokenType.NUMBER, input.substring(i, end))
                    i = end
                }
                c == '.' -> { tokens += XPathToken(XPathTokenType.DOT, "."); i++ }
                c == '@' -> { tokens += XPathToken(XPathTokenType.AT, "@"); i++ }
                c == '[' -> { tokens += XPathToken(XPathTokenType.LBRACKET, "["); i++ }
                c == ']' -> { tokens += XPathToken(XPathTokenType.RBRACKET, "]"); i++ }
                c == '(' -> { tokens += XPathToken(XPathTokenType.LPAREN, "("); i++ }
                c == ')' -> { tokens += XPathToken(XPathTokenType.RPAREN, ")"); i++ }
                c == ',' -> { tokens += XPathToken(XPathTokenType.COMMA, ","); i++ }
                c == '$' -> { tokens += XPathToken(XPathTokenType.DOLLAR, "$"); i++ }
                c == '*' -> { tokens += XPathToken(XPathTokenType.STAR, "*"); i++ }
                c == '|' -> { tokens += XPathToken(XPathTokenType.PIPE, "|"); i++ }
                c == '\'' || c == '"' -> {
                    val result = readStringLiteral(input, i, c) ?: return null
                    tokens += XPathToken(XPathTokenType.STRING, result.first)
                    i = result.second
                }
                c.isDigit() -> {
                    val end = scanNumber(input, i)
                    tokens += XPathToken(XPathTokenType.NUMBER, input.substring(i, end))
                    i = end
                }
                c.isLetter() || c == '_' -> {
                    val end = scanIdentifier(input, i)
                    tokens += XPathToken(XPathTokenType.IDENTIFIER, input.substring(i, end))
                    i = end
                }
                c in OPERATOR_CHARS -> {
                    val twoChar = if (i + 1 < input.length) input.substring(i, i + 2) else ""
                    if (twoChar == "<=" || twoChar == ">=" || twoChar == "!=") {
                        tokens += XPathToken(XPathTokenType.OP, twoChar)
                        i += 2
                    } else if (c == '!') {
                        return null // a lone '!' is not valid XPath 1.0 -- never guess
                    } else {
                        tokens += XPathToken(XPathTokenType.OP, c.toString())
                        i++
                    }
                }
                else -> return null
            }
        }
        tokens += XPathToken(XPathTokenType.EOF, "")
        return tokens
    }

    private fun readStringLiteral(input: String, openIndex: Int, quote: Char): Pair<String, Int>? {
        var i = openIndex + 1
        val sb = StringBuilder()
        while (i < input.length) {
            if (input[i] == quote) return sb.toString() to (i + 1)
            sb.append(input[i])
            i++
        }
        return null
    }

    private fun scanNumber(input: String, start: Int): Int {
        var i = start
        while (i < input.length && input[i].isDigit()) i++
        if (i < input.length && input[i] == '.') {
            i++
            while (i < input.length && input[i].isDigit()) i++
        }
        return i
    }

    /** See class doc for the hyphen-continuation rule. */
    private fun scanIdentifier(input: String, start: Int): Int {
        var i = start + 1
        while (i < input.length) {
            val c = input[i]
            if (c.isLetterOrDigit() || c == '_') {
                i++
                continue
            }
            if (c == '-' && i + 1 < input.length && (input[i + 1].isLetter() || input[i + 1] == '_')) {
                i += 2
                continue
            }
            break
        }
        return i
    }
}
