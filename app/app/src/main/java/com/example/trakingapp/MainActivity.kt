package com.example.trakingapp

import android.Manifest
import android.content.pm.PackageManager
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

    // change this if your PC IP changes
   // private val BASE_URL = "http://192.168.1.22:8000"
   // private val BASE_URL = "https://trakingapp.onrender.com/api/location"
    private val BASE_URL = "http://3.26.191.239:8000"
    private val TAG = "TrackingApp"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No UI => white screen is expected
        // setContentView(R.layout.activity_main)

        fused = LocationServices.getFusedLocationProviderClient(this)

        val permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
                val fine = perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                if (fine) {
                    Toast.makeText(this, "Location permission granted kesavan hahaha", Toast.LENGTH_SHORT).show()
                    Toast.makeText(this, "Location permission granted kesavan hahaha", Toast.LENGTH_SHORT).show()
                    Toast.makeText(this, "Location permission granted kesavan hahaha", Toast.LENGTH_SHORT).show()
                    startLocationUpdates()

                    // Debug: send one test ping without GPS, just to check server
                    sendToServer(12.9345, 77.6112, 0f)
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
            Priority.PRIORITY_HIGH_ACCURACY, 5_000L // 5 seconds
        ).setMinUpdateDistanceMeters(10f)          // only if moved > 10 m
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        fused.requestLocationUpdates(request, object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                Log.d(TAG, "Got location: ${loc.latitude}, ${loc.longitude}, speed=${loc.speed}")
                sendToServer(loc.latitude, loc.longitude, loc.speed)
            }
        }, Looper.getMainLooper())
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
            .url("$BASE_URL/api/location")   // final URL = http://3.26.191.239:8000/api/location
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Send failed", e)
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Send failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    Log.d(TAG, "Server response: ${it.code}")
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Sent to server: ${it.code}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }
}
