import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

val pluginVersion = providers.gradleProperty("version")

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
        // Publishes an archive built earlier rather than one built here, so that what reaches the Marketplace is
        // the file that was attached to the GitHub release and could be downloaded and tried before it was
        // accepted. Without the property the task publishes its own build, as it does by default.
        val archive = providers.gradleProperty("publishArchive")
        if (archive.isPresent) {
            archiveFile = layout.projectDirectory.file(archive.get())
        }
    }
}
