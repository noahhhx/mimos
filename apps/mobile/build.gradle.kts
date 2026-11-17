plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.openapi.generator) apply false
}

// check applies the formatting, as the backend's build does; CI runs spotlessCheck.
spotless {
    isEnforceCheck = false
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
}

subprojects {
    tasks
        .matching { it.name == "check" }
        .configureEach { dependsOn(rootProject.tasks.named("spotlessApply")) }
}

tasks.named("check") { dependsOn(gradle.includedBuild("build-logic").task(":check")) }
