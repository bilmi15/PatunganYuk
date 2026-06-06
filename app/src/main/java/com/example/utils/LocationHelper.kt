package com.example.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class LocationHelper(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): LocationResult? {
        return suspendCancellableCoroutine { continuation ->
            try {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            val addressName = getAddressFromLocation(location.latitude, location.longitude)
                            continuation.resume(
                                LocationResult(
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    addressName = addressName ?: "Lat: ${String.format(Locale.US, "%.4f", location.latitude)}, Lng: ${String.format(Locale.US, "%.4f", location.longitude)}"
                                )
                            )
                        } else {
                            continuation.resume(null)
                        }
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            } catch (e: Exception) {
                continuation.resume(null)
            }
        }
    }

    private fun getAddressFromLocation(lat: Double, lng: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val street = address.thoroughfare ?: address.featureName
                val block = address.subLocality
                val city = address.locality ?: address.subAdminArea

                val parts = mutableListOf<String>()
                if (!street.isNullOrEmpty()) parts.add(street)
                if (!block.isNullOrEmpty()) parts.add(block)
                if (!city.isNullOrEmpty()) parts.add(city)

                if (parts.isNotEmpty()) parts.joinToString(", ") else address.getAddressLine(0)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val addressName: String
)
