import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "ij-git-reflog"

pluginManagement {
    plugins {
        // Pinned low enough for the compiled plugin's coroutines to be
        // resumable in IntelliJ IDEA 2025.1, not by the platform it compiles
        // against. The plugin ships no stdlib of its own
        // (kotlin.stdlib.default.dependency in gradle.properties), so its
        // coroutines are resumed by whichever stdlib the running IDE bundles.
        // A compiler that writes a newer @DebugMetadata version than that
        // stdlib can read kills every resumption, because the IDE installs
        // coroutine debug probes.
        // Measured against IntelliJ IDEA 2025.1, which bundles
        // Kotlin 2.1.10: 2.4.10 and 2.3.20 both die on "Debug metadata
        // version mismatch. Expected: 1, got 2", and the suite hangs rather
        // than fails - the first runBlocking never returns - while code
        // compiled by 2.2.20 resumes. Nothing between 2.2.20 and 2.3.20 was
        // measured, so the boundary lies somewhere between the two.
        // Compiling against a newer platform is not the problem: metadata
        // version 1 is read by every later stdlib. The Plugin Verifier does
        // not catch any of this - it calls every target IDE compatible
        // either way. When sinceBuild moves, read
        // plugins/Kotlin/kotlinc/build.txt out of the oldest IDE then
        // supported and measure again before raising this pin.
        id("org.jetbrains.kotlin.jvm") version "2.2.20"
        id("org.jetbrains.changelog") version "2.5.0"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.19.0"
}

dependencyResolutionManagement {
    // Configure all projects' repositories
    repositories {
        mavenCentral()

        // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
        intellijPlatform {
            defaultRepositories()
        }
    }
}
