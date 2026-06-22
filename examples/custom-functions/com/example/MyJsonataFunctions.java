package com.example;

import cz.tix.jsonata.api.JsonataFunctionProvider;
import cz.tix.jsonata.api.JsonataFunctions;

import java.util.List;

/**
 * Example custom functions for the JSONata Playground plugin. Drop this (and the three
 * cz.tix.jsonata.api interface files) into any JVM project, build it, then enable + register
 * "com.example.MyJsonataFunctions" in Settings ▸ Tools ▸ JSONata Playground.
 *
 * Then, in a JSON file's playground, try:
 *   $greet("Petr")                 ->  "Hello, Petr!"
 *   $shout(Account.`Account Name`) ->  upper-cased value from the JSON
 *   $repeatStr("ab", 3)            ->  "ababab"
 *   $addAll(Account.Order.Product.Price)  ->  sum of all prices
 */
public class MyJsonataFunctions implements JsonataFunctionProvider {

    @Override
    public void register(JsonataFunctions functions) {
        functions.add("greet", args ->
            "Hello, " + (args.isEmpty() || args.get(0) == null ? "world" : args.get(0)) + "!");

        functions.add("shout", args ->
            args.isEmpty() || args.get(0) == null ? null : String.valueOf(args.get(0)).toUpperCase());

        functions.add("repeatStr", args -> {
            String s = String.valueOf(args.get(0));
            int n = ((Number) args.get(1)).intValue();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) sb.append(s);
            return sb.toString();
        });

        // Accepts either a single array argument or several numeric arguments.
        functions.add("addAll", args -> {
            double sum = 0;
            Iterable<?> values =
                (args.size() == 1 && args.get(0) instanceof List) ? (List<?>) args.get(0) : args;
            for (Object o : values) if (o instanceof Number) sum += ((Number) o).doubleValue();
            return sum;
        });
    }
}
