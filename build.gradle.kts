plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    kotlin("multiplatform").apply(false)
    id("com.android.application").apply(false)
    id("com.android.library").apply(false)
    id("org.jetbrains.compose").apply(false)
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.0.1")
    }

    // Dependency locking has to be enabled in the build script, otherwise
    // `./gradlew --write-locks` silently produces no gradle.lockfile at all.
    // Locking is only enforced when a lockfile is present, so enabling this
    // does not break builds that have no lockfile yet.
    dependencyLocking {
        lockAllConfigurations()
    }
}
