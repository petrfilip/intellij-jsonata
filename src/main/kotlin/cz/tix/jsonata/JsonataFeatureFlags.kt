package cz.tix.jsonata

/**
 * Master switch for the **custom functions** feature (the JSONata prelude and JVM
 * [cz.tix.jsonata.api.JsonataFunctionProvider] classes loaded from the project).
 *
 * Temporarily `false`: the feature is fully implemented but hidden from the UI while it is being
 * tested and fine-tuned. While off, the playground evaluates the bare expression only — no prelude,
 * no project providers, no custom-function completion or status row.
 *
 * To re-enable, flip this to `true` **and** uncomment the matching registrations:
 *  - `plugin.xml`        → `<projectConfigurable … JsonataConfigurable>` (Settings ▸ Tools page)
 *  - `jsonata-uast.xml`  → `<codeInsight.lineMarkerProvider … JsonataProviderLineMarkerProvider>` (gutter icon)
 */
internal const val CUSTOM_FUNCTIONS_ENABLED = false
