package com.example.luminawallpapers.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val isFromGps: Boolean
)

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Retrieves the best available user location:
     * 1. Checks cached last known location (instant)
     * 2. If null, requests a fast single active location update (GPS / Network) with a 3.5s timeout
     * 3. Gracefully returns null if hardware location is disabled or permission denied
     */
    suspend fun getBestLocation(context: Context): UserLocation? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) return@withContext null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        // 1. Try cached last known locations first for instant zero-latency response
        try {
            var bestLoc: Location? = null
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null) {
                        if (bestLoc == null || loc.time > bestLoc.time || loc.accuracy < bestLoc.accuracy) {
                            bestLoc = loc
                        }
                    }
                }
            }

            // If we got a fresh cached location (< 30 mins old), use it immediately
            val now = System.currentTimeMillis()
            if (bestLoc != null && (now - bestLoc.time) < 30 * 60 * 1000L) {
                return@withContext UserLocation(bestLoc.latitude, bestLoc.longitude, isFromGps = true)
            }

            // 2. Request a fresh active location fix with a short timeout
            val freshLoc = withTimeoutOrNull(3500L) {
                requestFreshLocation(context, locationManager)
            }

            if (freshLoc != null) {
                return@withContext UserLocation(freshLoc.latitude, freshLoc.longitude, isFromGps = true)
            }

            // Fallback to older cached location if available
            bestLoc?.let {
                UserLocation(it.latitude, it.longitude, isFromGps = true)
            }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun requestFreshLocation(
        context: Context,
        locationManager: LocationManager
    ): Location? = suspendCancellableCoroutine { continuation ->
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancellationSignal = CancellationSignal()
                val executor = Executors.newSingleThreadExecutor()

                val provider = when {
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    else -> LocationManager.PASSIVE_PROVIDER
                }

                locationManager.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    executor
                ) { location ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }

                continuation.invokeOnCancellation {
                    cancellationSignal.cancel()
                    executor.shutdown()
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                val provider = when {
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    else -> LocationManager.PASSIVE_PROVIDER
                }

                locationManager.requestSingleUpdate(provider, listener, null)

                continuation.invokeOnCancellation {
                    locationManager.removeUpdates(listener)
                }
            }
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
    }
}
