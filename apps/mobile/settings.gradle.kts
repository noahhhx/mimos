pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

// Patched floors for modules with security advisories that the Android Gradle Plugin, its lint
// tool, openapi-generator, and CameraX pull in older. A module below its floor is raised to it,
// keeping Guava's "-android" flavour; a newer version is left alone.
val securityFloors =
    mapOf(
        "com.fasterxml.jackson.core:jackson-core" to "2.22.3",
        "com.fasterxml.jackson.core:jackson-databind" to "2.22.3",
        "com.github.jknack:handlebars" to "4.5.2",
        "com.google.guava:guava" to "33.7.2-jre",
        "org.apache.commons:commons-lang3" to "3.18.0",
        "org.apache.httpcomponents:httpclient" to "4.5.13",
        "org.bitbucket.b_c:jose4j" to "0.9.6",
        "org.bouncycastle:bcpkix-jdk18on" to "1.85",
        "org.bouncycastle:bcprov-jdk18on" to "1.85",
        "org.bouncycastle:bcutil-jdk18on" to "1.85",
        "org.jdom:jdom2" to "2.0.6.1",
    )

fun versionParts(version: String) =
    version.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

fun isBelow(version: String, floor: String): Boolean {
    val (a, b) = versionParts(version) to versionParts(floor)
    for (i in 0 until maxOf(a.size, b.size)) {
        val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (diff != 0) return diff < 0
    }
    return false
}

fun ResolutionStrategy.raiseToSecurityFloors() = eachDependency {
    val floor = securityFloors["${requested.group}:${requested.name}"] ?: return@eachDependency
    val version = requested.version ?: return@eachDependency
    if (isBelow(version, floor)) {
        val android = version.endsWith("-android") && floor.endsWith("-jre")
        useVersion(if (android) floor.replace("-jre", "-android") else floor)
        because("security advisory floor")
    }
}

gradle.beforeProject {
    buildscript.configurations.configureEach { resolutionStrategy.raiseToSecurityFloors() }
    configurations.configureEach { resolutionStrategy.raiseToSecurityFloors() }
}

rootProject.name = "mimos-mobile"

include(":shared", ":androidApp")
