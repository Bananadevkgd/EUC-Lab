package com.euclab.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.euclab.app.ble.BleWheelManager

class MainActivity : ComponentActivity() {
    private lateinit var ble: BleWheelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ble = BleWheelManager(applicationContext)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                7007,
            )
        }

        Handler(Looper.getMainLooper()).postDelayed({ ble.startAutoConnect() }, 900L)

        setContent {
            AppThemeV5 {
                Box {
                    EucLabAppV5(ble)
                    V7SafetyOverlay(ble)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Handler(Looper.getMainLooper()).postDelayed({ ble.startAutoConnect() }, 500L)
    }

    override fun onDestroy() {
        ble.stopScan()
        super.onDestroy()
    }
}
