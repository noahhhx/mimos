package io.github.noahhhx.mimos.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.language.base.plugins.LifecycleBasePlugin

internal val bannedGroups =
    listOf(
        "com.google.android.gms",
        "com.google.firebase",
        "com.google.mlkit",
        "com.google.android.play",
        "com.android.billingclient",
    )

internal fun isBanned(group: String): Boolean = bannedGroups.any {
    group == it || group.startsWith("$it.")
}

const val RELEASE_RUNTIME_CLASSPATH = "releaseRuntimeClasspath"

class NoPlayServicesPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(LifecycleBasePlugin::class.java)
        val task =
            project.tasks.register("checkNoPlayServices", NoPlayServicesCheck::class.java) {
                group = LifecycleBasePlugin.VERIFICATION_GROUP
                description =
                    "Fails when $RELEASE_RUNTIME_CLASSPATH holds a banned Google artifact."
                rootComponent.set(
                    project.configurations.named(RELEASE_RUNTIME_CLASSPATH).flatMap {
                        it.incoming.resolutionResult.rootComponent
                    }
                )
            }
        project.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(task) }
    }
}

abstract class NoPlayServicesCheck : DefaultTask() {
    @get:Input abstract val rootComponent: Property<ResolvedComponentResult>

    @TaskAction
    fun check() {
        val banned =
            reachableModules(rootComponent.get())
                .filter { isBanned(it.group) }
                .map { it.displayName }
        if (banned.isNotEmpty()) {
            throw GradleException(
                "Google Play Services are banned from the app (ADR-0029). " +
                    "$RELEASE_RUNTIME_CLASSPATH holds: ${banned.sorted().joinToString(", ")}"
            )
        }
    }
}

private fun reachableModules(root: ResolvedComponentResult): List<ModuleComponentIdentifier> {
    val seen = mutableSetOf<ResolvedComponentResult>()
    val queue = ArrayDeque(listOf(root))
    while (queue.isNotEmpty()) {
        val component = queue.removeFirst()
        if (seen.add(component)) {
            component.dependencies.filterIsInstance<ResolvedDependencyResult>().mapTo(queue) {
                it.selected
            }
        }
    }
    return seen.map { it.id }.filterIsInstance<ModuleComponentIdentifier>()
}
