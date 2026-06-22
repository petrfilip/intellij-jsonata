package cz.tix.jsonata.completion

/**
 * Catalogue of JSONata built-in functions and keywords. Drives completion (names + signatures) and
 * parameter info (per-parameter hints). Signatures follow the official JSONata function library;
 * `[param]` denotes an optional parameter.
 */
object JsonataBuiltins {

    /** A single function parameter. */
    data class Param(val name: String, val optional: Boolean = false) {
        /** The parameter as shown in signatures: `[name]` if optional, else `name`. */
        fun display(): String = if (optional) "[$name]" else name
    }

    /** A built-in function entry. [name] includes the leading `$`. */
    data class Fn(
        val name: String,
        val params: List<Param>,
        val returns: String,
        val summary: String,
    ) {
        /** Rendered signature, e.g. `$round(number, [precision])`. */
        val signature: String
            get() = "$name(${params.joinToString(", ") { it.display() }})"
    }

    private fun fn(name: String, returns: String, summary: String, vararg params: Param) =
        Fn(name, params.toList(), returns, summary)

    private fun req(name: String) = Param(name, optional = false)
    private fun opt(name: String) = Param(name, optional = true)

    /** The catalogue of built-in functions; keys (`$name`) include the leading `$`. */
    val FUNCTIONS: List<Fn> = listOf(
        // ---- String ----
        fn("\$string", "string", "Casts the argument to a string.", req("arg"), opt("prettify")),
        fn("\$length", "number", "Number of characters in the string.", req("str")),
        fn("\$substring", "string", "Substring from start (and optional length).", req("str"), req("start"), opt("length")),
        fn("\$substringBefore", "string", "Substring before the first occurrence of chars.", req("str"), req("chars")),
        fn("\$substringAfter", "string", "Substring after the first occurrence of chars.", req("str"), req("chars")),
        fn("\$uppercase", "string", "Uppercases the string.", req("str")),
        fn("\$lowercase", "string", "Lowercases the string.", req("str")),
        fn("\$trim", "string", "Trims and normalises whitespace.", req("str")),
        fn("\$pad", "string", "Pads the string to a width (negative = left).", req("str"), req("width"), opt("char")),
        fn("\$contains", "boolean", "Whether str contains the substring/regex.", req("str"), req("pattern")),
        fn("\$split", "array", "Splits the string by separator/regex.", req("str"), req("separator"), opt("limit")),
        fn("\$join", "string", "Joins an array of strings with a separator.", req("array"), opt("separator")),
        fn("\$match", "array", "Applies the regex, returning match objects.", req("str"), req("pattern"), opt("limit")),
        fn("\$replace", "string", "Replaces matches of pattern with replacement.", req("str"), req("pattern"), req("replacement"), opt("limit")),
        fn("\$eval", "any", "Parses and evaluates a JSONata string.", req("expr"), opt("context")),
        fn("\$base64encode", "string", "Base64-encodes the string.", req("str")),
        fn("\$base64decode", "string", "Base64-decodes the string.", req("str")),
        fn("\$encodeUrlComponent", "string", "Encodes a URL component.", req("str")),
        fn("\$encodeUrl", "string", "Encodes a URL.", req("str")),
        fn("\$decodeUrlComponent", "string", "Decodes a URL component.", req("str")),
        fn("\$decodeUrl", "string", "Decodes a URL.", req("str")),

        // ---- Numeric ----
        fn("\$number", "number", "Casts the argument to a number.", req("arg")),
        fn("\$abs", "number", "Absolute value.", req("number")),
        fn("\$floor", "number", "Largest integer <= number.", req("number")),
        fn("\$ceil", "number", "Smallest integer >= number.", req("number")),
        fn("\$round", "number", "Rounds to optional precision.", req("number"), opt("precision")),
        fn("\$power", "number", "base raised to exponent.", req("base"), req("exponent")),
        fn("\$sqrt", "number", "Square root.", req("number")),
        fn("\$random", "number", "Random number in [0, 1)."),
        fn("\$formatNumber", "string", "Formats a number using a picture string.", req("number"), req("picture"), opt("options")),
        fn("\$formatBase", "string", "Formats an integer in the given radix.", req("number"), opt("radix")),
        fn("\$formatInteger", "string", "Formats an integer using a picture string.", req("number"), req("picture")),
        fn("\$parseInteger", "number", "Parses an integer using a picture string.", req("string"), req("picture")),

        // ---- Aggregation ----
        fn("\$sum", "number", "Sum of an array of numbers.", req("array")),
        fn("\$max", "number", "Maximum of an array of numbers.", req("array")),
        fn("\$min", "number", "Minimum of an array of numbers.", req("array")),
        fn("\$average", "number", "Arithmetic mean of an array of numbers.", req("array")),

        // ---- Boolean ----
        fn("\$boolean", "boolean", "Casts the argument to a boolean.", req("arg")),
        fn("\$not", "boolean", "Logical negation.", req("arg")),
        fn("\$exists", "boolean", "Whether the value exists (is not undefined).", req("arg")),

        // ---- Array ----
        fn("\$count", "number", "Number of items in the array.", req("array")),
        fn("\$append", "array", "Appends array2 to array1.", req("array1"), req("array2")),
        fn("\$sort", "array", "Sorts the array, optionally by a comparator.", req("array"), opt("function")),
        fn("\$reverse", "array", "Reverses the array.", req("array")),
        fn("\$shuffle", "array", "Randomly reorders the array.", req("array")),
        fn("\$distinct", "array", "Removes duplicate values.", req("array")),
        fn("\$zip", "array", "Zips arrays element-wise.", req("array1"), opt("...")),

        // ---- Object ----
        fn("\$keys", "array", "Keys of an object (or union over an array).", req("object")),
        fn("\$lookup", "any", "Value for a key in an object/array of objects.", req("object"), req("key")),
        fn("\$spread", "array", "Splits an object into single-key objects.", req("object")),
        fn("\$merge", "object", "Merges an array of objects into one.", req("array")),
        fn("\$sift", "object", "Keeps object entries for which function is truthy.", req("object"), req("function")),
        fn("\$each", "array", "Applies function to each key/value of an object.", req("object"), req("function")),
        fn("\$error", "any", "Throws an error with the given message.", req("message")),
        fn("\$assert", "any", "Throws if the condition is not truthy.", req("condition"), req("message")),
        fn("\$type", "string", "The JSONata type name of the value.", req("value")),

        // ---- Date/time ----
        fn("\$now", "string", "Current timestamp as ISO 8601 (or formatted).", opt("picture"), opt("timezone")),
        fn("\$millis", "number", "Current time in milliseconds since the epoch."),
        fn("\$fromMillis", "string", "Formats epoch milliseconds as a timestamp.", req("number"), opt("picture"), opt("timezone")),
        fn("\$toMillis", "number", "Parses a timestamp to epoch milliseconds.", req("timestamp"), opt("picture")),

        // ---- Higher-order ----
        fn("\$map", "array", "Applies function to each array element.", req("array"), req("function")),
        fn("\$filter", "array", "Keeps elements for which function is truthy.", req("array"), req("function")),
        fn("\$single", "any", "The single element matching function.", req("array"), req("function")),
        fn("\$reduce", "any", "Folds the array with function and optional init.", req("array"), req("function"), opt("init")),
    )

    private val BY_NAME: Map<String, Fn> = FUNCTIONS.associateBy { it.name }

    /** The function whose [Fn.name] (with `$`) equals [name], or null if not a built-in. */
    fun byName(name: String): Fn? = BY_NAME[name]

    /** JSONata language keywords offered in completion. */
    val KEYWORDS: List<String> = listOf("and", "or", "in", "function", "true", "false", "null")
}
