plugins {
    // Auto-provision the JDK 21 toolchain on any machine (CI / fresh checkout) that doesn't
    // already have one, instead of relying on a hardcoded local JBR path in gradle.properties.
    // 1.0.0 is the first release compatible with Gradle 9 (shaded deps fix the IBM_SEMERU clash).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "intellij-jsonata"
