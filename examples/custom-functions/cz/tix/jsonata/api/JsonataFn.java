package cz.tix.jsonata.api;

import java.util.List;

/**
 * A custom function body. Arguments arrive as already-parsed JSON values
 * (Map / List / Number / String / Boolean / null); return a JSON-compatible value
 * (or null, meaning JSONata "undefined").
 */
@FunctionalInterface
public interface JsonataFn {
    Object call(List<Object> args);
}
