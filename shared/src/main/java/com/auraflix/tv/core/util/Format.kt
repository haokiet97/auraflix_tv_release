package com.auraflix.tv.core.util

import java.util.Locale

/** 00:00 hoặc 1:02:03 */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/** "2 giờ 15 phút" từ chuỗi thời lượng của API ("45 phút/tập"). */
fun prettyTime(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

fun prettyViews(views: Long?): String? {
    if (views == null || views <= 0) return null
    return when {
        views >= 1_000_000 -> String.format(Locale.US, "%.1fM lượt xem", views / 1_000_000f)
        views >= 1_000 -> String.format(Locale.US, "%.0fK lượt xem", views / 1_000f)
        else -> "$views lượt xem"
    }
}

/** Rút gọn mô tả cho card. */
fun String.shorten(maxChars: Int = 520): String {
    val clean = trim()
    return if (clean.length <= maxChars) clean else clean.take(maxChars).trimEnd() + "…"
}
