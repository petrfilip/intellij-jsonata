# Publikace na JetBrains Marketplace

Návod, jak dostat plugin **JSONata** na [JetBrains Marketplace](https://plugins.jetbrains.com/)
a jak ho dál aktualizovat. Součástí je ověření, že plugin běží napříč všemi IntelliJ produkty
(nejen Java IntelliJ IDEA).

---

## 1. Shrnutí (TL;DR)

1. Doplnit metadata pluginu (popis, change-notes, licence) a ujasnit `sinceBuild`/`untilBuild`.
2. `./gradlew verifyPluginProjectConfiguration` → `./gradlew buildPlugin` → ověřit ZIP v sandboxu (`runIde`).
3. Spustit **Plugin Verifier** napříč produkty (`./gradlew verifyPlugin`) — potvrdí kompatibilitu s PyCharm,
   WebStorm, GoLand, … nejen IDEA.
4. Vygenerovat **podpisový certifikát** a **publish token**, doplnit `signing {}` / `publishing {}` do `build.gradle.kts`.
5. **První verzi nahrát ručně** přes web (kvůli moderaci a vyplnění listingu), další verze přes `./gradlew publishPlugin`.

> **Stav v tomto repu (verze 0.1.0):** připravené a **ověřené** je vše, co jde bez tajných klíčů —
> `build.gradle.kts` (signing/publishing/verifier/changeNotes), `LICENSE` (MIT), listing ikony
> (`META-INF/pluginIcon.svg` + dark varianta), `CHANGELOG.md`, vendor `Petr Filip` a GitHub workflow pro
> build i release. JDK toolchain je přenositelný (foojay-resolver 1.0.0, žádná natvrdo zadaná cesta),
> plugin je čistě **platform-only** (žádná Java/UAST závislost) a `./gradlew verifyPlugin` je **zelený
> napříč IDEA + PyCharm + WebStorm**. **Na tobě zbývá:** vygenerovat podpisový certifikát + publish token
> a nastavit je jako GitHub secrets (krok 7–8), a první verzi nahrát ručně kvůli moderaci (krok 10).

---

## 2. Kompatibilita napříč JetBrains IDE — ✅ ano, běží všude

**Otázka byla, jestli plugin může běžet ve všech IntelliJ produktech (ne jen v Java IntelliJ IDEA).
Odpověď: ano.** Plugin je tak přímo navržený. Důkazy z kódu:

| Co | Stav | Důsledek |
|----|------|----------|
| `<depends>com.intellij.modules.platform</depends>` | jediná závislost (a povinná) | jádro platformy je v **každém** IntelliJ IDE |
| Java/UAST | **žádná** závislost (ani volitelná, ani pro kompilaci) | nic vázaného na `com.intellij.modules.java` |
| Detekce JSON souboru (`isJsonFile`) | jen podle přípony `.json` / `.json5` | **žádná** závislost na JSON pluginu |
| Vykreslení výsledku (`JsonRenderer`) | vlastní pretty-printer | žádná externí JSON knihovna |
| JSONata engine `com.dashjoin:jsonata` | přibalený do pluginu | nezávisí na IDE |

Plugin **nemá žádnou část vázanou na Javu**. (Dřívější gutter ikona u tříd implementujících
`JsonataFunctionProvider` — `JsonataProviderLineMarkerProvider` přes UAST — byla odstraněna, protože
tahala Java/UAST API do hlavního jaru a Plugin Verifier ji hlásil jako chybějící API v non-Java IDE.
Custom funkce se registrují v *Settings ▸ Tools ▸ JSONata Playground*; ikona by se případně mohla vrátit
jako samostatný IDEA-only modul.)

### Produkty, kde plugin poběží

Vše postavené na IntelliJ Platformě build **243+** (2024.3+):

- IntelliJ IDEA (Community i Ultimate)
- PyCharm (Community i Professional), PyCharm = DataSpell
- WebStorm, PhpStorm, GoLand, RubyMine, CLion, Rider, RustRover, Aqua
- DataGrip (DataSpell), Android Studio (Koala+ na 2024.3 baseline)
- Writerside, JetBrains Gateway (kde dává smysl)

> **Poznámka k buildu vs. runtime:** projekt se kompiluje proti `intellijIdeaCommunity("2024.3.7")`,
> ale používá **jen** base-platform API — žádný `bundledPlugin("com.intellij.java")`. Marketplace
> odvodí kompatibilní produkty z `plugin.xml` (jediná závislost je platform modul) → plugin se nabídne
> ve všech IDE. Ověřeno Plugin Verifierem: „Compatible“ napříč IDEA, PyCharm i WebStorm (krok 6).

**Doporučení:** kompatibilitu nenech jen na papíře — ověř ji Plugin Verifierem (krok 6).

---

## 3. Předpoklady

- **Účet na JetBrains Marketplace** — přihlas se na <https://plugins.jetbrains.com/> přes JetBrains Account
  (JetBrains Hub). U prvního pluginu tě vyzve k odsouhlasení [Marketplace Agreement](https://plugins.jetbrains.com/legal/terms).
- **Vendor** je v `plugin.xml` nastavený: `Petr Filip`, `petr.filip@tix.cz`, `https://tix.cz`.
  Zvaž zřízení **organizačního (vendor) účtu** na Marketplace, ať plugin nevisí pod osobním profilem.
- **JDK 21** pro build (platforma 2024.3 běží na Javě 21). Toolchain je vyřešený přenositelně:
  `settings.gradle.kts` má `foojay-resolver-convention` **1.0.0** (jediná verze kompatibilní s Gradle
  9.0 — starší 0.10.0 padá na `NoSuchFieldError: IBM_SEMERU`), který JDK 21 doprovizuje, a CI ho dodá
  přes `setup-java`. V `gradle.properties` **není** žádná natvrdo zadaná cesta. Chceš-li lokálně využít
  existující JDK/JBR 21 (a vyhnout se stažení), přidej jeho cestu do `~/.gradle/gradle.properties`
  (`org.gradle.java.installations.paths`) — Gradle JBR uvnitř `.app` balíčků sám nenajde.

---

## 4. Příprava metadat pluginu

Marketplace listing se z velké části čte z `plugin.xml` a `build.gradle.kts`. Před první publikací zkontroluj:

- **`<id>`** `cz.tix.jsonata` — neměnný identifikátor, **už ho po publikaci nikdy neměň** (je klíč k listingu).
- **`<name>`** „JSONata“ — zobrazený název.
- **`<description>`** — musí dávat smysl jako samostatný text na webu (HTML v CDATA je OK). Doplň větu,
  že funguje **napříč všemi JetBrains IDE**.
- **`<vendor>`** — vyplněno.
- **Change-notes** — krátký changelog verze. Doplň do `pluginConfiguration` v `build.gradle.kts`
  (viz níže) nebo do `plugin.xml` přes `<change-notes>`.
- **Ikona** — `src/main/resources/icons/jsonata.svg` slouží i jako ikona akce; pro **listing** Marketplace
  použij ikonu `pluginIcon.svg` (40×40 light + volitelně `pluginIcon_dark.svg`) v `src/main/resources/META-INF/`.
- **Licence** — repo obsahuje `LICENSE` (**MIT**). Přibalený engine `com.dashjoin:jsonata` zůstává pod
  Apache‑2.0 (nese si vlastní licenci ve svém jaru) — MIT pluginu to nebrání. Při uploadu vyber MIT i v listingu.
- **`sinceBuild = "243"`, `untilBuild = null`** — otevřený horní limit znamená „načítej i na novějších IDE“.
  To je v pořádku; jen Plugin Verifier nedokáže ověřit ještě neexistující buildy (řešení: pouštět verifier
  v CI při nových release IDE).

Verze je na jednom místě: `version = "0.1.0"` v `build.gradle.kts`. Element `<version>` byl z `plugin.xml`
odstraněn — build ho do plugin.xml doplní z `pluginConfiguration.version`.

---

## 5. Build a lokální ověření

```bash
# 1) Sanity-check konfigurace pluginu (since/until, závislosti, …)
./gradlew verifyPluginProjectConfiguration

# 2) Testy
./gradlew test

# 3) Distribuční ZIP → build/distributions/intellij-jsonata-<verze>.zip
./gradlew buildPlugin

# 4) Ruční odzkoušení v sandboxu (otevři JSON, zapni playground, autocomplete…)
./gradlew runIde
```

ZIP z kroku 3 je přesně to, co se nahrává na Marketplace.

### Ověření v jiném produktu lokálně (volitelné)

Chceš-li si vyzkoušet plugin např. ve WebStormu lokálně, dočasně přepni cílovou IDE v `dependencies`:

```kotlin
// místo intellijIdeaCommunity("2024.3.7")
webstorm("2024.3")        // nebo pycharmCommunity("2024.3"), goland("2024.3"), …
```

Pro samotnou publikaci to ale **není nutné** — stačí stavět proti IDEA Community a ověřit Verifierem.

---

## 6. Plugin Verifier napříč produkty (potvrzení kompatibility)

Tohle je krok, který reálně potvrdí „běží všude“. `pluginVerifier()` už je v závislostech;
přidej do `build.gradle.kts` blok `pluginVerification`:

```kotlin
intellijPlatform {
    // … (pluginConfiguration zůstává) …

    pluginVerification {
        ides {
            // POZOR: recommended() se rozpadne jen na buildy IntelliJ IDEA (IC/IU) napříč verzemi —
            // ověří kompatibilitu napříč VERZEMI, ale ne napříč PRODUKTY. Pro non-Java IDE je třeba
            // přidat je explicitně. Plugin 2.16.0 NEMÁ funkci `ide(type, version)` — produkty se
            // vybírají přes `select { }` s filtrem (FilterParameters: types/channels/sinceBuild/untilBuild).
            recommended()
            select {
                types = listOf(IntelliJPlatformType.PyCharmCommunity, IntelliJPlatformType.WebStorm)
                channels = listOf(ProductRelease.Channel.RELEASE)
                sinceBuild = "243"
                untilBuild = "243.*"   // omezí na baseline 2024.3 → po jednom buildu na produkt
            }
        }
    }
}
```

Spuštění:

```bash
./gradlew verifyPlugin
```

Verifier stáhne uvedené IDE a zkontroluje, že plugin proti nim nepoužívá žádné chybějící/odebrané API.
Reporty: `build/reports/pluginVerifier/`. Cílem je **žádné chybějící API** v non-Java IDE — to potvrdí,
že plugin je opravdu platform-only. (Aktuální stav: „Compatible“ napříč IDEA, PyCharm i WebStorm.)

> Pokud verifier začne hlásit `COMPATIBILITY_PROBLEMS` (chybějící třídy) v non-Java IDE, znamená to, že
> se do kódu vloudila Java/UAST závislost (typicky `com.intellij.psi.PsiClass`, `org.jetbrains.uast.*`) —
> izoluj ji do samostatného Java-only modulu, nebo odstraň, dřív než publikuješ. Default `failureLevel`
> obsahuje `COMPATIBILITY_PROBLEMS` i `INTERNAL_API_USAGES`, takže takový problém build shodí.

---

## 7. Podpis pluginu (signing)

Marketplace vyžaduje **podepsaný** plugin. `zipSigner()` už v závislostech je; chybí jen certifikát a config.

### 7.1 Vygenerování certifikátu

```bash
# privátní klíč (vyžádá si heslo — zapamatuj si ho)
openssl genpkey \
  -aes-256-cbc \
  -algorithm RSA \
  -out private.pem \
  -pkeyopt rsa_keygen_bits:4096

# self-signed certifikát (chain) z toho klíče
openssl req \
  -key private.pem \
  -new -x509 -days 3650 \
  -out chain.crt
```

Vznikne `private.pem` (privátní klíč) a `chain.crt` (řetězec certifikátů).
**Oba soubory + heslo drž v tajnosti, necommituj** je do gitu.

### 7.2 Konfigurace v `build.gradle.kts`

```kotlin
intellijPlatform {
    // …

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey       = providers.environmentVariable("PRIVATE_KEY")
        password         = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
}
```

### 7.3 Hodnoty proměnných prostředí

- `CERTIFICATE_CHAIN` = **obsah** `chain.crt`
- `PRIVATE_KEY` = **obsah** `private.pem`
- `PRIVATE_KEY_PASSWORD` = heslo zvolené při generování klíče

Podepsání a ověření podpisu:

```bash
export CERTIFICATE_CHAIN="$(cat chain.crt)"
export PRIVATE_KEY="$(cat private.pem)"
export PRIVATE_KEY_PASSWORD="…"

./gradlew signPlugin
./gradlew verifyPluginSignature   # ověří, že ZIP je správně podepsaný
```

Podepsaný artefakt: `build/distributions/intellij-jsonata-<verze>-signed.zip`.

> Alternativa k env proměnným: `signing { certificateChainFile = file(...); privateKeyFile = file(...) }`
> s cestami k souborům. Pro CI je ale forma přes proměnné/secrets přehlednější.

---

## 8. Publish token

1. Přihlas se na <https://plugins.jetbrains.com/> → avatar → **My Tokens**.
2. Vygeneruj token a ulož ho (zobrazí se jen jednou).
3. Zpřístupni Gradlu jako `PUBLISH_TOKEN`:

```kotlin
intellijPlatform {
    // …

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // channels: prázdné = "default" = stable kanál.
        // Pro předběžnou verzi: channels = listOf("beta")  (viz krok 11)
    }
}
```

---

## 9. Výsledný blok `build.gradle.kts` (souhrn úprav)

Do stávajícího `intellijPlatform { … }` patří `signing`, `publishing` a `pluginVerification`
(a doporučené `changeNotes` do `pluginConfiguration`). **V tomto repu už je to aplikované** — níže je
aktuální stav:

```kotlin
intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null }
        }
        changeNotes = """
            <ul>
              <li>0.1.0 — první veřejná verze: živý JSONata playground, zvýraznění syntaxe,
                  anotace chyb, autocomplete (funkce i pole JSONu) a parameter info.</li>
            </ul>
        """.trimIndent()
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey       = providers.environmentVariable("PRIVATE_KEY")
        password         = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            recommended()
            select {
                types = listOf(IntelliJPlatformType.PyCharmCommunity, IntelliJPlatformType.WebStorm)
                channels = listOf(ProductRelease.Channel.RELEASE)
                sinceBuild = "243"
                untilBuild = "243.*"
            }
        }
    }
}
```

Importy pro `pluginVerification` (nahoře v `build.gradle.kts`):
`org.jetbrains.intellij.platform.gradle.IntelliJPlatformType` a
`org.jetbrains.intellij.platform.gradle.models.ProductRelease`.

---

## 10. První publikace (ruční nahrání)

U **nového** pluginu doporučuji první verzi nahrát ručně — vyplníš listing a plugin projde moderací:

1. Postav podepsaný ZIP: `./gradlew signPlugin` → `build/distributions/…-signed.zip`.
2. Na <https://plugins.jetbrains.com/> → **Upload plugin**.
3. Nahraj podepsaný ZIP, vyber licenci, kategorii/tagy, doplň popis, screenshoty, odkaz na repo.
4. Odešli ke schválení.

**Moderace:** úplně první verze pluginu prochází ruční kontrolou JetBrains (řádově jednotky pracovních dnů).
Než projde, není veřejně viditelná. Další verze už se zveřejňují automaticky.

Po schválení v listingu zkontroluj sekci **Compatible products** — měla by uvádět celé portfolio IDE
(protože povinná je jen platform závislost). Pokud chybí, je to signál, že se do `plugin.xml`
vloudila tvrdá Java závislost.

---

## 11. Aktualizace dalších verzí (přes Gradle)

Když plugin už existuje na Marketplace, další release je jeden příkaz:

```bash
# 1) zvedni version v build.gradle.kts (a změň changeNotes)
# 2) nastav env proměnné (CERTIFICATE_CHAIN, PRIVATE_KEY, PRIVATE_KEY_PASSWORD, PUBLISH_TOKEN)
./gradlew publishPlugin
```

`publishPlugin` interně **podepíše** a **nahraje** ZIP. Požadavky:
- platný `PUBLISH_TOKEN`,
- platný podpis,
- verze, která ještě nebyla publikovaná (čísla verzí nelze recyklovat).

### Kanály (channels)

- **default** (prázdné `channels`) = stabilní kanál, vidí ho všichni.
- **beta** / **eap** = uživatel si musí přidat custom plugin repository
  (`https://plugins.jetbrains.com/plugins/<channel>/<pluginId>`). Vhodné na předběžné verze:

```kotlin
publishing {
    token    = providers.environmentVariable("PUBLISH_TOKEN")
    channels = listOf("beta")
}
```

---

## 12. Automatické releasy přes GitHub Actions

**Ano — releasy jdou plně automaticky přes GitHub.** V repu jsou připravené dva workflow:

- **`.github/workflows/build.yml`** — na každý push/PR do `main` pustí `verifyPluginProjectConfiguration`,
  `test`, `buildPlugin` a `verifyPlugin` (cross-IDE). ZIP i report verifieru nahraje jako build artifacts.
- **`.github/workflows/release.yml`** — když na GitHubu **vydáš Release** (tag `vX.Y.Z`), workflow plugin
  **podepíše a publikuje** na Marketplace (`./gradlew publishPlugin`) a podepsaný ZIP připne k Release.
  Lze spustit i ručně přes *workflow_dispatch*.

Jednorázově nastav **repository secrets** (*Settings ▸ Secrets and variables ▸ Actions*):
`CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`, `PUBLISH_TOKEN` (viz krok 7–8). Pak je celý
release jen: zvedni `version` v `build.gradle.kts` → vytvoř GitHub Release → zbytek udělá workflow sám.

> ⚠️ **Úplně první verzi** je rozumné nahrát ručně (krok 10) kvůli moderaci a vyplnění listingu.
> Automatizace přebírá další releasy, jakmile plugin na Marketplace existuje.

Jádro `release.yml` (zkráceně — plná verze je v repu):

```yaml
name: Release
on:
  release:
    types: [published]
jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - name: Verify
        run: ./gradlew verifyPlugin
      - name: Publish
        env:
          CERTIFICATE_CHAIN:    ${{ secrets.CERTIFICATE_CHAIN }}
          PRIVATE_KEY:          ${{ secrets.PRIVATE_KEY }}
          PRIVATE_KEY_PASSWORD: ${{ secrets.PRIVATE_KEY_PASSWORD }}
          PUBLISH_TOKEN:        ${{ secrets.PUBLISH_TOKEN }}
        run: ./gradlew publishPlugin
```

> JDK 21 na CI řeší `setup-java` výše; `gradle.properties` už žádnou natvrdo zadanou cestu neobsahuje,
> takže není co přebíjet. Na strojích bez JDK 21 ho doprovizuje `foojay-resolver` (viz krok 3).

---

## 13. Checklist před publikací

Hotovo v repu (✅) / zbývá na tobě (☐):

- [x] `<id>`, `<name>`, `<vendor>` v `plugin.xml` finální (`<id>` `cz.tix.jsonata` se už nikdy nemění)
- [x] `<description>` dává smysl jako samostatný text; zmíněno „funguje napříč JetBrains IDE“
- [x] `changeNotes` pro aktuální verzi
- [x] verze sjednocená na jednom místě (`build.gradle.kts`)
- [x] `LICENSE` (MIT) v repu — *licenci vybrat i v listingu při uploadu*
- [x] listing ikona `META-INF/pluginIcon.svg` (40×40) + dark varianta
- [x] `./gradlew verifyPluginProjectConfiguration` bez chyb *(ověřeno)*
- [x] `./gradlew test` zelené *(ověřeno)*
- [x] `./gradlew verifyPlugin` — **Compatible** napříč IDEA + PyCharm + WebStorm, žádné chybějící/interní API *(ověřeno; běží i v CI)*
- [ ] `./gradlew signPlugin && ./gradlew verifyPluginSignature` OK *(potřebuje certifikát)*
- [ ] secrets nastavené (`CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`, `PUBLISH_TOKEN`) — lokálně i jako GitHub secrets
- [ ] první verze nahraná ručně přes web → prošla moderací
- [ ] po schválení zkontrolováno „Compatible products“ = celé portfolio IDE

---

## Odkazy

- Marketplace: <https://plugins.jetbrains.com/>
- Publikace pluginu (docs): <https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html>
- Podpis pluginu: <https://plugins.jetbrains.com/docs/intellij/plugin-signing.html>
- Plugin Verifier: <https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html>
- Kompatibilita / dependencies & modules: <https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html>
- IntelliJ Platform Gradle Plugin 2.x: <https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html>
</content>
</invoke>
