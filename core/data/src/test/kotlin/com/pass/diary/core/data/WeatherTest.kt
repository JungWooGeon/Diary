package com.pass.diary.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherTest {
    @Test fun parsesOpenMeteoDaily() {
        val json = """{"latitude":37.5,"daily_units":{},"daily":{"time":["2026-11-20"],"weather_code":[61],"temperature_2m_max":[11.6]}}"""
        assertEquals(Weather(WeatherKind.RAIN, 12), WeatherRepository.parse(json))
    }

    @Test fun nullDayIsNoWeather() {
        assertNull(WeatherRepository.parse("""{"daily":{"weather_code":[null],"temperature_2m_max":[null]}}""".replace("null", "")))
    }

    @Test fun roundTrip() {
        val w = Weather(WeatherKind.SNOW, -3)
        assertEquals(w, Weather.decode(w.encode()))
        assertNull(Weather.decode("hail|x"))
    }
}
