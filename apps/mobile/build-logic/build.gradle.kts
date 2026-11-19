plugins { `kotlin-dsl` }

gradlePlugin {
    plugins {
        create("noPlayServices") {
            id = "mimos.no-play-services"
            implementationClass = "io.github.noahhhx.mimos.gradle.NoPlayServicesPlugin"
        }
        create("designTokens") {
            id = "mimos.design-tokens"
            implementationClass = "io.github.noahhhx.mimos.gradle.DesignTokensPlugin"
        }
        create("licences") {
            id = "mimos.licences"
            implementationClass = "io.github.noahhhx.mimos.gradle.LicencesPlugin"
        }
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }
