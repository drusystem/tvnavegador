package com.tvdonghua.viewanime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object SeriesDonghua : CatalogSource {
    override val id = "sd"
    override val title = "SeriesDonghua"
    private const val HOME = "https://seriesdonghua.com/episodios"

    override fun load(pageUrl: String?, tab: String?): CatalogPage {
        val url = pageUrl ?: HOME
        val page = Net.get(url)
        val cards = ArrayList<ShowCard>()
        page.body.split("class=\"donghua-card\"").drop(1).forEach { chunk ->
            val href = attr(chunk, "href") ?: return@forEach
            if (!href.contains("episodio")) return@forEach
            cards.add(
                ShowCard(
                    title = Net.clean(text(chunk, "card-title\">") ?: "Episodio"),
                    badge = Net.clean(text(chunk, "badge-ep\">") ?: ""),
                    href = Net.absolute(page.url, href),
                    image = attr(chunk, "src")?.let { Net.absolute(page.url, it) }
                )
            )
        }
        val pageNo = Regex("[?&]page=(\\d+)").find(url)?.groupValues?.get(1) ?: "1"
        val heading = Regex("<h1[^>]*>\\s*([^<]+)").find(page.body)?.groupValues?.get(1)
        val label = if (heading != null && !url.contains("/episodios") && !url.contains("buscar")) {
            Net.clean(heading)
        } else {
            "Página $pageNo"
        }
        return CatalogPage(cards, rel(page.body, "next"), rel(page.body, "prev"), emptyList(), null, label)
    }

    fun directory(pageUrl: String?): CatalogPage {
        val url = pageUrl ?: "https://seriesdonghua.com/todos-los-donghuas"
        val page = Net.get(url)
        val cards = ArrayList<ShowCard>()
        page.body.split("class=\"donghua-card\"").drop(1).forEach { chunk ->
            val href = attr(chunk, "href") ?: return@forEach
            if (href.contains("episodio")) return@forEach
            val badge = Regex("badge-status[^\"]*\">\\s*([^<]+)").find(chunk)?.groupValues?.get(1)
            val named = attr(chunk, "title")?.removePrefix("Ver ")
                ?: text(chunk, "card-title\">")
                ?: href.trim('/').substringAfterLast('/').replace('-', ' ')
            cards.add(
                ShowCard(
                    Net.clean(named).removeSuffix(" Sub Español").ifBlank { "Serie" },
                    Net.clean(badge ?: ""),
                    Net.absolute(page.url, href),
                    attr(chunk, "src")?.let { Net.absolute(page.url, it) }
                )
            )
        }
        val pageNo = Regex("[?&]page=(\\d+)").find(url)?.groupValues?.get(1) ?: "1"
        return CatalogPage(cards, rel(page.body, "next"), rel(page.body, "prev"), emptyList(), null, "Directorio · página $pageNo")
    }

    override fun episode(pageUrl: String): EpisodePage {
        val page = Net.get(pageUrl)
        val csrf = Regex("name=\"csrf-token\" content=\"([^\"]+)\"").find(page.body)?.groupValues?.get(1)
        val servers = Regex(
            "data-video-id=\"(\\d+)\"\\s+data-server-index=\"(\\d+)\"[\\s\\S]{0,320}?<span>([^<]+)</span>"
        ).findAll(page.body).map {
            ServerSlot(Net.clean(it.groupValues[3]), null, it.groupValues[2].toInt())
        }.toList()
        val videoId = Regex("data-video-id=\"(\\d+)\"").find(page.body)?.groupValues?.get(1)?.toIntOrNull()
        val title = Net.clean(text(page.body, "player-title\">") ?: "Capítulo")
        val focus = servers.indexOfFirst { it.name.contains("ok.ru", true) }.coerceAtLeast(0)
        return EpisodePage(title, page.url, servers, page.cookies, csrf, videoId, focus)
    }

    override fun embed(page: EpisodePage, server: ServerSlot): String {
        server.embed?.let { return it }
        val id = page.videoId ?: throw IllegalStateException("Sin identificador de video")
        val token = page.csrf ?: throw IllegalStateException("Sin token")
        val json = Net.postJson(
            "https://seriesdonghua.com/api/player/get-server",
            "{\"video_id\":$id,\"server_index\":${server.key}}",
            page.pageUrl,
            page.cookies,
            token
        )
        val url = Regex("\"embed_url\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
            ?: throw IllegalStateException("El servidor no entregó reproductor")
        return url.replace("\\/", "/")
    }
}

object JkAnime : CatalogSource {
    override val id = "jk"
    override val title = "JKAnime"
    private const val HOME = "https://jkanime.net/"
    private var cached: String? = null

    private val tabs = listOf("animes" to "Animes", "donghuas" to "Donghuas", "ovas" to "Ovas")

    override fun load(pageUrl: String?, tab: String?): CatalogPage {
        val html = cached ?: Net.get(HOME).body.also { cached = it }
        val which = tab ?: "animes"
        val block = pane(html, which)
        val cards = ArrayList<ShowCard>()
        Regex("href=\"(https://jkanime.net/[^\"]+/\\d+/)\"").findAll(block).forEach { match ->
            val href = match.groupValues[1]
            val window = block.substring(match.range.first, minOf(match.range.first + 900, block.length))
            if (cards.any { it.href == href }) return@forEach
            cards.add(
                ShowCard(
                    title = Net.clean(text(window, "card-title\">") ?: href.trim('/').substringAfterLast('/')),
                    badge = Net.clean(text(window, "badge-primary\">") ?: ""),
                    href = href,
                    image = Regex("<img[^>]+src=\"([^\"]+)\"").find(window)?.groupValues?.get(1)
                )
            )
        }
        return CatalogPage(cards, null, null, tabs, which, "Programación")
    }

    fun directory(pageUrl: String?): CatalogPage {
        val url = pageUrl ?: "https://jkanime.net/directorio"
        val page = Net.get(url)
        if (page.body.contains("var animes =")) return directoryJson(page.body)
        return directorySearch(page.body)
    }

    fun episodes(pageUrl: String): CatalogPage {
        val caps = Regex("[?&]caps=(\\d+)").find(pageUrl)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val seriesUrl = pageUrl.substringBefore('?')
        val page = Net.get(seriesUrl)
        val csrf = Regex("name=\"csrf-token\" content=\"([^\"]+)\"").find(page.body)?.groupValues?.get(1)
            ?: throw IllegalStateException("Sin token")
        val animeId = Regex("ajax/episodes/(\\d+)/").find(page.body)?.groupValues?.get(1)
            ?: throw IllegalStateException("Sin lista de capítulos")
        val slug = seriesUrl.trimEnd('/').substringAfterLast('/')
        val poster = Regex("https://cdn\\.jkdesa\\.com/assets/images/animes/image/[^\"'\\s)]+")
            .find(page.body)?.value
        val thumb = poster?.replace("/animes/image/", "/animes/video/image_thumb/")?.substringBeforeLast('/')?.plus("/")
        val json = Net.postForm(
            "https://jkanime.net/ajax/episodes/$animeId/$caps",
            "_token=$csrf",
            seriesUrl,
            page.cookies,
            csrf
        )
        val obj = JSONObject(json)
        val data = obj.getJSONArray("data")
        val cards = ArrayList<ShowCard>()
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val number = item.optInt("number")
            val file = item.optString("image")
            val image = if (file.length > 10 && thumb != null) thumb + file else poster
            cards.add(
                ShowCard(
                    Net.clean(item.optString("title").ifBlank { "Capítulo $number" }),
                    "Ep $number",
                    "https://jkanime.net/$slug/$number/",
                    image
                )
            )
        }
        val current = obj.optInt("current_page", caps)
        val last = obj.optInt("last_page", current)
        val next = if (current < last) "$seriesUrl?caps=${current + 1}" else null
        val prev = if (current > 1) "$seriesUrl?caps=${current - 1}" else null
        val title = Net.clean(
            Regex("<h1[^>]*>\\s*([^<]+)").find(page.body)?.groupValues?.get(1) ?: slug.replace('-', ' ')
        )
        return CatalogPage(cards, next, prev, emptyList(), null, title)
    }

    private fun directoryJson(html: String): CatalogPage {
        val start = html.indexOf("var animes = ")
        val end = listOf(";\nvar mode", ";\r\nvar mode")
            .map { html.indexOf(it, start + 13) }
            .filter { it > start }
            .minOrNull() ?: html.indexOf(';', start + 13)
        val obj = JSONObject(html.substring(start + 13, end))
        val data = obj.getJSONArray("data")
        val cards = ArrayList<ShowCard>()
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val kind = item.optString("tipo") + " " + item.optString("type")
            if (kind.contains("hentai", true)) continue
            cards.add(
                ShowCard(
                    Net.clean(item.optString("title")),
                    Net.clean(item.optString("estado")),
                    item.optString("url"),
                    item.optString("image")
                )
            )
        }
        val pageNo = obj.optInt("current_page", 1)
        return CatalogPage(cards, jsonUrl(obj, "next_page_url"), jsonUrl(obj, "prev_page_url"), emptyList(), null, "Directorio · página $pageNo")
    }

    private fun directorySearch(html: String): CatalogPage {
        val cards = ArrayList<ShowCard>()
        val pattern = Regex(
            "href=\"(https://jkanime.net/[a-z0-9-]+/)\"[\\s\\S]{0,500}?data-setbg=\"([^\"]+)\"[\\s\\S]{0,700}?<h5>\\s*<a[^>]*>([^<]+)</a>",
            RegexOption.IGNORE_CASE
        )
        pattern.findAll(html).forEach { match ->
            val href = match.groupValues[1]
            val slug = href.trimEnd('/').substringAfterLast('/')
            if (slug in setOf("directorio", "buscar", "horario", "login", "registro", "pedidos", "aleatorio")) return@forEach
            if (cards.any { it.href == href }) return@forEach
            cards.add(ShowCard(Net.clean(match.groupValues[3]), "", href, match.groupValues[2].replace("&quot;", "")))
        }
        return CatalogPage(cards, null, null, emptyList(), null, "Búsqueda")
    }

    private fun jsonUrl(obj: JSONObject, key: String): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.optString(key)
        if (value.isBlank() || value == "null") return null
        return value
    }

    override fun episode(pageUrl: String): EpisodePage {
        val page = Net.get(pageUrl)
        val names = Regex("data-id=\"(\\d+)\"[^>]*>([^<]+)</a>").findAll(page.body)
            .associate { it.groupValues[1].toInt() to Net.clean(it.groupValues[2]) }
        val embeds = Regex("video\\[(\\d+)]\\s*=\\s*'<iframe[^>]*src=\"([^\"]+)\"").findAll(page.body)
            .associate { it.groupValues[1].toInt() to it.groupValues[2].replace("&amp;", "&") }
        val servers = embeds.keys.sorted().map { index ->
            ServerSlot(names[index] ?: "Reproductor ${index + 1}", embeds[index], index)
        }
        val title = Net.clean(
            Regex("<h1[^>]*>([^<]+)</h1>").find(page.body)?.groupValues?.get(1)
                ?: pageUrl.trim('/').substringAfterLast('/').substringBeforeLast('/')
        )
        val focus = if (embeds.containsKey(1)) servers.indexOfFirst { it.embed == embeds[1] }.coerceAtLeast(0) else 0
        return EpisodePage(title, page.url, servers, page.cookies, null, null, focus)
    }

    override fun embed(page: EpisodePage, server: ServerSlot): String {
        return server.embed ?: page.pageUrl
    }

    private fun pane(html: String, id: String): String {
        val key = "id=\"$id\""
        val start = html.indexOf(key).takeIf { it >= 0 } ?: return ""
        val bounds = listOf("animes", "donghuas", "ovas", "hentai")
            .map { html.indexOf("id=\"$it\"", start + key.length) }
            .filter { it > start }
        val end = bounds.minOrNull() ?: html.length
        return html.substring(start, end)
    }
}

