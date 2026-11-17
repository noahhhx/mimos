plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("mimos.no-play-services")
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
    // Newer than activity-compose brings: 2.9's lint checks fail to load in this
    // lint (ObsoleteLintCustomCheck).
    implementation(libs.androidx.lifecycle.runtime)
}
