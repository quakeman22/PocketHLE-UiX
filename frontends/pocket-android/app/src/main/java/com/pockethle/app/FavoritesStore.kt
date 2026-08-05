package com.pockethle.app

import android.content.Context

/**
 * Stores which games are pinned to the Today screen as favorites.
 * Purely client-side (SharedPreferences), like [CoverStore] — doesn't
 * touch the native library schema.
 */
object FavoritesStore {

    private const val PREFS = "favorites"
    private const val KEY_IDS = "ordered_ids"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Ordered list of favorited game IDs (insertion order preserved). */
    fun ids(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_IDS, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(",")
    }

    fun isFavorite(context: Context, gameId: String): Boolean = gameId in ids(context)

    /** Replaces the full favorites set, keeping the given order. */
    fun setAll(context: Context, gameIds: List<String>) {
        prefs(context).edit().putString(KEY_IDS, gameIds.joinToString(",")).apply()
    }

    /** Drops any favorite IDs that no longer exist in the library (e.g. game was removed). */
    fun pruneMissing(context: Context, existingIds: Set<String>) {
        val kept = ids(context).filter { it in existingIds }
        setAll(context, kept)
    }
}
