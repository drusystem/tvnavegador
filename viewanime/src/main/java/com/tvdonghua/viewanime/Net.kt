package com.tvdonghua.viewanime

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class PageBody(val url: String, val code: Int, val type: String, val body: String, val cookies: String)

object Net {
    const val AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    fun get(url: String, referer: String? = null, cookies: String? = null): PageBody {
        return open("GET", url, null, referer, cookies, null, 900_000)
    }

    fun postJson(url: String, json: String, referer: String, cookies: String, csrf: String): String {
        return open("POST", url, json.toByteArray(Charsets.UTF_8), referer, cookies, csrf, 200_000, null).body
    }

    fun postForm(url: String, form: String, referer: String, cookies: String, csrf: String): String {
        return open(
            "POST",
            url,
            form.toByteArray(Charsets.UTF_8),
            referer,
            cookies,
            csrf,
            400_000,
            "application/x-www-form-urlencoded"
        ).body
    }

    fun bytes(url: String): ByteArray {
        val conn = connect("GET", url, null, null, null, null, null)
        try {
            val stream = if (conn.responseCode < 400) conn.inputStream else conn.errorStream
            val out = ByteArrayOutputStream()
            stream.use { input ->
                val buf = ByteArray(8192)
                var n = input.read(buf)
                while (n >= 0) {
                    out.write(buf, 0, n)
                    if (out.size() > 2_000_000) break
                    n = input.read(buf)
                }
            }
            return out.toByteArray()
        } finally {
            conn.disconnect()
        }
    }

    private fun open(
        method: String,
        url: String,
        body: ByteArray?,
        referer: String?,
        cookies: String?,
        csrf: String?,
        maxChars: Int,
        contentType: String? = null
    ): PageBody {
        var current = url
        var jar = cookies.orEmpty()
        var hops = 0
        while (hops < 5) {
            val conn = connect(method, current, body, referer, jar, csrf, contentType)
            try {
                val code = conn.responseCode
                jar = merge(jar, conn.headerFields["Set-Cookie"])
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: break
                    current = URL(URL(current), loc).toString()
                    hops++
                    continue
                }
                val stream = if (code < 400) conn.inputStream else conn.errorStream
                val text = stream?.use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var n = input.read(buf)
                    while (n >= 0 && out.size() < maxChars) {
                        out.write(buf, 0, n)
                        n = input.read(buf)
                    }
                    out.toString(Charsets.UTF_8.name())
                }.orEmpty()
                val type = conn.contentType.orEmpty()
                return PageBody(current, code, type, text, jar)
            } finally {
                conn.disconnect()
            }
        }
        throw IllegalStateException("No hubo respuesta")
    }

    private fun connect(
        method: String,
        url: String,
        body: ByteArray?,
        referer: String?,
        cookies: String?,
        csrf: String?,
        contentType: String?
    ): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 15000
        conn.readTimeout = 25000
        conn.requestMethod = method
        conn.setRequestProperty("User-Agent", AGENT)
        conn.setRequestProperty("Accept", "text/html,application/json,*/*")
        conn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9")
        if (!referer.isNullOrBlank()) conn.setRequestProperty("Referer", referer)
        if (!cookies.isNullOrBlank()) conn.setRequestProperty("Cookie", cookies)
        if (!csrf.isNullOrBlank()) {
            conn.setRequestProperty("X-CSRF-TOKEN", csrf)
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            conn.setRequestProperty("Origin", origin(url))
        }
        if (body != null) conn.setRequestProperty("Content-Type", contentType ?: "application/json")
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body) }
        }
        return conn
    }

    private fun merge(jar: String, setCookies: List<String>?): String {
        val map = linkedMapOf<String, String>()
        jar.split(';').map { it.trim() }.filter { it.contains('=') }.forEach {
            val name = it.substringBefore('=').trim()
            map[name] = it
        }
        setCookies.orEmpty().forEach { raw ->
            val pair = raw.substringBefore(';').trim()
            if (pair.contains('=')) map[pair.substringBefore('=')] = pair
        }
        return map.values.joinToString("; ")
    }

    fun origin(url: String): String {
        val u = URL(url)
        val port = if (u.port > 0 && u.port != u.defaultPort) ":${u.port}" else ""
        return "${u.protocol}://${u.host}$port"
    }

    fun absolute(base: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        return URL(URL(base), href).toString()
    }

    fun clean(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
