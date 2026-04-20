package com.example.watcher.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class PlaceClusterManager(private val context: Context) {

    data class PlaceSnapshot(
        val clusterId: String,
        val placeType: String,
        val geohash: String
    )

    private val client by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    suspend fun snapshot(): PlaceSnapshot? {
        if (!hasPermission()) return null
        val location = withTimeoutOrNull(2_500L) { currentLocation() } ?: return null
        val geohash = Geohash.encode(
            latitude = location.latitude,
            longitude = location.longitude,
            precision = 7
        )
        val clusterId = geohash.take(6)
        return PlaceSnapshot(
            clusterId = clusterId,
            placeType = inferPlaceType(clusterId),
            geohash = geohash
        )
    }

    fun markPlaceType(clusterId: String, type: String) {
        if (clusterId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("${type}_cluster", clusterId).apply()
    }

    private fun inferPlaceType(clusterId: String): String {
        if (clusterId.isBlank()) return ""
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return when (clusterId) {
            prefs.getString("home_cluster", null) -> "home"
            prefs.getString("office_cluster", null) -> "office"
            else -> "unknown"
        }
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? = suspendCancellableCoroutine { cont ->
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location -> cont.resume(location) }
            .addOnFailureListener { cont.resume(null) }
            .addOnCanceledListener { cont.resume(null) }
    }

    private companion object {
        const val PREFS_NAME = "place_history"
    }
}

private object Geohash {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun encode(latitude: Double, longitude: Double, precision: Int): String {
        var latMin = -90.0
        var latMax = 90.0
        var lonMin = -180.0
        var lonMax = 180.0
        var bit = 0
        var ch = 0
        var evenBit = true
        val result = StringBuilder()

        while (result.length < precision) {
            if (evenBit) {
                val lonMid = (lonMin + lonMax) / 2.0
                if (longitude >= lonMid) {
                    ch = ch or (1 shl (4 - bit))
                    lonMin = lonMid
                } else {
                    lonMax = lonMid
                }
            } else {
                val latMid = (latMin + latMax) / 2.0
                if (latitude >= latMid) {
                    ch = ch or (1 shl (4 - bit))
                    latMin = latMid
                } else {
                    latMax = latMid
                }
            }

            evenBit = !evenBit
            if (bit < 4) {
                bit++
            } else {
                result.append(BASE32[ch])
                bit = 0
                ch = 0
            }
        }

        return result.toString()
    }
}
