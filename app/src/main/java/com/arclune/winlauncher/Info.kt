package com.arclune.winlauncher

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

object Net {
    fun get(url: String, timeoutMs: Int = 8000): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.setRequestProperty("User-Agent", "Win11Launcher/1.2")
        if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
    } catch (e: Exception) { null }
}

class DayForecast(val label: String, val code: Int, val hi: Int, val lo: Int)
class Weather(val city: String, val temp: Int, val code: Int, val humidity: Int, val wind: Int, val days: List<DayForecast>)
class NewsItem(val title: String, val link: String, val date: String)

/** Cuaca (Open-Meteo, tanpa API key) dan berita (RSS BBC Indonesia) dengan cache di prefs. */
object Info {
    private val ui = Handler(Looper.getMainLooper())
    private const val WEATHER_TTL = 30 * 60 * 1000L
    private const val NEWS_TTL = 15 * 60 * 1000L

    private fun prefs(c: Context) = c.getSharedPreferences("win11", Context.MODE_PRIVATE)

    fun city(c: Context): String = prefs(c).getString("wx_city", "Jakarta") ?: "Jakarta"

    fun setCity(c: Context, name: String) {
        prefs(c).edit().putString("wx_city", name.trim().ifEmpty { "Jakarta" }).remove("wx_json").remove("wx_at").apply()
    }

    fun codeText(code: Int): String = when (code) {
        0 -> "Cerah"
        1 -> "Cerah berawan"
        2 -> "Berawan sebagian"
        3 -> "Mendung"
        45, 48 -> "Berkabut"
        in 51..57 -> "Gerimis"
        in 61..67 -> "Hujan"
        in 71..77 -> "Salju"
        in 80..82 -> "Hujan deras"
        in 95..99 -> "Badai petir"
        else -> "Tidak diketahui"
    }

    fun codeEmoji(code: Int): String = when (code) {
        0 -> "☀️"
        1 -> "🌤️"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        in 61..67 -> "🌧️"
        in 71..77 -> "❄️"
        in 80..82 -> "🌧️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    // ------------------------------------------------------------------ cuaca
    /** Ambil cuaca. [force] melewati cache. Callback dipanggil di thread utama (null bila gagal & tanpa cache). */
    fun weather(c: Context, force: Boolean = false, cb: (Weather?) -> Unit) {
        val app = c.applicationContext
        val p = prefs(app)
        val cached = p.getString("wx_json", null)
        val at = p.getLong("wx_at", 0L)
        if (!force && cached != null && System.currentTimeMillis() - at < WEATHER_TTL) {
            val w = parseWeather(cached, city(app))
            if (w != null) { cb(w); return }
        }
        Thread {
            var result: Weather? = null
            try {
                val name = city(app)
                val geo = Net.get("https://geocoding-api.open-meteo.com/v1/search?name=" +
                    URLEncoder.encode(name, "UTF-8") + "&count=1&language=id")
                val first = geo?.let { JSONObject(it).optJSONArray("results")?.optJSONObject(0) }
                if (first != null) {
                    val lat = first.getDouble("latitude"); val lon = first.getDouble("longitude")
                    val shown = first.optString("name", name)
                    val body = Net.get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
                        "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=5")
                    if (body != null) {
                        result = parseWeather(body, shown)
                        if (result != null) p.edit().putString("wx_json", body).putLong("wx_at", System.currentTimeMillis()).apply()
                    }
                }
            } catch (e: Exception) { }
            if (result == null && cached != null) result = parseWeather(cached, city(app))
            val out = result
            ui.post { cb(out) }
        }.start()
    }

    private fun parseWeather(json: String, city: String): Weather? = try {
        val o = JSONObject(json)
        val cur = o.getJSONObject("current")
        val d = o.getJSONObject("daily")
        val times = d.getJSONArray("time"); val codes = d.getJSONArray("weather_code")
        val hi = d.getJSONArray("temperature_2m_max"); val lo = d.getJSONArray("temperature_2m_min")
        val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outFmt = SimpleDateFormat("EEE", Locale("id"))
        val days = ArrayList<DayForecast>()
        for (i in 0 until times.length()) {
            val label = if (i == 0) "Hari ini" else (inFmt.parse(times.getString(i))?.let { outFmt.format(it) } ?: times.getString(i))
            days.add(DayForecast(label, codes.getInt(i), Math.round(hi.getDouble(i)).toInt(), Math.round(lo.getDouble(i)).toInt()))
        }
        Weather(city, Math.round(cur.getDouble("temperature_2m")).toInt(), cur.getInt("weather_code"),
            cur.optInt("relative_humidity_2m", 0), Math.round(cur.optDouble("wind_speed_10m", 0.0)).toInt(), days)
    } catch (e: Exception) { null }

    // ------------------------------------------------------------------ berita
    fun news(c: Context, force: Boolean = false, cb: (List<NewsItem>) -> Unit) {
        val app = c.applicationContext
        val p = prefs(app)
        val cached = p.getString("news_json", null)
        val at = p.getLong("news_at", 0L)
        if (!force && cached != null && System.currentTimeMillis() - at < NEWS_TTL) {
            cb(readNews(cached)); return
        }
        Thread {
            var list: List<NewsItem> = emptyList()
            try {
                val xml = Net.get("https://feeds.bbci.co.uk/indonesia/rss.xml")
                if (xml != null) {
                    list = parseRss(xml)
                    if (list.isNotEmpty()) p.edit().putString("news_json", writeNews(list)).putLong("news_at", System.currentTimeMillis()).apply()
                }
            } catch (e: Exception) { }
            if (list.isEmpty() && cached != null) list = readNews(cached)
            val out = list
            ui.post { cb(out) }
        }.start()
    }

    private fun parseRss(xml: String): List<NewsItem> {
        val out = ArrayList<NewsItem>()
        val p = android.util.Xml.newPullParser()
        p.setInput(StringReader(xml))
        var ev = p.eventType
        var inItem = false
        var tag = ""
        var t = ""; var l = ""; var d = ""
        while (ev != XmlPullParser.END_DOCUMENT && out.size < 15) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    if (tag == "item") { inItem = true; t = ""; l = ""; d = "" }
                }
                XmlPullParser.TEXT -> if (inItem) {
                    when (tag) { "title" -> t += p.text; "link" -> l += p.text; "pubDate" -> d += p.text }
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == "item") {
                        if (t.isNotBlank() && l.isNotBlank()) out.add(NewsItem(t.trim(), l.trim(), shortDate(d.trim())))
                        inItem = false
                    }
                    tag = ""
                }
            }
            ev = p.next()
        }
        return out
    }

    private fun shortDate(s: String): String = try {
        val d = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).parse(s)
        if (d == null) "" else SimpleDateFormat("d MMM, HH:mm", Locale("id")).format(d)
    } catch (e: Exception) { "" }

    private fun writeNews(l: List<NewsItem>): String {
        val arr = JSONArray()
        for (n in l) arr.put(JSONObject().put("t", n.title).put("l", n.link).put("d", n.date))
        return arr.toString()
    }

    private fun readNews(s: String): List<NewsItem> = try {
        val arr = JSONArray(s)
        (0 until arr.length()).map { val o = arr.getJSONObject(it); NewsItem(o.getString("t"), o.getString("l"), o.optString("d")) }
    } catch (e: Exception) { emptyList() }
}
