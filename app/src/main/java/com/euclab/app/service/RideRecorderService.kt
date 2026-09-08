package com.euclab.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.euclab.app.data.WheelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.Instant

class RideRecorderService : Service(), LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var collectJob: Job? = null
    private var writer: BufferedWriter? = null
    private var currentFile: File? = null
    private var latestLocation: Location? = null
    private lateinit var locationManager: LocationManager

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LocationManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Ride recorder", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (WheelRepository.recording.value) return
        val ridesDir = File(filesDir, "rides").apply { mkdirs() }
        val file = File(ridesDir, "ride_${System.currentTimeMillis()}.csv")
        currentFile = file
        writer = BufferedWriter(FileWriter(file)).apply {
            write("timestamp_iso,timestamp_ms,speed_kmh,voltage_v,phase_current_a,mosfet_temp_c,pitch_deg,pwm_percent,trip_km,total_km,firmware_raw,charging,battery_percent,model,auto_off_sec,latitude,longitude,gps_accuracy_m,altitude_m,gps_speed_mps,bearing_deg\n")
            flush()
        }
        WheelRepository.setLastLogPath(file.absolutePath)
        WheelRepository.setRecording(true)
        startForeground(NOTIFICATION_ID, buildNotification(file.name))
        startLocation()

        collectJob?.cancel()
        collectJob = scope.launch {
            WheelRepository.telemetry.filterNotNull().collect { t ->
                val loc = latestLocation
                writer?.apply {
                    write(
                        listOf(
                            Instant.ofEpochMilli(t.timestampMs).toString(),
                            t.timestampMs,
                            t.speedKmh,
                            t.voltageV,
                            t.phaseCurrentA,
                            t.mosfetTempC,
                            t.pitchDeg,
                            t.pwmPercent,
                            t.tripKm,
                            t.totalKm,
                            t.firmwareRaw,
                            t.charging,
                            t.batteryPercent,
                            t.model.replace(',', ' '),
                            t.autoOffSec,
                            loc?.latitude ?: "",
                            loc?.longitude ?: "",
                            loc?.accuracy ?: "",
                            if (loc?.hasAltitude() == true) loc.altitude else "",
                            if (loc?.hasSpeed() == true) loc.speed else "",
                            if (loc?.hasBearing() == true) loc.bearing else "",
                        ).joinToString(",")
                    )
                    newLine()
                    flush()
                }
            }
        }
    }

    private fun startLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 0f, this, Looper.getMainLooper())
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        val old = latestLocation
        if (old == null || location.accuracy <= old.accuracy + 15f || location.time > old.time + 4000L) latestLocation = location
    }
    @Deprecated("Deprecated Android callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    private fun stopRecording() {
        collectJob?.cancel()
        collectJob = null
        runCatching { locationManager.removeUpdates(this) }
        runCatching { writer?.flush() }
        runCatching { writer?.close() }
        writer = null
        currentFile?.let { file -> scope.launch { exportRide(file) } }
        currentFile = null
        latestLocation = null
        WheelRepository.setRecording(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun exportRide(file: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/EUC Lab/Rides")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching
            contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        }
    }

    private fun buildNotification(fileName: String): Notification = Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        .setContentTitle("EUC Lab · запись поездки + GPS")
        .setContentText(fileName)
        .setOngoing(true)
        .build()

    override fun onDestroy() {
        collectJob?.cancel()
        runCatching { locationManager.removeUpdates(this) }
        runCatching { writer?.close() }
        currentFile?.let { exportRide(it) }
        WheelRepository.setRecording(false)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.euclab.app.START_RIDE"
        const val ACTION_STOP = "com.euclab.app.STOP_RIDE"
        private const val CHANNEL_ID = "ride_recorder"
        private const val NOTIFICATION_ID = 1001
    }
}
