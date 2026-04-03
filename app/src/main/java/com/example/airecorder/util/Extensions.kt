package com.example.airecorder.util

import android.content.Context
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun Long.formatDuration(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

fun Long.formatDateTime(): String = DateFormat.getDateTimeInstance().format(Date(this))

fun Long.formatBytes(): String {
    if (this <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = this.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[index])
}

fun Context.meetingsDirectory(): File = File(filesDir, "meetings").apply { mkdirs() }
