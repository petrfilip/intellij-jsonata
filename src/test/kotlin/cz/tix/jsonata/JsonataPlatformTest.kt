package cz.tix.jsonata

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import cz.tix.jsonata.completion.JsonataPlaygroundKeys
import cz.tix.jsonata.lang.JsonataFileType

/**
 * IntelliJ Platform integration tests (JUnit3-style [BasePlatformTestCase]) that exercise the
 * registered JSONata language extensions end to end: the [CompletionContributor] (function / value /
 * field completion, including the document-bound JSON supplier) and the engine-driven [Annotator].
 *
 * These run under the `test` task via the JUnit vintage engine. The `.jsonata` extension maps to the
 * JSONata language through the `<fileType>` registration in plugin.xml, so [configureByText] produces
 * a real [cz.tix.jsonata.lang.JsonataFile] with the parser, completion contributor and annotator wired.
 */
class JsonataPlatformTest : BasePlatformTestCase() {

    fun testJsonataExpressionFilesAreNotTreatedAsSourceJson() {
        val file = LightVirtualFile("query.jsonata", JsonataFileType, "\$sum(items)")

        assertFalse(".jsonata files must keep the JSONata editor, not the JSON split playground", isJsonFile(file))
    }

    // ---- completion: functions after `$` ----------------------------------

    fun testFunctionCompletionAfterDollar() {
        myFixture.configureByText("test.jsonata", "\$su<caret>")
        myFixture.completeBasic()

        val lookups = myFixture.lookupElementStrings
        // "$su" matches several builtins ($sum, $substring, $substringBefore, ...), so no single
        // match is auto-inserted and the lookup list is non-null.
        assertNotNull("lookup list should be present for a multi-match prefix", lookups)
        assertTrue("expected \$sum in $lookups", lookups!!.contains("\$sum"))
        assertTrue("expected \$substring in $lookups", lookups.contains("\$substring"))
    }

    // ---- completion: empty value position does not crash ------------------

    fun testValuePositionCompletionDoesNotThrow() {
        myFixture.configureByText("test.jsonata", "<caret>")
        // VALUE mode: addFields(null) (no-op without a supplier), addFunctions, addKeywords.
        // The contract for this test is purely "does not throw".
        myFixture.completeBasic()

        // The lookup may be null (single match auto-inserted) or non-null; either is acceptable here.
        // We only assert that completion ran without raising.
        val lookups = myFixture.lookupElementStrings
        if (lookups != null) {
            // When present it should at least contain some builtin functions (value position offers them).
            assertTrue("value-position lookups should include builtins, got $lookups", lookups.isNotEmpty())
        }
    }

    // ---- completion: JSON-field names at value position (root) ------------

    fun testRootFieldCompletionFromJsonSupplier() {
        myFixture.configureByText("test.jsonata", "<caret>")
        // Bind the JSON supplier on the editor's document BEFORE completing. BasePlatformTestCase
        // runs on the EDT so putUserData on the document is allowed here.
        myFixture.editor.document.putUserData(JsonataPlaygroundKeys.JSON_SUPPLIER) {
            "{\"account\":{\"order\":1},\"name\":\"x\"}"
        }
        myFixture.completeBasic()

        val lookups = myFixture.lookupElementStrings
        assertNotNull("lookup list should be present (root fields + functions + keywords)", lookups)
        assertTrue("expected root field 'account' in $lookups", lookups!!.contains("account"))
        assertTrue("expected root field 'name' in $lookups", lookups.contains("name"))
    }

    // ---- completion: field names after a `.` step -------------------------

    fun testFieldCompletionAfterDot() {
        myFixture.configureByText("test.jsonata", "account.<caret>")
        myFixture.editor.document.putUserData(JsonataPlaygroundKeys.JSON_SUPPLIER) {
            "{\"account\":{\"order\":1,\"id\":2}}"
        }
        myFixture.completeBasic()

        val lookups = myFixture.lookupElementStrings
        assertNotNull("lookup list should be present for fields after a dot", lookups)
        assertTrue("expected field 'order' in $lookups", lookups!!.contains("order"))
        assertTrue("expected field 'id' in $lookups", lookups.contains("id"))
        // After a `.` only fields are offered — functions/keywords must be absent.
        assertFalse("\$sum must NOT be offered after a dot, got $lookups", lookups.contains("\$sum"))
    }

