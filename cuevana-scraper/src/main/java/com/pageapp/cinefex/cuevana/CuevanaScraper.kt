package com.pageapp.cinefex.cuevana

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

object CuevanaScraper {
    private val client = OkHttpClient.Builder().followRedirects(true).build()
    private val domains = listOf("cuevana3.ch", "cuevana.biz", "cuevana3.cc")

    suspend fun getLinks(title: String): List<String> = withContext(Dispatchers.IO) {
        val links = mutableListOf<String>()
        for (domain in domains) {
            try {
                val searchUrl = "https://$domain/?s=${title.replace(" ", "+")}"
                val request = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    val doc = Jsoup.parse(html)
                    val iframeSrcs = doc.select("iframe[src]").map { it.attr("src") }
                    for (src in iframeSrcs) {
                        var embed = src
                        if (embed.startsWith("//")) embed = "https:$embed"
                        if (embed.isNotEmpty()) links.add(embed)
                    }
                    if (links.isNotEmpty()) break
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext links
    }
}
