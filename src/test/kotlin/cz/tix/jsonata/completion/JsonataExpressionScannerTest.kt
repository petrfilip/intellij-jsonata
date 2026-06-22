package cz.tix.jsonata.completion

import cz.tix.jsonata.completion.JsonataExpressionScanner.Call
import cz.tix.jsonata.completion.JsonataExpressionScanner.Ctx
import cz.tix.jsonata.completion.JsonataExpressionScanner.Mode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JsonataExpressionScannerTest {

    // ---- helpers -----------------------------------------------------------

    /** Analyze with the caret at the end of [text]. */
    private fun analyzeEnd(text: String): Ctx =
        JsonataExpressionScanner.analyze(text, text.length)

    private fun assertCtx(expectedMode: Mode, expectedContainer: String?, actual: Ctx) {
        assertEquals(expectedMode, actual.mode, "mode for $actual")
        assertEquals(expectedContainer, actual.containerExpr, "container for $actual")
    }

    // ---- analyze: Mode.VALUE ----------------------------------------------

    @Test
    fun `empty text is VALUE with no container`() {
        assertCtx(Mode.VALUE, null, analyzeEnd(""))
    }

    @Test
    fun `bare identifier without preceding dot is VALUE`() {
        assertCtx(Mode.VALUE, null, analyzeEnd("account"))
    }

    @Test
    fun `value position right after an open paren is VALUE`() {
        // "$sum(" -> caret follows '(', the partial token is empty and the
        // significant char before it is '(' (not '.'), so VALUE.
        assertCtx(Mode.VALUE, null, analyzeEnd("\$sum("))
    }

    @Test
    fun `after a comma with empty partial is VALUE`() {
        assertCtx(Mode.VALUE, null, analyzeEnd("\$map(items,"))
    }

    @Test
    fun `inside a single quoted string has no completion context`() {
        assertCtx(Mode.NONE, null, analyzeEnd("\$contains(name, 'Jo"))
    }

    @Test
    fun `inside a double quoted string has no completion context`() {
        assertCtx(Mode.NONE, null, analyzeEnd("\$contains(name, \"Jo"))
    }

    @Test
    fun `inside a backtick field name has no completion context`() {
        assertCtx(Mode.NONE, null, analyzeEnd("account.`first"))
    }

    @Test
    fun `inside a block comment has no completion context`() {
        assertCtx(Mode.NONE, null, analyzeEnd("account /* comment with $."))
    }

    @Test
    fun `inside a regex literal has no completion context`() {
        assertCtx(Mode.NONE, null, analyzeEnd("\$match(name, /ab"))
    }

    @Test
    fun `dollar inside a regex literal does not trigger function completion`() {
        assertCtx(Mode.NONE, null, analyzeEnd("\$match(name, /\$"))
    }

    @Test
    fun `after a closed string returns to value context`() {
        assertCtx(Mode.VALUE, null, analyzeEnd("\$contains(name, 'Jo'"))
    }

    @Test
    fun `after a closed block comment returns to value context`() {
        assertCtx(Mode.VALUE, null, analyzeEnd("/* comment */ "))
    }

    // ---- analyze: Mode.FUNCTION -------------------------------------------

    @Test
    fun `partial starting with dollar is FUNCTION`() {
        assertCtx(Mode.FUNCTION, null, analyzeEnd("\$su"))
    }

    @Test
    fun `lone dollar is FUNCTION`() {
        assertCtx(Mode.FUNCTION, null, analyzeEnd("\$"))
    }

    // ---- analyze: Mode.FIELD ----------------------------------------------

    @Test
    fun `single field after dot offers the container before the dot`() {
        assertCtx(Mode.FIELD, "account", analyzeEnd("account."))
    }

    @Test
    fun `multi-step path keeps the full balanced container`() {
        assertCtx(Mode.FIELD, "account.order", analyzeEnd("account.order.pr"))
    }

    @Test
    fun `container scan stops at the enclosing open paren of a function call`() {
        // "$sum(account.order.pr" -> container is only what's inside the call.
        assertCtx(Mode.FIELD, "account.order", analyzeEnd("\$sum(account.order.pr"))
    }

    @Test
    fun `binary operator terminates the container scan`() {
        assertCtx(Mode.FIELD, "b", analyzeEnd("a + b.c"))
    }

    @Test
    fun `multiplication operator terminates the container scan`() {
        assertCtx(Mode.FIELD, "b", analyzeEnd("a * b.c"))
    }

    @Test
    fun `modulo operator terminates the container scan`() {
        assertCtx(Mode.FIELD, "b", analyzeEnd("a % b.c"))
    }

    @Test
    fun `wildcard path step is kept in the container scan`() {
        assertCtx(Mode.FIELD, "a.*", analyzeEnd("a.*.c"))
    }

    @Test
    fun `descendant wildcard path step is kept in the container scan`() {
        assertCtx(Mode.FIELD, "a.**", analyzeEnd("a.**.c"))
    }

    @Test
    fun `predicate brackets are balanced and the string literal is opaque`() {
        assertCtx(Mode.FIELD, "items[type='book']", analyzeEnd("items[type='book']."))
    }

    @Test
    fun `a dot inside a string literal does not break the container`() {
        assertCtx(Mode.FIELD, "items[k='a.b']", analyzeEnd("items[k='a.b'].x"))
    }

    @Test
    fun `backtick quoted name is a single container token`() {
        assertCtx(Mode.FIELD, "`first name`", analyzeEnd("`first name`."))
    }

    @Test
    fun `a dot inside balanced predicate brackets stays inside the container`() {
        assertCtx(Mode.FIELD, "x[y.z]", analyzeEnd("x[y.z]."))
    }

    // ---- analyze: caret in the middle of the text --------------------------

    @Test
    fun `caret right after the dot of a path is FIELD on the left part`() {
        // index 8 is right after "account." in "account.order".
        val text = "account.order"
        assertEquals('.', text[7])
        assertEquals('o', text[8])
        assertCtx(Mode.FIELD, "account", JsonataExpressionScanner.analyze(text, 8))
    }

    // ---- findEnclosingCall: positive cases --------------------------------

    @Test
    fun `enclosing call reports name parameter index and open paren offset`() {
        // "$sum(a, b" -> '(' at index 4, one top-level comma -> parameter index 1.
        val text = "\$sum(a, b"
        assertEquals('(', text[4])
        val call = JsonataExpressionScanner.findEnclosingCall(text, text.length)
        assertEquals(Call("\$sum", 1, 4), call)
    }

    @Test
    fun `enclosing call on first argument has parameter index zero`() {
        // "$substring(str" -> '(' at index 10, no commas yet.
        val text = "\$substring(str"
        assertEquals('(', text[10])
        val call = JsonataExpressionScanner.findEnclosingCall(text, text.length)
        assertEquals(Call("\$substring", 0, 10), call)
    }

    @Test
    fun `nested call argument commas inside inner parens are not counted`() {
        // "$outer($inner(x), " -> outer '(' at 6; the comma after the inner
        // call is the only top-level comma -> parameter index 1.
        val text = "\$outer(\$inner(x), "
        assertEquals('(', text[6])
        val call = JsonataExpressionScanner.findEnclosingCall(text, text.length)
        assertEquals(Call("\$outer", 1, 6), call)
    }

    @Test
    fun `call name is found even when the call sits inside a predicate`() {
        // "items[$f(" -> '(' at index 8, function name "$f".
        val text = "items[\$f("
        assertEquals('(', text[8])
        val call = JsonataExpressionScanner.findEnclosingCall(text, text.length)
        assertEquals(Call("\$f", 0, 8), call)
    }

    // ---- findEnclosingCall: null cases ------------------------------------

    @Test
    fun `paren without leading dollar name is not a function call`() {
        assertNull(JsonataExpressionScanner.findEnclosingCall("foo(", "foo(".length))
    }

    @Test
    fun `plain path is not inside any call`() {
        assertNull(JsonataExpressionScanner.findEnclosingCall("account.order", "account.order".length))
    }

    @Test
    fun `caret after the closing paren is not inside the call`() {
        val text = "\$sum(a, b)"
        assertNull(JsonataExpressionScanner.findEnclosingCall(text, text.length))
    }

    @Test
    fun `caret inside a predicate bracket is not inside a call`() {
        // "items[x = " -> the unbalanced '[' is hit at depth 0 -> null.
        assertNull(JsonataExpressionScanner.findEnclosingCall("items[x = ", "items[x = ".length))
    }

    @Test
    fun `caret inside a string is not inside a call for parameter info`() {
        assertNull(JsonataExpressionScanner.findEnclosingCall("\$contains(name, 'Jo", "\$contains(name, 'Jo".length))
    }

    @Test
    fun `caret inside a block comment is not inside a call for parameter info`() {
        val text = "\$sum(/* ignored"
        assertNull(JsonataExpressionScanner.findEnclosingCall(text, text.length))
    }

    @Test
    fun `caret inside a regex literal is not inside a call for parameter info`() {
        val text = "\$match(name, /ab"
        assertNull(JsonataExpressionScanner.findEnclosingCall(text, text.length))
    }

    // ---- a few extra precision checks --------------------------------------

    @Test
    fun `caret strictly inside the argument list still resolves the call`() {
        // "$sum(a, b)" with caret just after the first arg's comma+space.
        val text = "\$sum(a, b)"
        val caret = text.indexOf('b') // 8, inside the arg list, before ')'
        assertEquals('b', text[caret])
        assertEquals(Call("\$sum", 1, 4), JsonataExpressionScanner.findEnclosingCall(text, caret))
    }

    @Test
    fun `whitespace before the dot does not change the container`() {
        // The significant-char scan in analyze skips whitespace before the token,
        // but the dot must be the immediately preceding significant char.
        assertCtx(Mode.FIELD, "account", analyzeEnd("account. "))
    }

    // ---- caret coercion ----------------------------------------------------

    @Test
    fun `caret past the end is coerced to the text length`() {
        // A caret of 999 against "account." behaves like a caret at the end -> FIELD on "account".
        assertCtx(Mode.FIELD, "account", JsonataExpressionScanner.analyze("account.", 999))
    }

    @Test
    fun `negative caret is coerced to zero`() {
        // A negative caret clamps to offset 0: empty partial, nothing before it -> VALUE.
        assertCtx(Mode.VALUE, null, JsonataExpressionScanner.analyze("account.", -5))
    }

    // ---- opaque-literal precision ------------------------------------------

    @Test
    fun `escaped quote inside a single-quoted string keeps it opaque until the real close`() {
        // "$x('a\'b')." -> the escaped quote does not close the string; the third quote does, so
        // the trailing dot is a real path step and the whole call is the container.
        assertCtx(Mode.FIELD, "\$x('a\\'b')", analyzeEnd("\$x('a\\'b')."))
    }

    @Test
    fun `backtick does not honor a backslash escape`() {
        // Intended behavior: JSONata backtick field names have NO backslash escaping, so "`a\`"
        // closes at the second backtick and ".x" becomes a path step on the (literal) backtick name.
        assertCtx(Mode.FIELD, "`a\\`", analyzeEnd("`a\\`.x"))
    }

    @Test
    fun `block comment immediately before a dollar token is FUNCTION`() {
        // "/* c */$su" -> the comment is opaque; the partial token "$su" still triggers FUNCTION.
        assertCtx(Mode.FUNCTION, null, analyzeEnd("/* c */\$su"))
    }

    // ---- extractContainer operator handling --------------------------------

    @Test
    fun `star and percent are treated as path separators in the container scan`() {
        // '*' and '%' (multiply/modulo) terminate the backward scan, yielding "b".
        assertCtx(Mode.FIELD, "b", analyzeEnd("a*b."))
        assertCtx(Mode.FIELD, "b", analyzeEnd("a%b."))
    }

    @Test
    fun `at and hash positional binders are kept in the path container`() {
        // Intended behavior: '@' and '#' are JSONata positional-variable BINDERS (e.g. `Order@$o`,
        // `books#$i`) that bind a path step, so they are kept as part of the container expression.
        // `a@b` / `a#b` is not valid JSONata anyway, so absorbing them here is harmless.
        assertCtx(Mode.FIELD, "a@b", analyzeEnd("a@b."))
        assertCtx(Mode.FIELD, "a#b", analyzeEnd("a#b."))
    }

    // ---- findEnclosingCall: lambda / object-literal arguments --------------

    @Test
    fun `findEnclosingCall resolves the call when the caret is in a lambda body argument`() {
        // "$sort(arr, function($l,$r){" -> the unbalanced '{' is a lambda body; scanning continues
        // outward to the enclosing "$sort(" call. One top-level comma -> 2nd arg -> parameterIndex 1.
        val text = "\$sort(arr, function(\$l,\$r){"
        assertEquals('(', text[5])
        assertEquals(Call("\$sort", 1, 5), JsonataExpressionScanner.findEnclosingCall(text, text.length))
    }

    @Test
    fun `findEnclosingCall rejects a bare dollar followed by an open paren`() {
        // "$(" is not a function call: '$' must be followed by at least one identifier char.
        assertNull(JsonataExpressionScanner.findEnclosingCall("\$(", 2))
    }

    @Test
    fun `findEnclosingCall does not treat a double dollar as a function name start`() {
        // "$$count(" -> "$$" is the JSONata root variable, not a "$name" call start; the function
        // name read must not begin on the inner '$' that is immediately preceded by another '$'.
        assertNull(JsonataExpressionScanner.findEnclosingCall("\$\$count(", "\$\$count(".length))
    }

    // ---- opaqueMask: regex vs division -------------------------------------

    @Test
    fun `division after an identifier is not a regex`() {
        // "a/b." -> the '/' follows an identifier (not an operator/keyword), so it is division;
        // "b" stays plain and the trailing dot is a path step on it.
        assertCtx(Mode.FIELD, "b", analyzeEnd("a/b."))
    }

    @Test
    fun `division after a number is not a regex`() {
        // "1/x." -> '/' after a digit is division, so "x" is a normal path step container.
        assertCtx(Mode.FIELD, "x", analyzeEnd("1/x."))
    }

    @Test
    fun `regex is enabled after the in keyword`() {
        // "x in /ab" -> 'in' is a keyword, so '/' starts a regex; the caret is inside it -> NONE.
        assertCtx(Mode.NONE, null, analyzeEnd("x in /ab"))
    }

    @Test
    fun `regex is enabled after the and keyword`() {
        assertCtx(Mode.NONE, null, analyzeEnd("x and /ab"))
    }

    @Test
    fun `regex is not enabled after a non-keyword word`() {
        // "foo /ab" -> 'foo' is not a keyword, so '/' is division, not a regex; the caret is at a
        // value position (the partial token "ab" is preceded by a non-dot operator) -> VALUE.
        assertCtx(Mode.VALUE, null, analyzeEnd("foo /ab"))
    }

    @Test
    fun `a closing bracket inside a regex char class does not end the regex`() {
        // "$match(x, /[a/b]" -> inside a '[...]' char class the '/' is literal, so the regex is
        // still open at the caret -> NONE.
        assertCtx(Mode.NONE, null, analyzeEnd("\$match(x, /[a/b]"))
    }

    @Test
    fun `a regex char class closes then a slash ends the regex and flags are masked`() {
        // "$match(x, /ab/i)." -> the char-class is absent; the second '/' closes the regex, the
        // trailing flag 'i' is masked, and the dot after ')' is a real path step on the call.
        assertCtx(Mode.FIELD, "\$match(x, /ab/i)", analyzeEnd("\$match(x, /ab/i)."))
    }

    @Test
    fun `a backslash escape inside a regex skips the following slash`() {
        // "$match(x, /a\/b" -> the escaped '/' does not close the regex, so the caret is still
        // inside it -> NONE.
        assertCtx(Mode.NONE, null, analyzeEnd("\$match(x, /a\\/b"))
    }

    @Test
    fun `an unterminated block comment has no completion context`() {
        assertCtx(Mode.NONE, null, analyzeEnd("a /* open"))
    }

    // ---- extractContainer: operator boundaries -----------------------------

    @Test
    fun `assignment operator terminates the container scan`() {
        // "$x := a." -> the ':=' (its '=') terminates the backward scan, leaving "a".
        assertCtx(Mode.FIELD, "a", analyzeEnd("\$x := a."))
    }

    @Test
    fun `ternary colon terminates the container scan`() {
        // "c ? t : a." -> the ':' is a separator, so the container is just "a".
        assertCtx(Mode.FIELD, "a", analyzeEnd("c ? t : a."))
    }

    @Test
    fun `semicolon terminates the container scan`() {
        // "$x := 1; a." -> the ';' separates expressions; the container is "a".
        assertCtx(Mode.FIELD, "a", analyzeEnd("\$x := 1; a."))
    }

    @Test
    fun `equals operator terminates the container scan`() {
        assertCtx(Mode.FIELD, "a", analyzeEnd("x = a."))
    }

    @Test
    fun `leading whitespace-only before a dot yields a null container`() {
        // "   ." -> nothing but whitespace precedes the dot, so there is no container expression.
        assertCtx(Mode.FIELD, null, analyzeEnd("   ."))
    }

    @Test
    fun `a unary minus terminates the container scan`() {
        // "-a." -> the leading '-' is a separator, so the container is "a".
        assertCtx(Mode.FIELD, "a", analyzeEnd("-a."))
    }

    @Test
    fun `a balanced paren group is captured whole as the container`() {
        assertCtx(Mode.FIELD, "(a + b)", analyzeEnd("(a + b)."))
    }

    @Test
    fun `a balanced predicate is captured whole as the container`() {
        assertCtx(Mode.FIELD, "a[b]", analyzeEnd("a[b]."))
    }

    // ---- findEnclosingCall: more edges -------------------------------------

    @Test
    fun `findEnclosingCall at offset zero is null`() {
        // No characters precede the caret, so there is no enclosing call.
        assertNull(JsonataExpressionScanner.findEnclosingCall("\$f(", 0))
    }

    @Test
    fun `findEnclosingCall reads a function name with digits and underscore`() {
        // "$f_2(" -> the name read includes letters, digits and underscores.
        val text = "\$f_2("
        assertEquals('(', text[4])
        assertEquals(Call("\$f_2", 0, 4), JsonataExpressionScanner.findEnclosingCall(text, text.length))
    }

    @Test
    fun `findEnclosingCall sees through a balanced predicate inside the args`() {
        // "$f(a[0], " -> the inner "[0]" predicate is balanced, so the comma after it is the only
        // top-level comma -> parameter index 1.
        val text = "\$f(a[0], "
        assertEquals('(', text[2])
        assertEquals(Call("\$f", 1, 2), JsonataExpressionScanner.findEnclosingCall(text, text.length))
    }

    @Test
    fun `findEnclosingCall returns null when an unbalanced predicate precedes a call`() {
        // "$f(a[" -> the unbalanced '[' at depth 0 is a predicate, so the caret is not in a call's
        // argument list -> null.
        assertNull(JsonataExpressionScanner.findEnclosingCall("\$f(a[", "\$f(a[".length))
    }

    @Test
    fun `findEnclosingCall counts args correctly across an unbalanced brace body`() {
        // "$f(a, function(){x:1, y" -> the caret is in the 2nd arg (index 1); the comma inside the
        // unbalanced "{ }" body is part of the object literal, not a top-level arg separator.
        val text = "\$f(a, function(){x:1, y"
        assertEquals(Call("\$f", 1, 2), JsonataExpressionScanner.findEnclosingCall(text, text.length))
    }
}
