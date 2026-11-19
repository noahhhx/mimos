plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("mimos.no-play-services")
    id("mimos.licences")
}

val generateLicences =
    tasks.named<io.github.noahhhx.mimos.gradle.GenerateLicences>("generateLicences") {
        packageName = "io.github.noahhhx.mimos"
        // The fonts come from the web app's files, not Maven (apps/web/src/fonts/README.md).
        extra.addAll(
            listOf("Newsreader", "Public Sans").map {
                io.github.noahhhx.mimos.gradle.Notice("$it (font)", emptyList(), listOf("OFL-1.1"))
            }
        )
    }

androidComponents {
    onVariants { variant ->
        variant.sources.kotlin?.addGeneratedSourceDirectory(
            generateLicences,
            io.github.noahhhx.mimos.gradle.GenerateLicences::outputDir,
        )
    }
}

val (compileSdkMajor, compileSdkMinor) =
    libs.versions.compileSdk.get().split(".").map(String::toInt)

android {
    namespace = "io.github.noahhhx.mimos"
    compileSdk { version = release(compileSdkMajor) { minorApiLevel = compileSdkMinor } }
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        applicationId = "io.github.noahhhx.mimos"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = providers.gradleProperty("mimos.version").get()
        // AppAuth claims this whole scheme for the sign-in redirect. App links such as
        // invites use the separate io.github.noahhhx.mimos scheme (ADR-0029).
        manifestPlaceholders["appAuthRedirectScheme"] = "io.github.noahhhx.mimos.oauth"
    }

    buildTypes { debug { applicationIdSuffix = ".debug" } }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        // Checks that compare against the newest releases online turn a passing
        // commit red later; dependency updates are a deliberate change instead.
        disable +=
            setOf(
                "GradleDependency",
                "AndroidGradlePluginVersion",
                "NewerVersionAvailable",
                "OldTargetApi",
            )
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.appauth)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel)
    // Newer than activity-compose brings: 2.9's lint checks fail to load in this
    // lint (ObsoleteLintCustomCheck).
    implementation(libs.androidx.lifecycle.runtime)
}