class CustomSource(private val saved: SavedSource) : CatalogSource {
    override val id = saved.id
    override val title = saved.name

    override fun load(pageUrl: String?, tab: String?): CatalogPage {
        val sampleHost = host(saved.sampleUrl)
        val list = pageUrl ?: saved.listUrl
        if (sampleHost.endsWith("seriesdonghua.com")) return SeriesDonghua.load(list, null)
        if (sampleHost.endsWith("jkanime.net") && (pageUrl == null || list.trimEnd('/') == "https://jkanime.net")) {
            return JkAnime.load(null, tab)
        }
        val page = Net.get(list)
        val cards = ArrayList<ShowCard>()
        Regex("href=\"([^\"]+)\"").findAll(page.body).forEach { match ->
            val href = Net.absolute(page.url, match.groupValues[1])
            if (!sameShape(saved.sampleUrl, href)) return@forEach
            if (cards.any { it.href == href }) return@forEach
            val window = page.body.substring(match.range.first, minOf(match.range.first + 700, page.body.length))
            val image = Regex("<img[^>]+src=\"([^\"]+)\"").find(window)?.groupValues?.get(1)
                ?.let { Net.absolute(page.url, it) }
            val badge = text(window, "badge-ep\">") ?: text(window, "badge-primary\">") ?: ""
            cards.add(ShowCard(titleFrom(href), Net.clean(badge), href, image))
        }
        return CatalogPage(cards, null, null, emptyList(), null, saved.name)
    }

