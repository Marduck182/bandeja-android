package com.bandeja.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The ten colors a user can give a category. Stored by [key], never as a number, so tones can be retuned later
 * without a data migration. Each accent has a container and an "on" color per theme (contrast checked by tests).
 */
enum class CategoryAccent(
    val key: String,
    val label: String,
    val lightContainer: Color,
    val lightOn: Color,
    val darkContainer: Color,
    val darkOn: Color,
) {
    Coral("coral", "Coral", Color(0xFFFFDAD2), Color(0xFF7A1F0F), Color(0xFF5A2218), Color(0xFFFFDAD2)),
    Amber("amber", "Ámbar", Color(0xFFFFE7B8), Color(0xFF5C3B00), Color(0xFF5C3D00), Color(0xFFFFE7B8)),
    Lime("lime", "Lima", Color(0xFFE2F2B0), Color(0xFF2F4000), Color(0xFF3B4A0A), Color(0xFFE2F2B0)),
    Emerald("emerald", "Esmeralda", Color(0xFFC4F0D4), Color(0xFF00431F), Color(0xFF0F4A2C), Color(0xFFC4F0D4)),
    Teal("teal", "Turquesa", Color(0xFFBEF0EC), Color(0xFF00403C), Color(0xFF0B4A46), Color(0xFFBEF0EC)),
    Sky("sky", "Cielo", Color(0xFFCDE6FF), Color(0xFF003255), Color(0xFF0E4570), Color(0xFFCDE6FF)),
    Indigo("indigo", "Índigo", Color(0xFFDDE0FF), Color(0xFF1A2480), Color(0xFF2B3590), Color(0xFFDDE0FF)),
    Violet("violet", "Violeta", Color(0xFFEBDCFF), Color(0xFF40207A), Color(0xFF4C2D86), Color(0xFFEBDCFF)),
    Pink("pink", "Rosa", Color(0xFFFFD9E6), Color(0xFF7A1745), Color(0xFF6A1B45), Color(0xFFFFD9E6)),
    Slate("slate", "Pizarra", Color(0xFFDDE2E8), Color(0xFF2B3440), Color(0xFF3A424D), Color(0xFFDDE2E8));

    fun container(dark: Boolean): Color = if (dark) darkContainer else lightContainer
    fun onContainer(dark: Boolean): Color = if (dark) darkOn else lightOn

    companion object {
        /** Unknown keys (e.g. written by a newer version) fall back to a neutral accent instead of failing. */
        fun fromKey(key: String): CategoryAccent = entries.firstOrNull { it.key == key } ?: Slate

        /** Next accent for a new category, cycling so consecutive ones look different. */
        fun forIndex(index: Int): CategoryAccent = entries[index.mod(entries.size)]
    }
}