    // ---- annotator: valid expression has no ERROR -------------------------

    fun testAnnotatorNoErrorForValidExpression() {
        myFixture.configureByText("test.jsonata", "account.order")
        val infos = myFixture.doHighlighting()
        val errors = infos.filter { it.severity == HighlightSeverity.ERROR }
        assertTrue("valid expression should produce no ERROR highlights, got $errors", errors.isEmpty())
    }

    // ---- annotator: invalid expression produces at least one ERROR --------

    fun testAnnotatorReportsErrorForInvalidExpression() {
        myFixture.configureByText("test.jsonata", "account.[")
        val infos = myFixture.doHighlighting()
        val errors = infos.filter { it.severity == HighlightSeverity.ERROR }
        assertTrue("invalid expression should produce at least one ERROR highlight", errors.isNotEmpty())
    }

    // ---- completion: a field name needing quotes inserts with backticks ----

    fun testFieldNeedingQuotesIsInsertedWithBackticks() {
        myFixture.configureByText("test.jsonata", "obj.fir<caret>")
        myFixture.editor.document.putUserData(JsonataPlaygroundKeys.JSON_SUPPLIER) {
            "{\"obj\":{\"first name\":1}}"
        }
        // The prefix "fir" uniquely matches the single field 'first name', so it auto-inserts.
        myFixture.completeBasic()

        // The space makes it an invalid bare identifier, so the insert handler backtick-quotes it.
        assertEquals("obj.`first name`", myFixture.editor.document.text)
    }

    // ---- completion: a reserved-word field name is backtick-quoted ---------

    fun testReservedWordFieldIsBacktickQuoted() {
        myFixture.configureByText("test.jsonata", "obj.an<caret>")
        myFixture.editor.document.putUserData(JsonataPlaygroundKeys.JSON_SUPPLIER) {
            "{\"obj\":{\"and\":1}}"
        }
        // After a dot only fields are offered, so the prefix "an" uniquely matches the field 'and';
        // 'and' is a reserved keyword, so even though it is a valid identifier it must be quoted.
        myFixture.completeBasic()

        assertEquals("obj.`and`", myFixture.editor.document.text)
    }

    // ---- completion: FUNCTION_INSERT appends () and positions the caret ----

    fun testFunctionInsertAppendsParensAndPlacesCaret() {
        // "$substringBe" matches only $substringBefore, so the platform auto-inserts it; the
        // FUNCTION_INSERT handler then appends "()" and moves the caret between the parens.
        myFixture.configureByText("test.jsonata", "\$substringBe<caret>")
        myFixture.completeBasic()

        assertEquals("\$substringBefore()", myFixture.editor.document.text)
        // Caret sits inside the parens: right after the '('.
        val expectedCaret = "\$substringBefore(".length
        assertEquals(expectedCaret, myFixture.editor.caretModel.offset)
    }

    // ---- completion: FUNCTION_INSERT does not duplicate an existing `(` -----

    fun testFunctionInsertDoesNotDuplicateExistingParen() {
        // The name is already followed by "(": the handler must NOT add a second '('.
        myFixture.configureByText("test.jsonata", "\$substringBe<caret>(str, 'x')")
        myFixture.completeBasic()

        assertEquals("\$substringBefore(str, 'x')", myFixture.editor.document.text)
    }

    // ---- completion: suppressed inside a string literal --------------------

    fun testCompletionSuppressedInsideStringLiteral() {
        myFixture.configureByText("test.jsonata", "\$contains(name, 'su<caret>')")
        myFixture.editor.document.putUserData(JsonataPlaygroundKeys.JSON_SUPPLIER) {
            "{\"account\":1}"
        }
        myFixture.completeBasic()

        // Inside a string literal the scanner reports Mode.NONE, so no elements are contributed and
        // the document is left untouched.
        assertEquals("\$contains(name, 'su')", myFixture.editor.document.text)
        val lookups = myFixture.lookupElementStrings
        if (lookups != null) {
            assertFalse("no builtins should be offered inside a string, got $lookups", lookups.contains("\$sum"))
            assertFalse("no fields should be offered inside a string, got $lookups", lookups.contains("account"))
        }
    }
}
