package com.euclab.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.euclab.app.ble.BleWheelManager

class MainActivity : ComponentActivity() {
    private lateinit var ble: BleWheelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ble = BleWheelManager(applicationContext)
        setContent {
            AppThemeV5 {
                EucLabAppV5(ble)
            }
        }
    }

    override fun onDestroy() {
        ble.stopScan()
        super.onDestroy()
    }
}
