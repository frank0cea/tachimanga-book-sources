package eu.kanade.tachiyomi.extension.zh.colamanga

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

class ColaManga : HttpSource() {
    override val name = "可乐漫画"
    override val baseUrl = "https://www.yoyomanga.com"
    override val lang = "zh"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/show?orderBy=hits&page=$page", headers)

    override fun popularMangaParse(response: Response) = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/show?orderBy=update&page=$page", headers)

    override fun latestUpdatesParse(response: Response) = parseMangaList(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?searchString=$query&page=$page", headers)
    }

    override fun searchMangaParse(response: Response) = parseMangaList(response)

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.doc()
        val mangas = document.select("li.fed-list-item").map { el ->
            SManga.create().apply {
                val link = el.selectFirst("a.fed-list-title")!!
                title = link.text()
                setUrlWithoutDomain(link.attr("abs:href"))
                thumbnail_url = el.selectFirst("a.fed-list-pics")?.imgUrl()
            }
        }
        val hasNext = document.selectFirst("a.fed-page-skip:contains(下), a:contains(下一页)") != null
        return MangasPage(mangas, hasNext)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.doc()
        return SManga.create().apply {
            title = document.selectFirst("h1.fed-part-eone, h1")?.text().orEmpty()
            description = document.selectFirst(".fed-part-esan, .fed-text-muted, meta[name=description]")?.let {
                if (it.tagName() == "meta") it.attr("content") else it.text()
            }
            thumbnail_url = document.selectFirst("a.fed-list-pics, .fed-deta-images img")?.imgUrl()
            author = document.selectFirst("li:contains(作者) a, span:contains(作者) + a")?.text()
            genre = document.select("li:contains(类别) a, .fed-deta-info a").eachText().joinToString()
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.doc().select("div.fed-play-item a, .fed-drop-info a[href*=/manga-]").map { el ->
            SChapter.create().apply {
                name = el.text().trim()
                setUrlWithoutDomain(el.attr("abs:href"))
            }
        }.distinctBy { it.url }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.doc()
        val images = document.select("div.fed-play-item img, img.lazy, img[data-original], img[data-src]")
            .map { it.imgUrl() }
            .filter { it.startsWith("http") && !it.contains("cover") }
            .distinct()
        if (images.isNotEmpty()) {
            return images.mapIndexed { i, url -> Page(i, imageUrl = url) }
        }
        val packed = document.select("script").map { it.data() }.firstOrNull { it.contains("eval(") }
        if (packed != null) {
            val urls = Regex("""https?://[^"'\\]+\.(?:jpg|jpeg|png|webp)""").findAll(packed).map { it.value }.toList()
            if (urls.isNotEmpty()) {
                return urls.mapIndexed { i, url -> Page(i, imageUrl = url) }
            }
        }
        return emptyList()
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun Response.doc(): Document = Jsoup.parse(body!!.string(), request.url.toString())

    private fun Element.imgUrl(): String = when {
        hasAttr("data-original") && attr("data-original").isNotBlank() -> absUrl("data-original")
        hasAttr("data-src") && attr("data-src").isNotBlank() -> absUrl("data-src")
        else -> absUrl("src")
    }
}
