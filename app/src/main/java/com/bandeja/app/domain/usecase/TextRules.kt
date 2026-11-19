package com.bandeja.app.domain.usecase

/** Limits and cleaning for text that comes from the user, applied in one place for every entry path. */
object TextRules {
    const val MAX_TITLE = 200
    const val MAX_NOTES = 5000
    const val MAX_TAG = 30
    const val MAX_TAGS = 20

    fun title(raw: String): String = raw.collapseWhitespace().take(MAX_TITLE)

    fun notes(raw: String): String = raw.trim().take(MAX_NOTES)

    /** `#Receta  Fácil` → `receta fácil`; blanks and repeats dropped; capped in count and length. */
    fun tags(raw: Collection<String>): List<String> =
        raw.asSequence()
            .map { it.trim().trimStart('#').collapseWhitespace().lowercase().take(MAX_TAG).trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_TAGS)
            .toList()

    private fun String.collapseWhitespace(): String = replace(Regex("\\s+"), " ").trim()
}