    override fun episode(pageUrl: String): EpisodePage {
        val site = host(pageUrl)
        if (site.endsWith("seriesdonghua.com")) return SeriesDonghua.episode(pageUrl)
        if (site.endsWith("jkanime.net")) return JkAnime.episode(pageUrl)
        val page = Net.get(pageUrl)
        val servers = Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .findAll(page.body)
            .map { it.groupValues[1].replace("&amp;", "&") }
            .filter { it.startsWith("http") && !ad(it) }
            .distinct()
            .mapIndexed { index, src -> ServerSlot("Reproductor ${index + 1}", src, index) }
            .toList()
        val ready = if (servers.isEmpty()) listOf(ServerSlot("Página", pageUrl, 0)) else servers
        val title = Net.clean(Regex("<h1[^>]*>([^<]+)</h1>").find(page.body)?.groupValues?.get(1) ?: titleFrom(pageUrl))
        return EpisodePage(title, page.url, ready, page.cookies, null, null, 0)
    }

    override fun embed(page: EpisodePage, server: ServerSlot): String {
        val site = host(page.pageUrl)
        if (site.endsWith("seriesdonghua.com")) return SeriesDonghua.embed(page, server)
        if (site.endsWith("jkanime.net")) return JkAnime.embed(page, server)
        return server.embed ?: page.pageUrl
    }

