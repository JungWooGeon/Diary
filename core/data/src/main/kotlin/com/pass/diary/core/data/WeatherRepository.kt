package com.pass.diary.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** A day's weather as stored in Entry.weather: "<kind>|<max °C>", e.g. "rain|12" (web reads the same string). */
enum class WeatherKind(val key: String, val label: String) {
    CLEAR("clear", "맑음"), CLOUDY("cloudy", "흐림"), FOG("fog", "안개"), RAIN("rain", "비"), SNOW("snow", "눈"), STORM("storm", "뇌우");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }

        /** WMO weather interpretation codes (Open-Meteo docs). */
        fun fromWmo(code: Int) = when (code) {
            0, 1 -> CLEAR
            2, 3 -> CLOUDY
            45, 48 -> FOG
            in 51..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> STORM
            else -> CLOUDY
        }
    }
}

data class Weather(val kind: WeatherKind, val maxC: Int) {
    fun encode() = "${kind.key}|$maxC"
    val label get() = "${kind.label} $maxC°"

    companion object {
        fun decode(s: String?): Weather? {
            val (k, t) = s?.split('|')?.takeIf { it.size == 2 } ?: return null
            return Weather(WeatherKind.of(k) ?: return null, t.toIntOrNull() ?: return null)
        }
    }
}

/**
 * Open-Meteo (no key, free, CC BY 4.0 — credited in the licenses screen). Daily weather code + max temperature for a
 * date at a coarse location (rounded to 0.1°, ~10 km, so the request never carries a precise position). Cached per
 * (date, cell): a day's weather is asked once.
 */
@Singleton
class WeatherRepository @Inject constructor(@ApplicationContext context: Context) {
    private val cache = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)

    suspend fun weather(date: String, lat: Double, lon: Double): Weather? {
        val la = "%.1f".format(Locale.US, lat); val lo = "%.1f".format(Locale.US, lon)
        val key = "$date@$la,$lo"
        cache.getString(key, null)?.let { return Weather.decode(it) }
        val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        val today = LocalDate.now()
        if (day.isAfter(today)) return null
        // forecast covers ~3 months back; older days come from the archive.
        val host = if (day.isBefore(today.minusDays(80))) "archive-api.open-meteo.com/v1/archive" else "api.open-meteo.com/v1/forecast"
        val url = "https://$host?latitude=$la&longitude=$lo&daily=weather_code,temperature_2m_max&timezone=auto&start_date=$date&end_date=$date"
        val w = withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000; conn.readTimeout = 8_000
                try { parse(conn.inputStream.bufferedReader().use { it.readText() }) } finally { conn.disconnect() }
            }.getOrNull()
        } ?: return null
        // Today's weather may still change; only finished days are cached.
        if (day.isBefore(today)) cache.edit().putString(key, w.encode()).apply()
        return w
    }

    companion object {
        internal fun parse(json: String): Weather? {
            val daily = Json.parseToJsonElement(json).jsonObject["daily"]?.jsonObject ?: return null
            val code = daily["weather_code"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.int ?: return null
            val max = daily["temperature_2m_max"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.double ?: return null
            return Weather(WeatherKind.fromWmo(code), max.roundToInt())
        }
    }
}
