package io.github.noahhhx.mimos.gradle

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** A Maven coordinate: `group:name:version`. */
data class Coordinates(val group: String, val name: String, val version: String) {
    override fun toString() = "$group:$name:$version"
}

/** What the app needs from a POM: its licences, else the parent that may declare them. */
data class Pom(val licences: List<String>, val parent: Coordinates?)

fun readPom(file: File): Pom {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = false
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    val root = factory.newDocumentBuilder().parse(file).documentElement
    val licences =
        root.child("licenses")?.children("license").orEmpty().mapNotNull { licence ->
            licence.text("name") ?: licence.text("url")
        }
    val parent =
        root.child("parent")?.let {
            Coordinates(
                it.text("groupId") ?: return@let null,
                it.text("artifactId") ?: return@let null,
                it.text("version") ?: return@let null,
            )
        }
    return Pom(licences, parent)
}

private fun Element.children(tag: String): List<Element> =
    (0 until childNodes.length).map(childNodes::item).filterIsInstance<Element>().filter {
        it.tagName == tag
    }

private fun Element.child(tag: String): Element? = children(tag).firstOrNull()

private fun Element.text(tag: String): String? = child(tag)?.textContent?.trim()?.ifEmpty { null }

/**
 * A licence as POMs name it, in SPDX's words where it is a common one: POMs spell Apache 2.0 a
 * dozen ways.
 */
fun spdx(name: String): String {
    val plain = name.lowercase()
    return when {
        "apache" in plain && "2" in plain -> "Apache-2.0"
        plain == "mit" || "mit license" in plain || "opensource.org/licenses/mit" in plain -> "MIT"
        "bsd" in plain && ("3" in plain || "new" in plain || "revised" in plain) -> "BSD-3-Clause"
        "bsd" in plain && "2" in plain -> "BSD-2-Clause"
        else -> name
    }
}

/** A row of the app's licences: one Maven group, its versions, and the licences in it. */
data class Notice(val name: String, val versions: List<String>, val licences: List<String>) :
    java.io.Serializable

/**
 * The app's components grouped by Maven group, which is how their projects release them: Compose's
 * dozens of modules are one row. A component with no licence is a build failure, since the app
 * cannot show what it does not know.
 */
fun notices(licences: Map<Coordinates, List<String>>): List<Notice> {
    val unknown = licences.filterValues { it.isEmpty() }.keys
    require(unknown.isEmpty()) {
        "No licence found in the POM or its parents of: ${unknown.sortedBy { it.toString() }.joinToString(", ")}"
    }
    return licences.entries
        .groupBy({ it.key.group }, { it })
        .map { (group, entries) ->
            Notice(
                name = group,
                versions = entries.map { it.key.version }.distinct().sorted(),
                licences = entries.flatMap { it.value }.map(::spdx).distinct().sorted(),
            )
        }
        .sortedBy { it.name }
}
