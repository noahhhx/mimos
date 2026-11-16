plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    // The KMP library plugin gives lint no main-source analysis; this adds it,
    // so androidApp's lint (checkDependencies) reads commonMain.
    alias(libs.plugins.android.lint)
}

val (compileSdkMajor, compileSdkMinor) =
    libs.versions.compileSdk.get().split(".").map(String::toInt)

kotlin {
    android {
        namespace = "io.github.noahhhx.mimos.shared"
        compileSdk { version = release(compileSdkMajor) { minorApiLevel = compileSdkMinor } }
        minSdk = libs.versions.minSdk.get().toInt()
        buildToolsVersion = libs.versions.buildTools.get()
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
