package eu.kanade.tachiyomi.extension.zh.mycomic

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

class MyComic : HttpSource() {
    override val name = "MyComic"
    override val baseUrl = "https://mycomic.com"
    override val lang = "zh"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .set("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/comics?page=$page", headers)
    override fun popularMangaParse(response: Response) = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/comics?sort=updated&page=$page", headers)
    override fun latestUpdatesParse(response: Response) = parseMangaList(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/comics?q=$query&page=$page", headers)
    }

    override fun searchMangaParse(response: Response) = parseMangaList(response)

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.doc()
        val mangas = document.select("div.grid > div.group").map { el ->
            SManga.create().apply {
                setUrlWithoutDomain(el.selectFirst("a")!!.attr("abs:href"))
                el.selectFirst("img")!!.let {
                    title = it.attr("alt")
                    thumbnail_url = it.imgUrl()
                }
            }
        }
        val hasNext = document.selectFirst("nav[role=navigation] a[rel=next]") != null
        return MangasPage(mangas, hasNext)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.doc()
        val detail = document.selectFirst("div[data-flux-card]") ?: document
        return SManga.create().apply {
            title = detail.selectFirst("div[data-flux-heading], h1")?.text().orEmpty()
            thumbnail_url = detail.selectFirst("img")?.imgUrl()
            description = detail.selectFirst("div[x-show=show], meta[name=description]")?.let {
                if (it.tagName() == "meta") it.attr("content") else it.text()
            }
            author = detail.selectFirst("a[href*=/authors/]")?.text()
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.doc().select("a[href*=/chapters/]").map { el ->
            SChapter.create().apply {
                name = el.text().trim()
                setUrlWithoutDomain(el.attr("abs:href"))
            }
        }.distinctBy { it.url }
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.doc().select("img[x-ref], img").map { it.imgUrl() }
            .filter { it.startsWith("http") && !it.contains("logo") }
            .distinct()
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun Response.doc(): Document = Jsoup.parse(body!!.string(), request.url.toString())

    private fun Element.imgUrl(): String = when {
        hasAttr("data-src") && attr("data-src").isNotBlank() -> absUrl("data-src")
        else -> absUrl("src")
    }
}
