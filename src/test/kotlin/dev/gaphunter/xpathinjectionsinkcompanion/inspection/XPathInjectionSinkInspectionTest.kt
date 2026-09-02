package dev.gaphunter.xpathinjectionsinkcompanion.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class XPathInjectionSinkInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(XPathInjectionSinkInspection::class.java)
    }

    fun `test a request parameter concatenated into an XPath predicate is flagged`() {
        myFixture.configureByText(
            "UserController.java",
            """
            import javax.xml.xpath.XPath;
            import javax.xml.xpath.XPathFactory;
            import org.springframework.web.bind.annotation.GetMapping;

            class UserController {
                @GetMapping("/user")
                Object findUser(String username) throws Exception {
                    XPath xpath = XPathFactory.newInstance().newXPath();
                    return xpath.evaluate("//user[username='" + username + "']", (Object) null);
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("CWE-643") == true })
    }

    fun `test a request parameter passed directly to evaluate is flagged`() {
        myFixture.configureByText(
            "RawController.java",
            """
            import javax.xml.xpath.XPath;
            import javax.xml.xpath.XPathFactory;
            import org.springframework.web.bind.annotation.GetMapping;

            class RawController {
                @GetMapping("/raw")
                Object run(String userExpr) throws Exception {
                    XPath xpath = XPathFactory.newInstance().newXPath();
                    return xpath.evaluate(userExpr, (Object) null);
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("CWE-643") == true })
    }

    fun `test a hardcoded XPath expression with no taint is not flagged`() {
        myFixture.configureByText(
            "SafeController.java",
            """
            import javax.xml.xpath.XPath;
            import javax.xml.xpath.XPathFactory;
            import org.springframework.web.bind.annotation.GetMapping;

            class SafeController {
                @GetMapping("/safe")
                Object run(String unused) throws Exception {
                    XPath xpath = XPathFactory.newInstance().newXPath();
                    return xpath.evaluate("//user[@id='1']", (Object) null);
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("CWE-643") == true })
    }

    fun `test a non-XPath type with a coincidentally named evaluate method is not flagged`() {
        myFixture.configureByText(
            "OtherEvaluator.java",
            """
            import org.springframework.web.bind.annotation.GetMapping;

            class MyEvaluator {
                Object evaluate(String s, Object ctx) { return s; }
            }

            class OtherEvaluator {
                @GetMapping("/x")
                Object handle(String expr) {
                    MyEvaluator evaluator = new MyEvaluator();
                    return evaluator.evaluate(expr, null);
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("CWE-643") == true })
    }

    fun `test a non-endpoint method with the same shape is not flagged`() {
        myFixture.configureByText(
            "Helper.java",
            """
            import javax.xml.xpath.XPath;
            import javax.xml.xpath.XPathFactory;

            class Helper {
                Object run(String username) throws Exception {
                    XPath xpath = XPathFactory.newInstance().newXPath();
                    return xpath.evaluate("//user[username='" + username + "']", (Object) null);
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("CWE-643") == true })
    }
}
