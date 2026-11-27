package com.auraflix.tv.core.update

/**
 * Rút gọn nội dung release GitHub thành vài dòng dễ đọc trên TV.
 * Chỉ lấy phần "Có gì mới", bỏ bảng, lệnh cài đặt và ký hiệu Markdown.
 */
object ReleaseNotes {

    fun clean(body: String?): String {
        if (body.isNullOrBlank()) return ""
        val lines = body.lines().map { it.trim() }
        val start = lines.indexOfFirst {
            it.startsWith("##") && it.contains("Có gì mới", ignoreCase = true)
        }
        val scoped = if (start >= 0) {
            lines.drop(start + 1).takeWhile { !it.startsWith("## ") }
        } else {
            lines
        }
        return scoped
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("|") || it.startsWith("---") || it.startsWith("```") }
            .filterNot { it.contains("adb install", ignoreCase = true) }
            .map {
                it.removePrefix("- ").removePrefix("* ").trim()
                    .replace(Regex("^#+\\s*"), "")
                    .replace("**", "")
                    .replace("`", "")
            }
            .filter { it.isNotEmpty() }
            .take(10)
            .joinToString("\n")
            .take(700)
    }
}
