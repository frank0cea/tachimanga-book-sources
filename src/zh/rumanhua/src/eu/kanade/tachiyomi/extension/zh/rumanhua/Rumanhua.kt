package eu.kanade.tachiyomi.extension.zh.rumanhua

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

class Rumanhua : HttpSource() {
    override val name = "如漫画"
    override val baseUrl = "https://www.rumanhua.org"
    override val lang = "zh"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/custom/top", headers)
    override fun popularMangaParse(response: Response) = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/custom/update", headers)
    override fun latestUpdatesParse(response: Response) = parseMangaList(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?key=$query", headers)
    }

    override fun searchMangaParse(response: Response) = parseMangaList(response)

    private fun parseMangaList(response: Response): MangasPage {
        val mangas = response.doc().select("a[href*=/news/]").mapNotNull { el ->
            val href = el.attr("abs:href")
            val title = el.attr("title").ifBlank { el.text() }.trim()
            if (title.isEmpty()) return@mapNotNull null
            SManga.create().apply {
                this.title = title
                setUrlWithoutDomain(href)
                thumbnail_url = el.selectFirst("img")?.imgUrl()
                    ?: el.parent()?.selectFirst("img")?.imgUrl()
            }
        }.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.doc()
        return SManga.create().apply {
            title = document.selectFirst("h1, .title, p.title")?.text().orEmpty()
            description = document.selectFirst(".intro, .desc, meta[name=description]")?.let {
                if (it.tagName() == "meta") it.attr("content") else it.text()
            }
            thumbnail_url = document.selectFirst("img")?.imgUrl()
            author = document.selectFirst(".author, p:contains(作者)")?.text()
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.doc().select("a[href]").mapNotNull { el ->
            val href = el.attr("abs:href")
            if (!href.contains("/chapter") && !href.contains("/read") && !href.contains("/view")) {
                return@mapNotNull null
            }
            val title = el.text().ifBlank { return@mapNotNull null }
            SChapter.create().apply {
                name = title.trim()
                setUrlWithoutDomain(href)
            }
        }.distinctBy { it.url }
    }

    override fun pageListParse(response: Response): List<Page> {
        val images = response.doc().select("#images img, .comic-page img, img[data-src], img.lazy").map { it.imgUrl() }
            .filter { it.startsWith("http") }
            .ifEmpty {
                response.doc().select("img").map { it.imgUrl() }.filter { it.startsWith("http") && !it.contains("logo") }
            }
        return images.mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun Response.doc(): Document = Jsoup.parse(body!!.string(), request.url.toString())

    private fun Element.imgUrl(): String = when {
        hasAttr("data-src") && attr("data-src").isNotBlank() -> absUrl("data-src")
        else -> absUrl("src")
    }
}