    private fun sameShape(sample: String, candidate: String): Boolean {
        if (host(sample) != host(candidate)) return false
        val samplePath = path(sample)
        val candidatePath = path(candidate)
        if (samplePath.contains("-episodio-") && candidatePath.contains("-episodio-")) return true
        val a = samplePath.trim('/').split('/')
        val b = candidatePath.trim('/').split('/')
        if (a.size < 2 || a.size != b.size) return false
        return a.last().all { it.isDigit() } && b.last().all { it.isDigit() }
    }

    private fun titleFrom(url: String): String {
        return path(url).trim('/').substringAfterLast('/').replace('-', ' ').replaceFirstChar { it.uppercase() }
    }
}

object Library {
    fun all(context: Context): List<CatalogSource> {
        return listOf(SeriesDonghua, JkAnime) + Shelf.load(context).map { CustomSource(it) }
    }

    fun open(context: Context, id: String): CatalogSource {
        return all(context).firstOrNull { it.id == id } ?: SeriesDonghua
    }
}

object Shelf {
    private const val PREFS = "viewanime_sources"
    private const val KEY = "items"

    fun load(context: Context): List<SavedSource> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        val list = ArrayList<SavedSource>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(SavedSource(o.getString("id"), o.getString("name"), o.getString("list"), o.getString("sample")))
        }
        return list
    }

    fun add(context: Context, listUrl: String, sampleUrl: String) {
        val items = load(context).toMutableList()
        val name = host(sampleUrl).removePrefix("www.")
        items.add(SavedSource("c:" + UUID.randomUUID().toString().take(8), name, listUrl, sampleUrl))
        save(context, items)
    }

    fun remove(context: Context, id: String) {
        save(context, load(context).filter { it.id != id })
    }

    private fun save(context: Context, items: List<SavedSource>) {
        val array = JSONArray()
        items.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("list", it.listUrl).put("sample", it.sampleUrl))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}

private fun host(url: String): String {
    return Regex("https?://([^/]+)").find(url)?.groupValues?.get(1)?.lowercase().orEmpty()
}

private fun path(url: String): String {
    return url.substringAfter("://").substringAfter('/')
}

private fun ad(url: String): Boolean {
    return Regex("agl003|acscdn|doubleclick|googlesyndication|google-analytics|facebook|adangle|aclib|imasdk", RegexOption.IGNORE_CASE)
        .containsMatchIn(url)
}

private fun attr(chunk: String, name: String): String? {
    return Regex("$name=\"([^\"]+)\"").find(chunk)?.groupValues?.get(1)
}

private fun text(chunk: String, marker: String): String? {
    val start = chunk.indexOf(marker)
    if (start < 0) return null
    val from = start + marker.length
    val end = chunk.indexOf('<', from)
    if (end < 0) return null
    return chunk.substring(from, end)
}

private fun rel(html: String, relation: String): String? {
    return Regex("<a[^>]*href=\"([^\"]+)\"[^>]*rel=\"$relation\"").find(html)?.groupValues?.get(1)
}
