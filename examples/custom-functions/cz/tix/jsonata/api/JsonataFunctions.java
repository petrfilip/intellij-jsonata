package cz.tix.jsonata.api;

/** Sink for registering named functions; passed to {@link JsonataFunctionProvider#register}. */
public interface JsonataFunctions {
    /** Registers {@code fn} callable as {@code $name(...)} in expressions (name without the leading $). */
    void add(String name, JsonataFn fn);
}
