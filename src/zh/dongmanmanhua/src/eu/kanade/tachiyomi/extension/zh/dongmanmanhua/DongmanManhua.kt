package eu.kanade.tachiyomi.extension.zh.dongmanmanhua

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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DongmanManhua : HttpSource() {
    override val name = "咚漫"
    override val baseUrl = "https://www.dongmanmanhua.cn"
    override val lang = "zh"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/dailySchedule", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val entries = response.doc().select("div#dailyList .daily_section li a, div.daily_lst.comp li a")
            .map(::mangaFromElement)
            .distinctBy { it.url }
        return MangasPage(entries, false)
    }

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/dailySchedule?sortOrder=UPDATE&webtoonCompleteType=ONGOING", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.doc()
        val day = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "div._list_SUNDAY"
            Calendar.MONDAY -> "div._list_MONDAY"
            Calendar.TUESDAY -> "div._list_TUESDAY"
            Calendar.WEDNESDAY -> "div._list_WEDNESDAY"
            Calendar.THURSDAY -> "div._list_THURSDAY"
            Calendar.FRIDAY -> "div._list_FRIDAY"
            Calendar.SATURDAY -> "div._list_SATURDAY"
            else -> "div"
        }
        val entries = document.select("div#dailyList > $day li > a")
            .map(::mangaFromElement)
            .distinctBy { it.url }
        return MangasPage(entries, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addQueryParameter("keyword", query)
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.doc()
        val entries = document.select("#content > div.card_wrap.search ul:not(#filterLayer) li a")
            .map(::mangaFromElement)
        val hasNextPage = document.selectFirst("div.more_area, div.paginate a[onclick] + a") != null
        return MangasPage(entries, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.selectFirst("p.subj")!!.text()
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.doc()
        val detailElement = document.selectFirst(".detail_header .info")
        val infoElement = document.selectFirst("#_asideDetail")
        return SManga.create().apply {
            title = document.selectFirst("h1.subj, h3.subj")!!.text()
            author = detailElement?.selectFirst(".author:nth-of-type(1)")?.ownText()
                ?: detailElement?.selectFirst(".author_area")?.ownText()
            artist = detailElement?.selectFirst(".author:nth-of-type(2)")?.ownText()
                ?: author
            genre = detailElement?.select(".genre").orEmpty().joinToString { it.text() }
            description = infoElement?.selectFirst("p.summary")?.text()
            status = with(infoElement?.selectFirst("p.day_info")?.text().orEmpty()) {
                when {
                    contains("更新") -> SManga.ONGOING
                    contains("完结") -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
            thumbnail_url = document.selectFirst("#content img")?.absUrl("src")
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var document = response.doc()
        val chapters = mutableListOf<SChapter>()
        while (true) {
            document.select("ul#_listUl li").forEach { chapters.add(chapterFromElement(it)) }
            val next = document.selectFirst("div.paginate a[onclick] + a") ?: break
            document = client.newCall(GET(next.absUrl("href"), headers)).execute().doc()
        }
        return chapters
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.selectFirst("span.subj span")!!.text()
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        date_upload = DATE_FORMAT.parseOrZero(element.selectFirst("span.date")?.text())
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.doc().select("div#_imageList > img").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("data-url").ifBlank { element.absUrl("src") })
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun Response.doc(): Document = Jsoup.parse(body!!.string(), request.url.toString())

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-M-d", Locale.ENGLISH)

        private fun SimpleDateFormat.parseOrZero(value: String?): Long {
            if (value.isNullOrBlank()) return 0L
            return try {
                parse(value)?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }
}
