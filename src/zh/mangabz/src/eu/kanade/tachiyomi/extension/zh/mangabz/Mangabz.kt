package eu.kanade.tachiyomi.extension.zh.mangabz

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

open class Mangabz(
    override val name: String,
    override val baseUrl: String,
) : HttpSource() {
    override val lang = "zh"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("User-Agent", USER_AGENT)

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga-list-p$page/", headers)
    override fun popularMangaParse(response: Response) = parseList(response)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga-list-0-0-2-p$page/", headers)
    override fun latestUpdatesParse(response: Response) = parseList(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?title=$query&page=$page", headers)
    }

    override fun searchMangaParse(response: Response) = parseList(response)

    private fun parseList(response: Response): MangasPage {
        val document = response.doc()
        val mangas = document.select(".mh-list > li").map { el ->
            SManga.create().apply {
                title = el.selectFirst("h2")!!.text()
                setUrlWithoutDomain(el.selectFirst("a")!!.attr("abs:href"))
                thumbnail_url = el.selectFirst("img")?.absUrl("src")
                    ?: el.selectFirst("p.mh-cover")?.attr("style")
                        ?.substringAfter("url(")?.substringBefore(")")
            }
        }
        val hasNext = document.select(".page-pagination a").last()?.text() == ">"
        return MangasPage(mangas, hasNext)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.doc()
        val details = document.select(".detail-info-tip > *")
        return SManga.create().apply {
            title = document.selectFirst(".detail-info-title")!!.ownText()
            thumbnail_url = document.selectFirst(".detail-info-cover")?.absUrl("src")
            author = details.getOrNull(0)?.children()?.joinToString { it.ownText() }
            status = when (details.getOrNull(1)?.child(0)?.ownText()) {
                "连载中", "連載中" -> SManga.ONGOING
                "已完结", "已完結" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = details.getOrNull(2)?.children()?.joinToString { it.ownText() }
            description = document.selectFirst(".detail-info-content")?.text()
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.doc().select("#chapterlistload a").map { el ->
            SChapter.create().apply {
                name = el.ownText().ifBlank { el.text() }
                setUrlWithoutDomain(el.attr("abs:href"))
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.doc()
        val location = document.location().substringBefore("?")
        val script = document.select("script").map { it.data() }
            .firstOrNull { it.contains("IMAGE_COUNT") }
            ?: return document.select("img").mapIndexed { i, img -> Page(i, imageUrl = img.absUrl("src")) }
        val countKey = if (script.contains("XMANHUA_IMAGE_COUNT")) "XMANHUA_IMAGE_COUNT" else "MANGABZ_IMAGE_COUNT"
        val cidKey = if (script.contains("XMANHUA_CID")) "XMANHUA_CID" else "MANGABZ_CID"
        val count = script.substringAfter("$countKey=").substringBefore(";").toInt()
        val cid = script.substringAfter("$cidKey=").substringBefore(";")
        return List(count) { index ->
            Page(index, url = "$location/chapterimage.ashx?cid=$cid&page=${index + 1}")
        }
    }

    override fun imageUrlParse(response: Response): String {
        val script = Unpacker.unpack(response.body!!.string())
        val prefix = script.substringAfter("pix=\"").substringBefore("\"")
        val path = script.substringAfter("[\"").substringBefore("\"")
        return prefix + path
    }

    private fun Response.doc(): Document = Jsoup.parse(body!!.string(), request.url.toString())

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"
    }
}
