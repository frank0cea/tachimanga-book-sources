package eu.kanade.tachiyomi.extension.zh.mangabz

internal object Unpacker {
    fun unpack(script: String): String {
        val packed = substringBetween(script, "}('", ".split('|'),0,{}))").replace("\\'", "\"")
        if (packed.isEmpty()) return ""
        val data = packed.substringBefore("',")
        val dictionary = substringBetween(packed.substringAfter("',"), "'", "'").split("|")
        val size = dictionary.size
        return Regex("""\w+""").replace(data) { match ->
            val key = match.value
            val index = parseRadix62(key)
            if (index >= size) key else dictionary[index].ifEmpty { key }
        }
    }

    private fun substringBetween(text: String, left: String, right: String): String {
        val start = text.indexOf(left)
        if (start == -1) return ""
        val from = start + left.length
        val end = text.indexOf(right, from)
        if (end == -1) return ""
        return text.substring(from, end)
    }

    private fun parseRadix62(str: String): Int {
        var result = 0
        for (ch in str) {
            result = result * 62 + when {
                ch.code <= '9'.code -> ch.code - '0'.code
                ch.code >= 'a'.code -> ch.code - ('a'.code - 10)
                else -> ch.code - ('A'.code - 36)
            }
        }
        return result
    }
}
