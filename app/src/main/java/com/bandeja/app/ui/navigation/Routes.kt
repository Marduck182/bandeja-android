package com.bandeja.app.ui.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"

    const val ARG_ID = "id"
    const val CATEGORY = "category/{$ARG_ID}"
    const val CONTENT = "content/{$ARG_ID}"
    const val CATEGORY_EDITOR = "category-editor?$ARG_ID={$ARG_ID}"

    fun category(id: Long) = "category/$id"
    fun content(id: Long) = "content/$id"

    /** `null` opens the editor to create a category. */
    fun categoryEditor(id: Long? = null) = if (id == null) "category-editor" else "category-editor?$ARG_ID=$id"

    /** Destinations of the bottom bar: the only ones that show it. */
    val tabs = listOf(HOME, SEARCH, FAVORITES, SETTINGS)
}
