package com.example.trakingapp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var fused: FusedLocationProviderClient
    private val client = OkHttpClient()

    // ⚠️ Use your EC2 public IP or domain (NO /api/location here)
    private val BASE_URL = "http://3.26.191.239:8000"

    private val TAG = "TrackingApp"

    // Throttling / filtering
    private var lastSentTime: Long = 0L
    private var lastSentLat: Double? = null
    private var lastSentLon: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No UI -> white screen is ok

        fused = LocationServices.getFusedLocationProviderClient(this)

        val permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
                val fine = perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                if (fine) {
                    Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show()
                    startLocationUpdates()
                } else {
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
                }
            }

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5_000L           // request every 5 seconds
        )
            .setMinUpdateIntervalMillis(5_000L) // don't get updates faster than this
            .setMinUpdateDistanceMeters(5f)     // at least 5 m movement
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        fused.requestLocationUpdates(
            request,
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    handleNewLocation(loc)
                }
            },
            Looper.getMainLooper()
        )
    }

    private fun handleNewLocation(loc: Location) {
        val lat = loc.latitude
        val lon = loc.longitude
        val speed = loc.speed

        Log.d(TAG, "Got location: $lat, $lon, speed=$speed")

        val now = System.currentTimeMillis()

        // Calculate distance from last sent point
        var distance = Float.MAX_VALUE
        if (lastSentLat != null && lastSentLon != null) {
            val results = FloatArray(1)
            Location.distanceBetween(
                lastSentLat!!, lastSentLon!!,
                lat, lon,
                results
            )
            distance = results[0]
        }

        // Rules:
        //  - send if moved >= 10m OR
        //  - send if 15s elapsed since last point (even if stationary)
        val timeSinceLast = now - lastSentTime
        val shouldSend = (distance >= 10f) || (timeSinceLast >= 15_000L) || lastSentTime == 0L

        if (!shouldSend) {
            Log.d(TAG, "Skipping send: distance=$distance m, timeSinceLast=$timeSinceLast ms")
            return
        }

        lastSentTime = now
        lastSentLat = lat
        lastSentLon = lon

        sendToServer(lat, lon, speed)
    }

    private fun sendToServer(lat: Double, lon: Double, speed: Float) {
        val json = """
            {
              "vehicle_id": "ANDROID01",
              "lat": $lat,
              "lon": $lon,
              "speed": $speed,
              "fuel": 0
            }
        """.trimIndent()

        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/api/location")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Send failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
                Log.d(TAG, "Server response: ${response.code}")
            }
        })
    }
}
