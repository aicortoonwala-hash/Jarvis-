package com.aicortoonwala.jarvis

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MarketClient {
    fun niftyPrice(): String {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("https://query1.finance.yahoo.com/v8/finance/chart/%5ENSEI?interval=1m&range=1d")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 10000
                setRequestProperty("User-Agent", "Mozilla/5.0 JARVIS")
                setRequestProperty("Accept", "application/json")
            }
            val code = connection.responseCode
            if (code !in 200..299) return "Nifty data is unavailable right now."
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val result = JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
            val meta = result.getJSONObject("meta")
            val price = meta.optDouble("regularMarketPrice", Double.NaN)
            if (price.isNaN()) "I couldn't get the Nifty price right now."
            else "Nifty 50 is around ${"%.2f".format(java.util.Locale.US, price)}."
        } catch (_: Exception) {
            "I couldn't get the Nifty price right now."
        } finally {
            connection?.disconnect()
        }
    }
}
