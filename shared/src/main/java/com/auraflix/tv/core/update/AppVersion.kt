package com.auraflix.tv.core.update

/**
 * So sánh phiên bản dạng `1.2.3` / `v1.2.3` / `1.2.3-beta`.
 * Tách riêng khỏi Android để unit test được trên JVM.
 */
object AppVersion {

    fun parse(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .trim()
            .split('.')
            .map { part -> part.filter(Char::isDigit).toIntOrNull() ?: 0 }

    /** true nếu [latest] mới hơn [current]. */
    fun isNewer(latest: String, current: String): Boolean {
        val a = parse(latest)
        val b = parse(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
