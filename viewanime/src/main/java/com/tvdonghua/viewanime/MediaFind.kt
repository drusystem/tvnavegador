package com.tvdonghua.viewanime

data class Stream(val url: String, val referer: String, val cookies: String = "")

object MediaFind {
    private val address = Regex(
        """https?:\\?/\\?/[^"'\\\s<>]{12,500}?\.(?:m3u8|mp4)(?:\?[^"'\\\s<>]*)?""",
        RegexOption.IGNORE_CASE
    )

    fun locate(embedUrl: String, referer: String): Stream? {
        if (embedUrl.contains("dailymotion.com", true)) return null
        val direct = plain(embedUrl)
        if (direct != null) return Stream(direct, referer)
        val page = try {
            Net.get(embedUrl, referer)
        } catch (e: Exception) {
            return null
        }
        val type = page.type.lowercase()
        if (type.contains("mpegurl") || type.startsWith("video/") || type.contains("mp4")) {
            return Stream(page.url, referer)
        }
        val found = scan(page.body)
        if (found != null) return Stream(found, embedUrl, page.cookies)
        return null
    }

    private fun plain(url: String): String? {
        val path = url.substringBefore('?').lowercase()
        if (path.endsWith(".m3u8") || path.endsWith(".mp4")) return url
        return null
    }

    private fun scan(html: String): String? {
        val hits = address.findAll(html).map { normalize(it.value) }.filter { usable(it) }.toList()
        return hits.firstOrNull { it.contains(".m3u8", true) } ?: hits.firstOrNull()
    }

    private fun normalize(raw: String): String {
        return raw.replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&")
    }

    private fun usable(url: String): Boolean {
        return !Regex("doubleclick|googlesyndication|facebook|analytics|pixel|preview|thumb", RegexOption.IGNORE_CASE)
            .containsMatchIn(url)
    }
}
