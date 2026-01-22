import android.content.Context

// [!code ++] ADD AT BOTTOM OF FILE
object SearchHistoryManager {
    private const val PREF_NAME = "search_prefs"
    private const val KEY_HISTORY = "history_json"

    fun getHistory(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val stringSet = prefs.getStringSet(KEY_HISTORY, emptySet()) ?: emptySet()
        // Sort by length or however you prefer, but Set loses order.
        // For simple "recent" order without complex JSON, we assume the set is small.
        return stringSet.toList().reversed()
    }

    fun addSearch(context: Context, query: String) {
        if (query.isBlank()) return
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val history = prefs.getStringSet(KEY_HISTORY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        // Remove if exists to re-add at "top" (simulated)
        history.remove(query)
        history.add(query)

        // Limit to 5
        if (history.size > 5) {
            val iterator = history.iterator()
            iterator.next()
            iterator.remove()
        }

        prefs.edit().putStringSet(KEY_HISTORY, history).apply()
    }

    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}