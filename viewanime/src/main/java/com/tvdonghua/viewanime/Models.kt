package com.tvdonghua.viewanime

data class ShowCard(
    val title: String,
    val badge: String,
    val href: String,
    val image: String?
)

data class CatalogPage(
    val episodes: List<ShowCard>,
    val nextUrl: String?,
    val prevUrl: String?,
    val tabs: List<Pair<String, String>>,
    val activeTab: String?,
    val label: String
)

data class ServerSlot(
    val name: String,
    val embed: String?,
    val key: Int
)

data class EpisodePage(
    val title: String,
    val pageUrl: String,
    val servers: List<ServerSlot>,
    val cookies: String,
    val csrf: String?,
    val videoId: Int?,
    val focus: Int
)

interface CatalogSource {
    val id: String
    val title: String
    fun load(pageUrl: String?, tab: String?): CatalogPage
    fun episode(pageUrl: String): EpisodePage
    fun embed(page: EpisodePage, server: ServerSlot): String
}

data class SavedSource(
    val id: String,
    val name: String,
    val listUrl: String,
    val sampleUrl: String
)
