import io.github.noahhhx.mimos.gradle.GENERATE_DESIGN_TOKENS
import io.github.noahhhx.mimos.gradle.GenerateDesignTokens
import io.github.noahhhx.mimos.gradle.mimosVersion

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
    val version = mimosVersion()
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

// The cases the web's tests also read (libraries/test-cases/README.md), embedded as Kotlin strings:
// a unit test has no way to reach a file outside the module.
val testCasesDirectory = rootProject.layout.projectDirectory.dir("../../libraries/test-cases")
val generatedTestCases = layout.buildDirectory.dir("generated/testcases")

val generateTestCases by tasks.registering {
    val input = testCasesDirectory
    val output = generatedTestCases
    inputs.dir(input)
    outputs.dir(output)
    doLast {
        // A string constant holds at most 65535 bytes, so each file goes in as joined chunks.
        fun literal(text: String) =
            "\"" +
                text
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\$", "\\\$")
                    .replace("\n", "\\n") +
                "\""
        val entries =
            input.asFile
                .listFiles { file -> file.extension == "json" }!!
                .sortedBy { it.name }
                .joinToString(",\n") { file ->
                    val chunks = file.readText().chunked(10_000).joinToString(", ") { literal(it) }
                    "        ${literal(file.name)} to listOf($chunks).joinToString(\"\")"
                }
        val target = output.get().file("io/github/noahhhx/mimos/testcases/TestCases.kt").asFile
        target.parentFile.mkdirs()
        target.writeText(
            "package io.github.noahhhx.mimos.testcases\n\n" +
                "/** Generated from libraries/test-cases by the generateTestCases task. */\n" +
                "internal object TestCases {\n" +
                "    val files: Map<String, String> =\n        mapOf(\n$entries\n        )\n" +
                "\n    fun read(name: String): String =\n" +
                "        files[name] ?: error(\"no test case file named \$name\")\n" +
                "}\n"
        )
    }
}

// Lint reads the unit-test sources too, and does not learn from the source set that this task makes
// them.
tasks
    .matching { it.name.startsWith("lintAnalyze") || it.name.endsWith("LintModel") }
    .configureEach { dependsOn(generateTestCases) }

// Release builds read each source directory's sibling baselineProfiles, a path the generated
// directories' tasks do not produce, so Gradle cannot see the dependency on its own.
tasks
    .matching { it.name.endsWith("ArtProfile") }
    .configureEach {
        dependsOn("openApiGenerate", generateAppVersion, GENERATE_DESIGN_TOKENS)
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
        commonTest {
            kotlin.srcDir(generateTestCases.map { generatedTestCases.get() })
        }
        androidMain.dependencies { implementation(libs.androidx.activity.compose) }
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
