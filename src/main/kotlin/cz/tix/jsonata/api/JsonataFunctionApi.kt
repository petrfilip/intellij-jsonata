package cz.tix.jsonata.api

/**
 * Stable, dependency-free ABI for contributing custom JSONata functions to the playground.
 *
 * Implement [JsonataFunctionProvider] in YOUR project (a public class with a no-arg constructor) and
 * register it in *Settings ▸ Tools ▸ JSONata Playground* (or via the gutter icon on the class). The
 * plugin loads it from your project's compiled output and binds the functions so expressions can
 * call them as `$name(...)`.
 *
 * These interfaces intentionally do NOT depend on the JSONata engine, so your code stays decoupled
 * from the dashjoin library version.
 *
 * Example (Kotlin):
 * ```
 * class MyFunctions : JsonataFunctionProvider {
 *     override fun register(functions: JsonataFunctions) {
 *         functions.add("greet") { args -> "Hello, ${args.firstOrNull()}" }
 *     }
 * }
 * ```
 */
interface JsonataFunctionProvider {
    fun register(functions: JsonataFunctions)
}

/** Sink passed to a [JsonataFunctionProvider] for registering named functions. */
interface JsonataFunctions {
    /** Registers [fn] callable as `$name(...)` in expressions ([name] without the leading `$`). */
    fun add(name: String, fn: JsonataFn)
}

/**
 * A custom function body. Arguments arrive as already-parsed JSON values
 * (`Map` / `List` / `Number` / `String` / `Boolean` / `null`); return a JSON-compatible value
 * (or `null`, meaning JSONata *undefined*).
 */
fun interface JsonataFn {
    fun call(args: List<Any?>): Any?
}
