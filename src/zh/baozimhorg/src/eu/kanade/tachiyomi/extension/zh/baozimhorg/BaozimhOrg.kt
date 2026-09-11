package eu.kanade.tachiyomi.extension.zh.baozimhorg

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class BaozimhOrg : HttpSource() {
    override val name = "包子漫画"
    override val baseUrl = "https://baozimh.org"
    override val lang = "zh"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/hots", headers)
    override fun popularMangaParse(response: Response) = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/dayup", headers)
    override fun latestUpdatesParse(response: Response) = parseMangaList(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isBlank()) "$baseUrl/manga" else "$baseUrl/s/$query"
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = parseMangaList(response)

    private fun parseMangaList(response: Response): MangasPage {
        val mangas = response.doc().select("a[href]").mapNotNull { el ->
            val href = el.attr("abs:href")
            if (!href.contains("/manga/") || href.contains("/manga-genre/")) return@mapNotNull null
            val title = el.selectFirst("img")?.attr("alt")?.ifBlank { null }
                ?: el.text().ifBlank { null }
                ?: return@mapNotNull null
            SManga.create().apply {
                this.title = title.trim()
                setUrlWithoutDomain(href)
                thumbnail_url = el.selectFirst("img")?.imgUrl()
            }
        }.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.doc()
        return SManga.create().apply {
            title = document.selectFirst("h1, h2")?.text().orEmpty()
            description = document.selectFirst("meta[name=description]")?.attr("content")
            thumbnail_url = document.selectFirst("img[src]")?.imgUrl()
            author = document.select("a[href*=/author/], a[href*=/artist/]").eachText().joinToString()
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.doc().select("a[href]").mapNotNull { el ->
            val href = el.attr("abs:href")
            if (!href.contains("/chapter") && !href.contains("/ch/")) return@mapNotNull null
            val title = el.text().ifBlank { return@mapNotNull null }
            SChapter.create().apply {
                name = title.trim()
                setUrlWithoutDomain(href)
            }
        }.distinctBy { it.url }.reversed()
    }

    override fun pageListParse(response: Response): List<Page> {
        val images = response.doc().select("img[src], img[data-src]").map { it.imgUrl() }
            .filter { it.startsWith("http") && !it.contains("logo") && !it.endsWith(".svg") }
        return images.mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun Response.doc(): Document = Jsoup.parse(body!!.string(), request.url.toString())

    private fun Element.imgUrl(): String = when {
        hasAttr("data-src") && attr("data-src").isNotBlank() -> absUrl("data-src")
        else -> absUrl("src")
    }
}
