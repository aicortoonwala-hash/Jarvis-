package com.aicortoonwala.jarvis

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MarketClient {
    fun niftyPrice(): String {
        return try {
            val url = URL("https://query1.finance.yahoo.com/v8/finance/chart/%5ENSEI?range=1d&interval=1m")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 10000
                setRequestProperty("User-Agent", "Mozilla/5.0 JARVIS")
            }
            if (connection.responseCode !in 200..299) return "I couldn't get the Nifty price right now."
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val meta = JSONObject(body).getJSONObject("chart").getJSONArray("result")
                .getJSONObject(0).getJSONObject("meta")
            val price = meta.optDouble("regularMarketPrice", Double.NaN)
            if (price.isNaN()) "I couldn't get the Nifty price right now."
            else "Nifty 50 is ${"%.2f".format(java.util.Locale.US, price)}."
        } catch (_: Exception) {
            "I couldn't get the Nifty price right now."
        }
    }
}
