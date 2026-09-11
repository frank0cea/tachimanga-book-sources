package eu.kanade.tachiyomi.extension.zh.mangabz

import eu.kanade.tachiyomi.source.SourceFactory

class MangabzFactory : SourceFactory {
    override fun createSources() = listOf(
        Mangabz("Mangabz", "https://www.mangabz.com"),
        Mangabz("Xmanhua", "https://www.xmanhua.com"),
    )
}
