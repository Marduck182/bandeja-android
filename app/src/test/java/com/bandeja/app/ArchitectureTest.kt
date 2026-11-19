package com.bandeja.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the layering of docs/03-architecture.md true: ui -> domain <- data.
 * It reads the source files, so a wrong import fails the build instead of waiting for a code review.
 */
class ArchitectureTest {
    private val root = File("src/main/java/com/bandeja/app")

    private fun importsOf(layer: String): List<Pair<String, String>> =
        File(root, layer).walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().filter { it.startsWith("import ") }.map { file.name to it.removePrefix("import ").trim() }
        }.toList()

    private fun assertNoImports(layer: String, vararg forbiddenPrefixes: String) {
        val imports = importsOf(layer)
        assertTrue("no sources found for layer '$layer' (wrong working directory?)", imports.isNotEmpty())
        val violations = imports.filter { (_, import) -> forbiddenPrefixes.any { import.startsWith(it) } }
        assertTrue("layer '$layer' must not import ${forbiddenPrefixes.toList()}: $violations", violations.isEmpty())
    }

    @Test
    fun `domain is plain Kotlin and knows neither data nor ui`() =
        assertNoImports("domain", "android.", "androidx.", "com.bandeja.app.data", "com.bandeja.app.ui", "com.bandeja.app.di")

    @Test
    fun `data does not depend on ui`() = assertNoImports("data", "com.bandeja.app.ui", "androidx.compose")

    @Test
    fun `ui reaches the database only through repositories`() =
        assertNoImports("ui", "androidx.room", "com.bandeja.app.data.local", "com.bandeja.app.data.remote")
}
