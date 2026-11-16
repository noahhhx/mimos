plugins { `kotlin-dsl` }

gradlePlugin {
    plugins {
        create("noPlayServices") {
            id = "mimos.no-play-services"
            implementationClass = "io.github.noahhhx.mimos.gradle.NoPlayServicesPlugin"
        }
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }
