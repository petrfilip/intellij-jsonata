# Example custom functions for JSONata Playground

These files let you try the **custom functions** feature end-to-end from another project.

## What's here
- `cz/tix/jsonata/api/` — the three ABI interfaces (`JsonataFunctionProvider`, `JsonataFunctions`,
  `JsonataFn`). Copies so your project compiles; at runtime the plugin provides the real ones
  (same FQNs), so keep the package name `cz.tix.jsonata.api`.
- `com/example/MyJsonataFunctions.java` — a sample provider with `$greet`, `$shout`, `$repeatStr`, `$addAll`.

## How to try it
1. Copy all of these into any JVM project you have open in IntelliJ (keep the package folders).
2. **Build the project** (Build ▸ Build Project) so the classes are compiled to the module output.
3. In IntelliJ: **Settings ▸ Tools ▸ JSONata Playground**:
   - check *Load custom functions from this project's compiled output*,
   - add `com.example.MyJsonataFunctions` to the provider list,
   - Apply.
4. Open a JSON file, reveal the playground (Split / floating button), and use the functions, e.g.:
   ```
   $greet("Petr")
   $repeatStr("ab", 3)
   $addAll([1, 2, 3, 4])
   ```
   They also autocomplete (`Ctrl+Space` after `$`) and appear in the panel's "Custom functions" line.

## Notes
- Loaded code runs **inside the IDE JVM, unsandboxed** — only enable it for projects you trust
  (that's why it's an explicit per-project opt-in, off by default).
- After you edit + recompile a provider, toggle the setting (or reopen the JSON file) to reload.
- Kotlin works too: implement `JsonataFunctionProvider` and call `functions.add("name") { args -> ... }`.
