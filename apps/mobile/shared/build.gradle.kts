import io.github.noahhhx.mimos.gradle.GENERATE_DESIGN_TOKENS
import io.github.noahhhx.mimos.gradle.GenerateDesignTokens

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    // The KMP library plugin gives lint no main-source analysis; this adds it,
    // so androidApp's lint (checkDependencies) reads commonMain.
    alias(libs.plugins.android.lint)
    alias(libs.plugins.openapi.generator)
    id("mimos.design-tokens")
}

val (compileSdkMajor, compileSdkMinor) =
    libs.versions.compileSdk.get().split(".").map(String::toInt)

val generatedApi = layout.buildDirectory.dir("generated/openapi")
val generatedVersion = layout.buildDirectory.dir("generated/version")

val generateAppVersion by tasks.registering {
    val version = providers.gradleProperty("mimos.version")
    val output = generatedVersion
    inputs.property("version", version)
    outputs.dir(output)
    doLast {
        val file = output.get().file("io/github/noahhhx/mimos/instance/AppVersionName.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package io.github.noahhhx.mimos.instance\n\n" +
                "internal const val APP_VERSION_NAME = \"${version.get()}\"\n"
        )
    }
}

kotlin {
    android {
        namespace = "io.github.noahhhx.mimos.shared"
        compileSdk { version = release(compileSdkMajor) { minorApiLevel = compileSdkMinor } }
        minSdk = libs.versions.minSdk.get().toInt()
        buildToolsVersion = libs.versions.buildTools.get()
        // Compose Resources packages the fonts as Android assets only with this on.
        androidResources { enable = true }
        withHostTest {}
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(
                tasks.named("openApiGenerate").map {
                    generatedApi.get().dir("src/commonMain/kotlin")
                }
            )
            kotlin.srcDir(generateAppVersion.map { generatedVersion.get() })
            kotlin.srcDir(
                tasks.named<GenerateDesignTokens>(GENERATE_DESIGN_TOKENS).flatMap { it.outputDir }
            )
        }
        commonMain.dependencies {
            api(libs.ktor.client.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

// The fonts' TTFs: apps/web/src/fonts/README.md, "Mobile".
compose.resources {
    packageOfResClass = "io.github.noahhhx.mimos.resources"
    publicResClass = false
}

openApiGenerate {
    generatorName = "kotlin"
    library = "multiplatform"
    inputSpec = rootProject.layout.projectDirectory.file("../../contracts/api/openapi.yaml")
    outputDir = generatedApi
    cleanupOutput = true
    generateApiTests = false
    generateModelTests = false
    generateApiDocumentation = false
    generateModelDocumentation = false
    packageName = "io.github.noahhhx.mimos.api"
    templateDir = layout.projectDirectory.dir("openapi-templates")
    typeMappings = mapOf("object" to "JsonObject")
    schemaMappings = mapOf("PanelBlock" to "io.github.noahhhx.mimos.client.PanelBlock")
    inlineSchemaOptions = mapOf("RESOLVE_INLINE_ENUMS" to "true")
    importMappings = mapOf("JsonObject" to "kotlinx.serialization.json.JsonObject")
    configOptions =
        mapOf(
            "hideGenerationTimestamp" to "true",
            "dateLibrary" to "kotlinx-datetime",
            "enumUnknownDefaultCase" to "true",
        )
}
