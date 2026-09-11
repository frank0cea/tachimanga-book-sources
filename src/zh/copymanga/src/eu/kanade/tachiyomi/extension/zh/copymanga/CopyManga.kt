package eu.kanade.tachiyomi.extension.zh.copymanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class CopyManga : HttpSource() {
    override val name = "拷贝漫画"
    override val baseUrl = "https://www.copy3000.com"
    override val lang = "zh"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Accept-Language", "zh-CN,zh;q=0.9")

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/comics?ordering=-popular&offset=${(page - 1) * 21}&limit=21", headers)

    override fun popularMangaParse(response: Response) = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/comics?ordering=-datetime_updated&offset=${(page - 1) * 21}&limit=21", headers)

    override fun latestUpdatesParse(response: Response) = parseMangaList(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("q", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = parseMangaList(response)

    private fun parseMangaList(response: Response): MangasPage {
        val mangas = response.doc().select("a[href^=/comic/]").mapNotNull { el ->
            val href = el.attr("abs:href")
            if (href.contains("/chapter")) return@mapNotNull null
            val title = el.selectFirst("img")?.attr("alt")?.ifBlank { null }
                ?: el.selectFirst("p, h6, span")?.text()?.ifBlank { null }
                ?: el.text().ifBlank { null }
                ?: return@mapNotNull null
            SManga.create().apply {
                this.title = title.trim()
                setUrlWithoutDomain(href.substringBefore("?"))
                thumbnail_url = el.selectFirst("img")?.imgUrl()
            }
        }.distinctBy { it.url }
        val hasNext = response.doc().selectFirst("a:contains(下一页), a[rel=next]") != null
        return MangasPage(mangas, hasNext)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.doc()
        return SManga.create().apply {
            title = document.selectFirst("h6, h1, .comicParticulars-title")?.text()
                ?: document.title().substringBefore("-").trim()
            description = document.selectFirst(".comicParticulars-synopsis, .intro, meta[name=description]")?.let {
                if (it.tagName() == "meta") it.attr("content") else it.text()
            }
            thumbnail_url = document.selectFirst("img.lazyload, img")?.imgUrl()
            author = document.select("div:contains(作者) a, span:contains(作者) + a").eachText().joinToString()
            status = when {
                document.text().contains("連載中") || document.text().contains("连载中") -> SManga.ONGOING
                document.text().contains("已完結") || document.text().contains("已完结") -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.doc().select("a[href*=/chapter]").map { el ->
            SChapter.create().apply {
                name = el.text().trim()
                setUrlWithoutDomain(el.attr("abs:href"))
            }
        }.distinctBy { it.url }
    }

    override fun pageListParse(response: Response): List<Page> {
        val images = response.doc().select("img.lazy-read, img[data-src], .comicContent-image img, img").map { it.imgUrl() }
            .filter { url ->
                url.startsWith("http") &&
                    listOf("cover", "logo", "icon", "avatar").none { url.contains(it) }
            }
            .distinct()
        return images.mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun Response.doc(): Document = Jsoup.parse(body!!.string(), request.url.toString())

    private fun Element.imgUrl(): String = when {
        hasAttr("data-src") && attr("data-src").isNotBlank() -> absUrl("data-src")
        else -> absUrl("src")
    }
}
