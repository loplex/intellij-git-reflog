import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

val pluginVersion = providers.gradleProperty("version")

kotlin {
    // Said here rather than left to whichever JDK happens to run Gradle. On CI that is the one the workflow
    // installs and the build is reproducible by accident; off CI it is whatever the developer has, and a build
    // whose bytecode depends on that is one whose output nobody can compare. 21 is what the platform this
    // compiles against requires.
    jvmToolchain(21)
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.3.5")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.VCS)

        // Git tool window integration: ChangesViewContentProvider + git command execution.
        bundledPlugin("Git4Idea")
    }
}

changelog {
    groups.empty()
    repositoryUrl = "https://github.com/loplex/intellij-git-reflog"

    // What a release tag carries in front of the version, declared once in gradle.properties and read from
    // there by the release tooling too. Without this the plugin applies a 'v' of its own, which is this
    // project's prefix only by coincidence; with the fact written down twice, the links and the tags could come
    // to disagree and nothing would say so. No default, for the same reason the release tooling has none: a
    // guessed prefix is wrong in silence, and the two readers of the declaration have to fail the same way when
    // it is missing.
    versionPrefix = providers.gradleProperty("tagPrefix").orNull
        ?: error("gradle.properties does not say, in tagPrefix, what release tags are called")
}

/**
 * The change notes of the version being built, rendered while the build is configured rather than while it runs.
 *
 * The changelog extension holds on to the project, which the configuration cache refuses to store, so a lambda
 * that reaches for it when a task executes cannot be cached. Reading it here leaves a plain string behind, which
 * can be.
 *
 * What that costs is the cache's own knowledge of when to throw the string away: the extension reads
 * CHANGELOG.md with plain IO, which the cache does not see, so a changed changelog would otherwise go on being
 * reported as the old one. Reading the file through a value source as well is what tells it.
 */
val changeNotesHtml: String = run {
    providers.fileContents(layout.projectDirectory.file("CHANGELOG.md")).asText.orNull
    with(changelog) {
        renderItem(
            (getOrNull(pluginVersion.get()) ?: getUnreleased())
                .withHeader(false)
                .withEmptySections(false),
            Changelog.OutputType.HTML,
        )
    }
}

intellijPlatform {
    pluginConfiguration {
        version = pluginVersion

        ideaVersion {
            // 2025.3, the platform the tab is built against and the first with the APIs it uses.
            sinceBuild = "253"

            // Left unbounded on purpose. An upper bound turns every IDE update into a release the plugin needs
            // in order to keep working, and there is nothing known about this tab that a later IDE breaks -
            // `verifyPlugin` is what would find such a thing, and it says so before the bound does.
            untilBuild = provider { null }
        }

        // The section of CHANGELOG.md for the version being built, so that what the Marketplace shows as the
        // change notes and what the repository records are one text rather than two that drift.
        changeNotes = provider { changeNotesHtml }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")

        // A version with no pre-release suffix goes to the default channel, which is what users get offered;
        // `0.2.0-beta.1` goes to a `beta` channel, which they have to subscribe to. Derived from the version
        // rather than passed in, so that a release cannot be published to the wrong one by a typo at the
        // command line.
        channels = pluginVersion.map {
            listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" })
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    publishPlugin {
        // The platform plugin makes this task depend on patchChangelog whenever the changelog plugin is applied,
        // so that a publish closes the Unreleased section on its way. Here the release closes it itself, in
        // prepare-release.yml, before anything is built. Left in, a publishPlugin run by hand from a branch with
        // work under [Unreleased] would rewrite CHANGELOG.md as a side effect - before the token check or the
        // marker check below could stop it, since a task's dependencies run before the task does.
        setDependsOn(dependsOn.filterNot { it == "patchChangelog" })

        // -SNAPSHOT says the version has not been released, so it is precisely what must not be published. The
        // release workflows never run this task - Publish uploads the accepted archive over the Marketplace API
        // - so it is only ever reached by a publish run by hand, from a branch that may still carry the marker,
        // which is exactly when it is worth refusing.
        val declared = pluginVersion.get()
        doFirst {
            require(!declared.endsWith("-SNAPSHOT")) {
                "$declared is a version being worked on, not one to publish"
            }
        }
    }
}
