package com.euclab.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
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

class RideRecorderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var collectJob: Job? = null
    private var writer: BufferedWriter? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
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
        writer = BufferedWriter(FileWriter(file)).apply {
            write("timestamp_iso,timestamp_ms,speed_kmh,voltage_v,phase_current_a,mosfet_temp_c,pitch_deg,pwm_percent,trip_km,total_km,firmware_raw,charging\n")
            flush()
        }
        WheelRepository.setLastLogPath(file.absolutePath)
        WheelRepository.setRecording(true)
        startForeground(NOTIFICATION_ID, buildNotification(file.name))

        collectJob?.cancel()
        collectJob = scope.launch {
            WheelRepository.telemetry.filterNotNull().collect { t ->
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
                        ).joinToString(",")
                    )
                    newLine()
                    flush()
                }
            }
        }
    }

    private fun stopRecording() {
        collectJob?.cancel()
        collectJob = null
        runCatching { writer?.flush() }
        runCatching { writer?.close() }
        writer = null
        WheelRepository.setRecording(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(fileName: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("EUC Lab · recording ride")
            .setContentText(fileName)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        collectJob?.cancel()
        runCatching { writer?.close() }
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
