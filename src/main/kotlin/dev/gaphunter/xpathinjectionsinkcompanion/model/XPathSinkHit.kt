package dev.gaphunter.xpathinjectionsinkcompanion.model

import com.intellij.psi.PsiElement

/** A confirmed XPath injection sink: [taintedParameterName] flows (same method, direct reference or one-hop concatenation) into an `XPath.evaluate(...)`/`.compile(...)` argument whose static skeleton parses as well-formed XPath. */
data class XPathSinkHit(val anchor: PsiElement, val taintedParameterName: String)
