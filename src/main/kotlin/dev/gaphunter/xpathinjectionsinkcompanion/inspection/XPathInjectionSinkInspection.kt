package dev.gaphunter.xpathinjectionsinkcompanion.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile
import dev.gaphunter.xpathinjectionsinkcompanion.detect.JavaXPathInjectionSinkFinder
import dev.gaphunter.xpathinjectionsinkcompanion.model.XPathSinkHit
import dev.gaphunter.xpathinjectionsinkcompanion.review.ReviewPrompt

/** Flags an `XPath.evaluate(...)`/`.compile(...)` call whose argument is tainted by an HTTP endpoint parameter -- CWE-643. See [JavaXPathInjectionSinkFinder]. */
class XPathInjectionSinkInspection : LocalInspectionTool() {

    companion object {
        const val MAX_FILE_LENGTH = 500_000
    }

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file.text.length > MAX_FILE_LENGTH) return null

        val hits = JavaXPathInjectionSinkFinder.findAll(file)
        if (hits.isEmpty()) return null

        val problems = hits.map { hit ->
            manager.createProblemDescriptor(
                hit.anchor,
                messageFor(hit),
                isOnTheFly,
                emptyArray(),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            )
        }

        val path = file.virtualFile?.path
        if (path != null) {
            for (hit in hits) {
                val lineNumber = file.viewProvider.document?.getLineNumber(hit.anchor.textRange.startOffset) ?: -1
                ReviewPrompt.recordHit(file.project, "$path:$lineNumber:${hit.taintedParameterName}")
            }
        }

        return problems.toTypedArray()
    }

    private fun messageFor(hit: XPathSinkHit): String =
        "XPath expression built from endpoint parameter '${hit.taintedParameterName}' -- an attacker's raw input becomes " +
            "XPath SOURCE TEXT re-parsed by the XPath engine (CWE-643), the OWASP-documented XPath Injection mechanism"
}
