package com.aicortoonwala.jarvis

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.location.LocationManager

class LocationHelper(private val context: Context) {
    fun lastLocation(): Pair<Double, Double>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = manager.getProviders(true)
        return providers.asSequence().mapNotNull { provider ->
            try { manager.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
        }.maxByOrNull { it.time }?.let { it.latitude to it.longitude }
    }
}
