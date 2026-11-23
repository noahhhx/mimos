import io.github.noahhhx.mimos.gradle.ReleaseVersion
import io.github.noahhhx.mimos.gradle.mimosVersion

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

val appVersion = mimosVersion()

val (compileSdkMajor, compileSdkMinor) =
    libs.versions.compileSdk.get().split(".").map(String::toInt)

/** Signs with the keystore at [path], whose passwords and alias are [prefix]'s other variables. */
fun com.android.build.api.dsl.ApkSigningConfig.signWith(path: String, prefix: String) {
    fun required(name: String) =
        // CI passes a missing secret as an empty string.
        providers.environmentVariable("${prefix}_$name").orNull?.ifEmpty { null }
            ?: throw GradleException("${prefix}_KEYSTORE is set, so ${prefix}_$name must be too")
    storeFile = file(path)
    storePassword = required("KEYSTORE_PASSWORD")
    keyAlias = required("KEY_ALIAS")
    keyPassword = required("KEY_PASSWORD")
}

android {
    namespace = "io.github.noahhhx.mimos"
    compileSdk { version = release(compileSdkMajor) { minorApiLevel = compileSdkMinor } }
    buildToolsVersion = libs.versions.buildTools.get()

    defaultConfig {
        applicationId = "io.github.noahhhx.mimos"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionName = appVersion.get()
        versionCode = ReleaseVersion.parse(appVersion.get()).versionCode
        // AppAuth claims this whole scheme for the sign-in redirect. App links such as
        // invites use the separate io.github.noahhhx.mimos scheme (ADR-0029).
        manifestPlaceholders["appAuthRedirectScheme"] = "io.github.noahhhx.mimos.oauth"
    }

    // Keys come from the environment, never the repository (docs/harness/releasing.md). Without
    // them, debug builds use the SDK's own key and release builds stay unsigned.
    fun env(name: String): String? = providers.environmentVariable(name).orNull?.ifEmpty { null }

    signingConfigs {
        getByName("debug") { env("MIMOS_DEBUG_KEYSTORE")?.let { signWith(it, "MIMOS_DEBUG") } }
        env("MIMOS_RELEASE_KEYSTORE")?.let { create("release") { signWith(it, "MIMOS_RELEASE") } }
    }

    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
        // The release, minified the same way and not debuggable (R8 barely shrinks a debuggable
        // build), but with the debug build's id, key, and local-stack allowances, so CI's flows
        // run on what ships (docs/harness/releasing.md).
        create("minified") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    sourceSets { getByName("minified") { res.directories.add("src/debug/res") } }

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
