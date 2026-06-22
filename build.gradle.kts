import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "cz.tix.jsonata"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Target IDE we compile/run against. Plugin still loads on 2024.3+ (sinceBuild 243).
        intellijIdeaCommunity("2024.3.7")
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
        // Compile-time only: UAST + PSI for the (optional) gutter icon on provider classes.
        bundledPlugin("com.intellij.java")
    }

    // JSONata engine — faithful Java port of jsonata.js (Apache-2.0). Bundled into the plugin.
    implementation("com.dashjoin:jsonata:0.9.8")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // JUnit 4/3 on the COMPILE classpath: BasePlatformTestCase extends junit.framework.TestCase,
    // and the IntelliJ Platform test runtime references JUnit 4. Vintage engine runs these tests.
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "243"
            // No hard upper bound — plugin keeps loading on newer IDEs.
            untilBuild = provider { null }
        }
        changeNotes = """
            <ul>
              <li>0.1.0 — first public release: live JSONata playground, syntax highlighting,
                  error annotations, autocomplete (builtins + JSON field keys) and parameter info.</li>
            </ul>
        """.trimIndent()
    }

    // Plugin signing — required by JetBrains Marketplace. Values come from env vars / CI secrets.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    // Marketplace publishing. PUBLISH_TOKEN from plugins.jetbrains.com ▸ My Tokens.
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // Empty channels = "default" (stable). Use listOf("beta") for pre-releases.
    }

    // Verify cross-product compatibility (PyCharm, WebStorm, GoLand, …), not just IntelliJ IDEA.
    pluginVerification {
        ides {
            recommended()
        }
    }
}

kotlin {
    jvmToolchain(21)
}

// Building the searchable-options index launches a headless IDE we don't need for distribution.
tasks.named("buildSearchableOptions") {
    enabled = false
}
