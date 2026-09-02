package dev.gaphunter.xpathinjectionsinkcompanion.detect

import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiPolyadicExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import dev.gaphunter.xpathinjectionsinkcompanion.model.XPathSinkHit
import dev.gaphunter.xpathinjectionsinkcompanion.xpath.XPathParser

/**
 * Finds `xpath.evaluate(ARG)`/`xpath.compile(ARG)` call sites (where
 * `xpath`'s declared type mentions `XPath`, or the qualifier chains
 * through `XPathFactory....newXPath()`) whose ARG is built (same
 * method, direct reference or one-hop concatenation) from an HTTP
 * endpoint parameter -- CWE-643. The attacker's raw string becomes
 * XPath SOURCE TEXT re-parsed by the XPath engine, exactly the OWASP
 * Testing Guide's documented XPath Injection mechanism (same "taint
 * alone is the vulnerability" reasoning as this catalog's SpEL
 * Injection Sink Companion -- the attacker controls the substituted
 * text's shape, so no AST-danger-shape gate would be sound).
 *
 * **The grammar's real job here:** reconstruct the argument's STATIC
 * skeleton (every tainted/non-constant operand replaced by a fixed
 * placeholder) and confirm it parses as well-formed XPath via
 * [XPathParser] -- this is a NOISE-REDUCTION check, never a security
 * gate: it only skips a `.evaluate(...)` call whose argument clearly
 * isn't shaped like real XPath source text at all (e.g. an unrelated
 * string coincidentally reaching a same-named method), it never
 * suppresses a real injection once the skeleton IS valid XPath.
 *
 * **v0.1 scope, stated honestly:** only Java; only same-method taint;
 * `looksLikeXPath` is a declared-type-TEXT heuristic (or a
 * `newXPath()` call chain), never resolved against the real
 * `javax.xml.xpath` classpath.
 */
object JavaXPathInjectionSinkFinder {

    private val SINK_METHOD_NAMES = setOf("evaluate", "compile")

    fun findAll(file: PsiFile): List<XPathSinkHit> {
        val hits = mutableListOf<XPathSinkHit>()
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)
                if (!ControllerEndpointSignals.isEndpointMethod(method)) return
                hits += hitsForMethod(method)
            }
        })
        return hits
    }

    private fun hitsForMethod(method: PsiMethod): List<XPathSinkHit> {
        val body = method.body ?: return emptyList()
        val taintedNames = method.parameterList.parameters.map { it.name }.toSet()
        if (taintedNames.isEmpty()) return emptyList()

        val hits = mutableListOf<XPathSinkHit>()
        body.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                super.visitMethodCallExpression(call)
                if (call.methodExpression.referenceName !in SINK_METHOD_NAMES) return
                if (!looksLikeXPath(call.methodExpression.qualifierExpression)) return
                val argument = call.argumentList.expressions.getOrNull(0) ?: return

                val taintedName = firstTaintedReference(argument, taintedNames) ?: return
                val skeleton = buildTaintSkeleton(argument) ?: return
                if (XPathParser.parse(skeleton) == null) return

                val anchor = call.methodExpression.referenceNameElement ?: call.methodExpression
                hits += XPathSinkHit(anchor, taintedName)
            }
        })
        return hits
    }

    private fun firstTaintedReference(expression: PsiElement, taintedNames: Set<String>): String? {
        var found: String? = null
        expression.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitReferenceExpression(expr: PsiReferenceExpression) {
                if (found != null) return
                super.visitReferenceExpression(expr)
                val name = expr.referenceName
                if (name != null && name in taintedNames) found = name
            }
        })
        return found
    }

    /** See class doc -- reconstructs a validation-only skeleton, never guessing on a shape it doesn't recognize (a literal, a bare reference, or a `+` concatenation of those). */
    private fun buildTaintSkeleton(expression: PsiExpression): String? = when (expression) {
        is PsiLiteralExpression -> (expression.value as? String) ?: PLACEHOLDER
        is PsiReferenceExpression -> PLACEHOLDER
        is PsiPolyadicExpression -> {
            if (expression.operationTokenType != JavaTokenType.PLUS) {
                null
            } else {
                val parts = expression.operands.map { operand -> (operand as? PsiLiteralExpression)?.value as? String ?: PLACEHOLDER }
                parts.joinToString("")
            }
        }
        else -> PLACEHOLDER
    }

    private const val PLACEHOLDER = "PLACEHOLDER"

    private fun looksLikeXPath(qualifier: PsiExpression?): Boolean {
        if (qualifier is PsiMethodCallExpression) {
            if (qualifier.methodExpression.referenceName == "newXPath") return true
            return looksLikeXPath(qualifier.methodExpression.qualifierExpression)
        }
        val resolved = (qualifier as? PsiReferenceExpression)?.resolve() as? PsiVariable ?: return false
        return resolved.type.presentableText.contains("XPath")
    }
}
