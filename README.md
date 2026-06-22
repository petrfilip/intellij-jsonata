# JSONata — IntelliJ plugin

IntelliJ IDEA plugin pro práci s [JSONata](https://jsonata.org/). Nad otevřeným JSON souborem otevřeš
panel, napíšeš JSONata výraz a hned vidíš výsledek — podobně jako [try.jsonata.org](https://try.jsonata.org/),
ale uvnitř IDE a napojené přímo na tvůj JSON soubor.

## Co umí

- **Živý náhled (per‑soubor).** JSON soubory se otevírají ve **split editoru** (jako Markdown):
  vlevo JSON, vpravo playground (výraz nahoře, výsledek dole) — náhled patří vždy tomu jednomu
  souboru, přepneš‑li na jiný JSON, vidíš jeho vlastní. Přepočítává se při editaci JSONu i výrazu
  (debounce, mimo EDT, runtime limity proti zacyklení).
- **Zvýraznění syntaxe** JSONata výrazu (vlastní lexer + jazyk).
- **Anotace chyb** — výraz se kompiluje reálným enginem; syntaktická chyba se podtrhne na svém místě.
- **Doplňování (autocomplete)** obojího:
  - **JSONata jazyk** — vestavěné funkce (se signaturami) a klíčová slova;
  - **struktura JSONu** — názvy polí podle aktuální cesty pod kurzorem (cesta se *vyhodnotí* proti
    navázanému JSONu, takže funguje i přes predikáty a volání funkcí).
- **Parameter info** — signatura vestavěné funkce s zvýrazněným aktuálním argumentem.

Engine: [`com.dashjoin:jsonata`](https://github.com/dashjoin/jsonata-java) (věrný port jsonata.js).

## Použití

1. Otevři JSON soubor — otevře se ve split editoru (ve výchozím stavu jen editor, nic neruší).
2. Náhled odkryješ přepínačem **Editor / Split / Preview** vpravo nahoře, nebo plovoucím tlačítkem
   **Open JSONata Playground** (a z popup menu ve stromu projektu).
3. Vlevo JSON, vpravo playground napojený na tenhle soubor. Piš výraz; výsledek se aktualizuje.
   Přepneš‑li na jiný JSON, vidíš jeho vlastní playground.
4. Doplňování: `Ctrl+Space` — po `.` se nabídnou pole JSONu (i uvnitř `.( … )` bloků a `[ … ]`
   predikátů, podle skutečného kontextu), po `$` funkce. Chyby se podtrhnou; **Ctrl+P** ukáže
   signaturu funkce.

## Vlastní funkce (custom functions)

> ⚠️ **Dočasně vypnuto v UI** (0.1.0) — feature je hotová v kódu, ale skrytá z frontendu, než ji
> doladíme. Zapnutí: přepni `CUSTOM_FUNCTIONS_ENABLED` na `true` (v `JsonataFeatureFlags.kt`) a
> odkomentuj registrace v `plugin.xml` (`projectConfigurable`) a `jsonata-uast.xml` (`lineMarkerProvider`).

Dvě cesty, jak přidat funkce volané ve výrazu jako `$mojeFunkce(...)` — konfigurace je v
**Settings ▸ Tools ▸ JSONata Playground** (per projekt):

1. **JSONata prelude** — definice (`$double := function($x){ $x * 2 };`) se prependují ke každému výrazu.
2. **JVM funkce z tvého projektu** — napiš třídu implementující `cz.tix.jsonata.api.JsonataFunctionProvider`,
   zkompiluj projekt, v Settings zapni *Load custom functions* a přidej její FQN. Plugin ji načte
   z výstupu modulu (vlastní classloader) a funkce nabinduje. Hotová ukázka k vyzkoušení v jiném repu:
   [`examples/custom-functions/`](examples/custom-functions/).

Třídu lze zaregistrovat i **gutter ikonou** u její deklarace (Java/Kotlin přes UAST): u třídy
implementující `JsonataFunctionProvider` se objeví ikona — **zelená** když je zaregistrovaná, „+"
když ne; kliknutím registraci přepneš (a zapne se opt‑in). Zdrojem pravdy zůstává Settings.

Dostupné funkce vidíš v řádku „Custom functions" v panelu a našeptávají se (`Ctrl+Space` po `$`).

> ⚠️ Načtený JVM kód běží **nesandboxovaně v JVM IDE**, proto je to explicitní opt‑in (defaultně
> vypnuto) — zapínej jen pro projekty, kterým věříš.

## Spuštění a vývoj

```bash
./gradlew runIde          # spustí sandbox IDE s nainstalovaným pluginem
./gradlew buildPlugin     # ZIP do build/distributions/
./gradlew test            # spustí testy (240: pure + headless platform)
./gradlew verifyPluginProjectConfiguration
```

### Požadavky na build
- IntelliJ Platform Gradle Plugin **2.16**, cílová IDE **IntelliJ IDEA Community 2024.3** (`sinceBuild 243`).
- Build běží na **JDK 21 toolchainu** (platforma 2024.3 vyžaduje Java 21). Cesta k lokálnímu JDK 21 je
  v `gradle.properties` (`org.gradle.java.installations.paths`); na CI/jiném stroji nainstaluj JDK 21
  nebo přidej `foojay-resolver` do `settings.gradle.kts`.
- Gradle wrapper je připnutý na 9.0.0.

## Architektura (stručně)

```
cz.tix.jsonata
├─ engine/        JsonataEvaluator (kompilace+eval s limity), JsonRenderer (pretty JSON)
├─ lang/          JsonataLanguage, FileType, Lexer, ParserDefinition (flat PSI),
│                 SyntaxHighlighter(+Factory), Annotator (engine-driven), ParameterInfoHandler
├─ completion/    JsonataExpressionScanner (kontext kurzoru, string/depth-aware),
│                 JsonataFieldKeys (doplňování polí „vyhodnoť prefix proti JSONu"),
│                 JsonataBuiltins (katalog funkcí), JsonataCompletionContributor
├─ editor/        JsonataSplitEditorProvider/SplitEditor/PreviewFileEditor (TextEditorWithPreview)
├─ toolwindow/    JsonataPlaygroundPanel (LanguageTextField výraz + výsledek; obsah náhledu)
├─ api/           JsonataFunctionProvider / JsonataFunctions / JsonataFn (ABI pro custom funkce)
├─ settings/      JsonataProjectSettings (registr), JsonataFunctionLoader (classloader), JsonataConfigurable
└─ actions/       OpenJsonataPlaygroundAction, JsonataFloatingToolbarProvider
```

Detailní analýzy a rozhodnutí: [`ANALYZA.md`](ANALYZA.md) (celkový návrh) a
[`AUTOCOMPLETE.md`](AUTOCOMPLETE.md) (autocomplete + proč ne LSP).

## Známá omezení / budoucí práce

- Zadaný výraz se nepamatuje mezi sezeními (žije v záložce editoru daného souboru).
- Všechny JSON soubory se otevírají ve split editoru (náhled je ale defaultně skrytý).
- Plný strukturní PSI parser (rename, find-usages `$proměnných`) — neudělán; není pro stávající funkce nutný.
- Doplňování polí uvnitř lambda (`$map(a, function($v){ $v. })`) — nemá scope `$v`, spadne na fallback.
- Regex literály `/.../` se nelexují zvlášť (engine je vyhodnotí správně).
- IntelliJ Plugin Verifier proti více verzím IDE je vhodné zapnout v CI.
