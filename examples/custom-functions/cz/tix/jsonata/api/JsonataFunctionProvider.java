package cz.tix.jsonata.api;

/**
 * Implement this in YOUR project to contribute custom JSONata functions to the playground.
 * The implementing class must be public and have a public no-arg constructor.
 *
 * NOTE: these three interface files are copies provided so your project compiles. At runtime the
 * plugin supplies the real interfaces (same fully-qualified names) via its own classloader, so your
 * copies are shadowed and the types match — keep the package `cz.tix.jsonata.api`.
 */
public interface JsonataFunctionProvider {
    void register(JsonataFunctions functions);
}
