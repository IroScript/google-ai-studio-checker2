package com.example.util

import java.io.File
import java.util.regex.Pattern

object YoutubeIdHelper {
    private val bracketRegex = Pattern.compile("\\[([a-zA-Z0-9_-]{11})\\]")
    private val parenRegex = Pattern.compile("\\(([a-zA-Z0-9_-]{11})\\)")
    private val hyphenRegex = Pattern.compile("-([a-zA-Z0-9_-]{11})$")
    private val exact11Regex = Pattern.compile("^[a-zA-Z0-9_-]{11}$")

    /**
     * Extracts YouTube ID if present in the filename/path/title, matching original logic.
     */
    fun extractYoutubeId(filePathOrName: String): String? {
        val fileName = File(filePathOrName).nameWithoutExtension

        val bracketMatch = bracketRegex.matcher(fileName)
        if (bracketMatch.find()) {
            return bracketMatch.group(1)
        }

        val parenMatch = parenRegex.matcher(fileName)
        if (parenMatch.find()) {
            return parenMatch.group(1)
        }

        val hyphenMatch = hyphenRegex.matcher(fileName)
        if (hyphenMatch.find()) {
            return hyphenMatch.group(1)
        }

        if (fileName.length == 11 && exact11Regex.matcher(fileName).matches()) {
            return fileName
        }

        return null
    }

    fun getThumbnailUrl(youtubeId: String): String {
        return "https://img.youtube.com/vi/$youtubeId/mqdefault.jpg"
    }
}
