package com.aicortoonwala.jarvis

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class MarketClient {
    fun niftyPrice(): String = quoteSymbol("^NSEI", "Nifty 50")
    fun quoteFor(command: String): String {
        val query = extractQuery(command); if (query.isBlank()) return "Tell me the stock name or symbol."
        val symbol = resolveSymbol(query) ?: return "I couldn't identify the stock $query right now."
        return quoteSymbol(symbol, query)
    }
    private fun extractQuery(command: String): String {
        val c = command.trim(); val lower = c.lowercase(Locale.getDefault())
        val patterns = listOf("share price", "stock price", "share bhav", "stock bhav", "share ka price", "stock ka price", "share rate", "stock rate", "share value")
        var q = c
        for (p in patterns) { val idx = lower.indexOf(p); if (idx >= 0) { q = (c.substring(0, idx) + " " + c.substring(idx + p.length)).trim(); break } }
        q = q.replace(Regex("(?i)\\b(batao|bata|please|karo|kar do|kitna hai|kya hai|price|bhav|rate|share|stock)\\b"), " ")
        return q.replace(Regex("\\s+"), " ").trim()
    }
    private fun resolveSymbol(query: String): String? {
        if (query.equals("nifty", true) || query.equals("nifty 50", true) || query.equals("nse", true)) return "^NSEI"
        if (query.equals("sensex", true) || query.equals("bse", true)) return "^BSESN"
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("https://query1.finance.yahoo.com/v1/finance/search?q=" + URLEncoder.encode(query, "UTF-8") + "&quotesCount=8&newsCount=0")
            connection = (url.openConnection() as HttpURLConnection).apply { requestMethod = "GET"; connectTimeout = 8000; readTimeout = 10000; setRequestProperty("User-Agent", "Mozilla/5.0 JARVIS"); setRequestProperty("Accept", "application/json") }
            if (connection.responseCode !in 200..299) return null
            val quotes = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optJSONArray("quotes") ?: return null
            var fallback: String? = null
            for (i in 0 until quotes.length()) { val item = quotes.optJSONObject(i) ?: continue; val symbol = item.optString("symbol"); val type = item.optString("quoteType").uppercase(Locale.US); if (symbol.isBlank() || type != "EQUITY") continue; if (symbol.endsWith(".NS", true)) return symbol; if (symbol.endsWith(".BO", true)) fallback = symbol }
            fallback
        } catch (_: Exception) { null } finally { connection?.disconnect() }
    }
    private fun quoteSymbol(symbol: String, label: String): String {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("https://query1.finance.yahoo.com/v8/finance/chart/" + URLEncoder.encode(symbol, "UTF-8") + "?interval=1m&range=1d")
            connection = (url.openConnection() as HttpURLConnection).apply { requestMethod = "GET"; connectTimeout = 8000; readTimeout = 10000; setRequestProperty("User-Agent", "Mozilla/5.0 JARVIS"); setRequestProperty("Accept", "application/json") }
            if (connection.responseCode !in 200..299) return "$label price is unavailable right now."
            val result = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONObject("chart").optJSONArray("result")?.optJSONObject(0) ?: return "$label price is unavailable right now."
            val meta = result.getJSONObject("meta"); val price = meta.optDouble("regularMarketPrice", Double.NaN); val currency = meta.optString("currency", "")
            if (price.isNaN()) "I couldn't get the $label price right now." else "${label.trim()} is around ${"%.2f".format(Locale.US, price)}${if (currency.isNotBlank()) " $currency" else ""}."
        } catch (_: Exception) { "I couldn't get the $label price right now." } finally { connection?.disconnect() }
    }
}
