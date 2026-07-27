package com.esusic.recoverytracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses the shipped resource XML directly: a missing German key or a format specifier that
 * drifted between the two files is a crash at runtime, not something the compiler can see.
 */
class TranslationCompletenessTest {

    private data class Resource(val name: String, val texts: List<String>)

    private val specifier = Regex("%(\\d+\\$)?[-+ 0,(#]*\\d*(\\.\\d+)?[a-zA-Z]")

    private val defaults = parse("src/main/res/values/strings.xml", translatableOnly = true)
    private val german = parse("src/main/res/values-de/strings.xml", translatableOnly = false)

    @Test
    fun `every translatable default string has a German counterpart`() {
        val missing = defaults.keys - german.keys
        assertTrue("Missing German translations: ${missing.sorted()}", missing.isEmpty())
    }

    @Test
    fun `German has no keys the default locale does not define`() {
        val extra = german.keys - defaults.keys
        assertTrue("Orphaned German keys: ${extra.sorted()}", extra.isEmpty())
    }

    @Test
    fun `format specifiers match between the default and German copy`() {
        defaults.forEach { (name, source) ->
            val translation = german.getValue(name)
            assertEquals(
                "Item count differs for $name",
                source.texts.size,
                translation.texts.size
            )
            source.texts.zip(translation.texts).forEach { (english, deutsch) ->
                assertEquals(
                    "Format specifiers differ for $name: \"$english\" vs \"$deutsch\"",
                    specifiers(english),
                    specifiers(deutsch)
                )
            }
        }
    }

    @Test
    fun `the default resource file is actually being read`() {
        assertTrue(defaults.size > 200)
        assertTrue(defaults.containsKey("home_core_metrics"))
        // app_name is translatable="false" and must stay out of the German file.
        assertTrue("app_name" !in defaults)
        assertTrue("app_name" !in german)
    }

    private fun specifiers(text: String): List<String> =
        specifier.findAll(text.replace("%%", "")).map { it.value }.toList()

    private fun parse(path: String, translatableOnly: Boolean): Map<String, Resource> {
        val file = File(path)
        assertTrue("Missing resource file: ${file.absolutePath}", file.isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val resources = mutableMapOf<String, Resource>()
        val root = document.documentElement
        val children = root.childNodes
        for (index in 0 until children.length) {
            val node = children.item(index) as? Element ?: continue
            val name = node.getAttribute("name").takeIf { it.isNotEmpty() } ?: continue
            if (translatableOnly && node.getAttribute("translatable") == "false") continue
            val texts = when (node.tagName) {
                "string" -> listOf(node.textContent)
                "plurals" -> pluralItems(node)
                else -> continue
            }
            resources[name] = Resource(name, texts)
        }
        return resources
    }

    // Quantities are compared in a fixed order so "one" is never matched against "other".
    private fun pluralItems(plurals: Element): List<String> {
        val items = plurals.getElementsByTagName("item")
        val byQuantity = mutableMapOf<String, String>()
        for (index in 0 until items.length) {
            val item = items.item(index) as Element
            byQuantity[item.getAttribute("quantity")] = item.textContent
        }
        return byQuantity.toSortedMap().values.toList()
    }
}
