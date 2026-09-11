package eu.kanade.tachiyomi.extension.zh.dm5

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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Dm5 : HttpSource() {
    override val name = "极速漫画"
    override val baseUrl = "https://www.1kkk.com"
    override val lang = "zh"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept-Language", "zh-TW")
        .set("Referer", "$baseUrl/")
        .set(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36",
        )

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manhua-list-p$page/", headers)
    override fun popularMangaParse(response: Response) = parseList(response, "ul.mh-list > li > div.mh-item")

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manhua-list-s2-p$page/", headers)
    override fun latestUpdatesParse(response: Response) = parseList(response, "ul.mh-list > li > div.mh-item")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?title=$query&language=1&page=$page", headers)
    }

    override fun searchMangaParse(response: Response) = parseList(response, "ul.mh-list > li, div.banner_detail_form")

    private fun parseList(response: Response, selector: String): MangasPage {
        val document = response.doc()
        val mangas = document.select(selector).map { el ->
            SManga.create().apply {
                val link = el.selectFirst("h2.title > a, .title > a")!!
                title = link.text()
                setUrlWithoutDomain(link.attr("abs:href"))
                thumbnail_url = el.selectFirst("img")?.absUrl("src")
                    ?: el.selectFirst("p.mh-cover")?.attr("style")
                        ?.substringAfter("url(")?.substringBefore(")")
            }
        }
        val hasNext = document.selectFirst("div.page-pagination a:contains(>)") != null
        return MangasPage(mangas, hasNext)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.doc()
        return SManga.create().apply {
            title = document.selectFirst("div.banner_detail_form p.title")!!.ownText()
            thumbnail_url = document.selectFirst("div.banner_detail_form img")?.absUrl("src")
            author = document.selectFirst("div.banner_detail_form p.subtitle > a")?.text()
            genre = document.select("div.banner_detail_form p.tip a").eachText().joinToString()
            description = document.selectFirst("div.banner_detail_form p.content")?.text()
            status = when (document.selectFirst("div.banner_detail_form p.tip > span > span")?.text()) {
                "连载中" -> SManga.ONGOING
                "已完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.doc()
        document.selectFirst(".warning-bar")?.let { throw Exception(it.text()) }
        val container = document.selectFirst("div#chapterlistload")
            ?: throw Exception("章节列表为空，请先用 WebView 打开确认")
        return container.select("li > a").map { el ->
            SChapter.create().apply {
                setUrlWithoutDomain(el.attr("abs:href"))
                name = el.selectFirst("p.title")?.text() ?: el.text()
                el.selectFirst("p.tip")?.text()?.let { date_upload = DATE_FORMAT.parseOrZero(it) }
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.doc()
        val images = document.select("div#barChapter > img.load-src")
        if (images.isNotEmpty()) {
            return images.mapIndexed { index, it -> Page(index, imageUrl = it.absUrl("data-src")) }
        }
        val script = document.selectFirst("script:containsData(DM5_MID)")?.data()
            ?: throw Exception("未找到章节数据")
        val cid = script.substringAfter("var DM5_CID=").substringBefore(";")
        val mid = script.substringAfter("var DM5_MID=").substringBefore(";")
        val dt = script.substringAfter("var DM5_VIEWSIGN_DT=\"").substringBefore("\";")
        val sign = script.substringAfter("var DM5_VIEWSIGN=\"").substringBefore("\";")
        val imageCount = script.substringAfter("var DM5_IMAGE_COUNT=").substringBefore(";").toInt()
        val requestUrl = document.location()
        return (1..imageCount).map {
            val url = requestUrl.toHttpUrl().newBuilder()
                .addPathSegment("chapterfun.ashx")
                .addQueryParameter("cid", cid)
                .addQueryParameter("page", it.toString())
                .addQueryParameter("key", "")
                .addQueryParameter("language", "1")
                .addQueryParameter("gtk", "6")
                .addQueryParameter("_cid", cid)
                .addQueryParameter("_mid", mid)
                .addQueryParameter("_dt", dt)
                .addQueryParameter("_sign", sign)
                .build()
            Page(it - 1, url = url.toString())
        }
    }

    override fun imageUrlParse(response: Response): String {
        val script = Unpacker.unpack(response.body!!.string())
        val pix = script.substringAfter("var pix=\"").substringBefore("\"")
        val pvalue = script.substringAfter("var pvalue=[\"").substringBefore("\"")
        val query = script.substringAfter("pix+pvalue[i]+\"").substringBefore("\"")
        return pix + pvalue + query
    }

    private fun Response.doc(): Document = Jsoup.parse(body!!.string(), request.url.toString())

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }

        private fun SimpleDateFormat.parseOrZero(value: String): Long = try {
            parse(value)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
