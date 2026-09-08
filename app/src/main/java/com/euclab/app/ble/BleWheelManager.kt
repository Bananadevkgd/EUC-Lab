package com.euclab.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.euclab.app.data.BleCandidate
import com.euclab.app.data.LinkState
import com.euclab.app.data.WheelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.zip.CRC32

class BleWheelManager(private val context: Context) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetoothManager?.adapter
    private val decoder = VeteranFrameDecoder()
    private var gatt: BluetoothGatt? = null
    private var wheelCharacteristic: BluetoothGattCharacteristic? = null
    private val commandHandler = Handler(Looper.getMainLooper())
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("ble_profile", Context.MODE_PRIVATE)
    private var currentAddress: String? = null
    private var autoConnectScan = false

    private val _lightOn = MutableStateFlow(false)
    val lightOn: StateFlow<Boolean> = _lightOn.asStateFlow()

    private val devicesByAddress = linkedMapOf<String, android.bluetooth.BluetoothDevice>()
    private val candidateMap = linkedMapOf<String, BleCandidate>()
    private val _candidates = MutableStateFlow<List<BleCandidate>>(emptyList())
    val candidates: StateFlow<List<BleCandidate>> = _candidates.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handleScanResult(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::handleScanResult)
        override fun onScanFailed(errorCode: Int) {
            autoConnectScan = false
            WheelRepository.setLink(LinkState.ERROR, "BLE scan failed: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val name = result.scanRecord?.deviceName ?: runCatching { device.name }.getOrNull() ?: "Unnamed BLE"
        val uuids = result.scanRecord?.serviceUuids.orEmpty().map { it.uuid }
        val likely = uuids.contains(SERVICE_UUID) ||
            name.contains("Veteran", ignoreCase = true) ||
            name.contains("Sherman", ignoreCase = true) ||
            name.contains("Leaper", ignoreCase = true) ||
            name.startsWith("LK", ignoreCase = true)

        devicesByAddress[device.address] = device
        candidateMap[device.address] = BleCandidate(name, device.address, result.rssi, likely)
        _candidates.value = candidateMap.values
            .sortedWith(compareByDescending<BleCandidate> { it.likelyEuc }.thenByDescending { it.rssi })
            .take(20)

        val saved = rememberedWheelAddress()
        if (autoConnectScan && saved != null && device.address.equals(saved, ignoreCase = true)) {
            autoConnectScan = false
            connect(device.address)
        }
    }

    fun canScan(): Boolean {
        if (Build.VERSION.SDK_INT < 31) {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    }

    fun canConnect(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun isAutoConnectEnabled(): Boolean = prefs.getBoolean("auto_connect", true)
    fun setAutoConnectEnabled(enabled: Boolean) { prefs.edit().putBoolean("auto_connect", enabled).apply() }
    fun rememberedWheelAddress(): String? = prefs.getString("last_address", null)
    fun rememberedWheelName(): String? = prefs.getString("last_name", null)

    @SuppressLint("MissingPermission")
    fun startScan() = startScanInternal(autoTarget = false)

    @SuppressLint("MissingPermission")
    fun startAutoConnect() {
        if (!isAutoConnectEnabled() || rememberedWheelAddress().isNullOrBlank()) return
        if (!canScan() || !canConnect()) return
        if (WheelRepository.linkState.value in setOf(LinkState.CONNECTED, LinkState.CONNECTING, LinkState.DISCOVERING)) return
        startScanInternal(autoTarget = true)
    }

    @SuppressLint("MissingPermission")
    private fun startScanInternal(autoTarget: Boolean) {
        if (!canScan()) {
            WheelRepository.setLink(LinkState.ERROR, "Bluetooth permission is missing")
            return
        }
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            WheelRepository.setLink(LinkState.ERROR, "Bluetooth is unavailable or disabled")
            return
        }
        runCatching { scanner.stopScan(scanCallback) }
        candidateMap.clear()
        devicesByAddress.clear()
        _candidates.value = emptyList()
        autoConnectScan = autoTarget
        WheelRepository.setLink(LinkState.SCANNING, if (autoTarget) "Looking for saved wheel…" else "Scanning nearby BLE devices…")
        scanner.startScan(scanCallback)
        commandHandler.postDelayed({
            if (WheelRepository.linkState.value == LinkState.SCANNING) {
                runCatching { scanner.stopScan(scanCallback) }
                autoConnectScan = false
                WheelRepository.setLink(LinkState.IDLE, "Scan stopped")
            }
        }, if (autoTarget) 12_000L else 20_000L)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!canScan()) return
        autoConnectScan = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        if (WheelRepository.linkState.value == LinkState.SCANNING) {
            WheelRepository.setLink(LinkState.IDLE, "Scan stopped")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        if (!canConnect()) {
            WheelRepository.setLink(LinkState.ERROR, "Bluetooth connect permission is missing")
            return
        }
        val device = devicesByAddress[address] ?: runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            WheelRepository.setLink(LinkState.ERROR, "Device not found")
            return
        }
        stopScan()
        reconnectHandler.removeCallbacksAndMessages(null)
        decoder.reset()
        WheelRepository.clearTelemetry()
        WheelRepository.clearBms()
        wheelCharacteristic = null
        runCatching { gatt?.close() }
        currentAddress = address
        WheelRepository.setLink(LinkState.CONNECTING, "Connecting to ${candidateMap[address]?.name ?: address}…")
        gatt = device.connectGatt(context, false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        reconnectHandler.removeCallbacksAndMessages(null)
        autoConnectScan = false
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        currentAddress = null
        wheelCharacteristic = null
        decoder.reset()
        WheelRepository.clearTelemetry()
        WheelRepository.clearBms()
        WheelRepository.setLink(LinkState.IDLE, "Disconnected")
    }

    private fun rememberConnectedWheel() {
        val address = currentAddress ?: return
        val name = candidateMap[address]?.name ?: rememberedWheelName() ?: address
        prefs.edit().putString("last_address", address).putString("last_name", name).apply()
    }

    private fun scheduleReconnect() {
        if (!isAutoConnectEnabled() || rememberedWheelAddress().isNullOrBlank()) return
        reconnectHandler.removeCallbacksAndMessages(null)
        reconnectHandler.postDelayed({ startAutoConnect() }, 1800L)
    }

    fun setLight(on: Boolean): Boolean {
        val ver = currentModelVersion()
        val ok = if (ver >= 3) {
            sendStream(buildVeteranCommandOld(0x0D, 8, if (on) 1 else 0) + buildVeteranCommandNew(0x0D, 8, if (on) 1 else 0))
        } else sendCommand(if (on) "SetLightON".encodeToByteArray() else "SetLightOFF".encodeToByteArray())
        if (ok) _lightOn.value = on
        return ok
    }

    fun beep(): Boolean {
        val old = buildVeteranCommandOld(0x0E, 9, 1, byte5 = 0x00)
        val newer = buildVeteranCommandNew(0x0E, 9, 1, byte5 = 0x00, byte6 = 0x00)
        return sendStream(old + newer)
    }

    fun setKeyToneVolume(percent: Int): Boolean = sendNew(0x1C, 23, percent.coerceIn(0, 100))

    fun setPedalMode(mode: Int): Boolean {
        val value = when (mode) { 0 -> 3; 1 -> 2; 2 -> 1; else -> return false }
        return if (currentModelVersion() >= 3) sendOldAndNew(0x0C, 7, value)
        else sendCommand(when (mode) { 0 -> "SETh"; 1 -> "SETm"; else -> "SETs" }.encodeToByteArray())
    }

    fun resetTrip(): Boolean = if (currentModelVersion() >= 3) {
        sendStream(buildVeteranCommandOld(0x0B, 6, 1, 0x00) + buildVeteranCommandNew(0x0D, 8, 1, 0x00, 0x02))
    } else sendCommand("CLEARMETER".encodeToByteArray())

    fun setAlarmSpeed(kmh: Int): Boolean = sendOldAndNew(0x11, 12, kmh.coerceIn(0, 120))
    fun setPedalTilt(tenthsDeg: Int): Boolean = sendOldAndNew(0x10, 11, tenthsDeg.coerceIn(-80, 80))
    fun setTransportMode(enabled: Boolean): Boolean = sendNew(0x16, 17, if (enabled) 1 else 0)
    fun setHighSpeedMode(enabled: Boolean): Boolean = sendNew(0x1A, 21, if (enabled) 1 else 0)
    fun setLowVoltageMode(enabled: Boolean): Boolean = sendNew(0x19, 20, if (enabled) 1 else 0)
    fun setScreenBacklight(percent: Int): Boolean = sendNew(0x14, 15, percent.coerceIn(0, 100))
    fun setStopSpeed(raw: Int): Boolean = sendNew(0x11, 12, raw.coerceIn(0, 100))
    fun setPwmLimitRaw(raw: Int): Boolean = sendNew(0x12, 13, raw.coerceIn(0, 100))
    fun setVoltageCorrection(value: Int): Boolean = sendNew(0x18, 19, value.coerceIn(-15, 15))
    fun setMaxChargeVoltageRaw(value: Int): Boolean = sendNew(0x1D, 24, value.coerceIn(0, 120))
    fun setBrakePressureAlarm(value: Int): Boolean = sendNew(0x22, 29, value.coerceIn(0, 150))
    fun setLateralCutoffAngle(angle: Int): Boolean = sendOldAndNew(0x16, 17, angle.coerceIn(0, 90), newByte6 = 0x00)
    fun setDynamicAssist(value: Int): Boolean = sendNew(0x1F, 26, value.coerceIn(0, 100))
    fun setAccelerationLimit(value: Int): Boolean = sendNew(0x21, 28, value.coerceIn(0, 100))
    fun setWheelDisplayMiles(miles: Boolean): Boolean = sendNew(0x17, 18, if (miles) 1 else 0)
    fun setPedalHardness(value: Int): Boolean = sendNew(0x0F, 10, value.coerceIn(0, 100))
    fun calibrate(): Boolean = sendStream(buildVeteranCommandNew(0x15, 16, 1, byte5 = 0x01, byte6 = 0x02))

    private fun currentModelVersion(): Int = WheelRepository.telemetry.value?.firmwareRaw?.div(1000) ?: 0
    private fun sendNew(cmd: Int, pos: Int, value: Int): Boolean = sendStream(buildVeteranCommandNew(cmd, pos, value, 0x01, 0x02))
    private fun sendOldAndNew(cmd: Int, pos: Int, value: Int, oldByte5: Int = 0x01, newByte6: Int = 0x00): Boolean =
        sendStream(buildVeteranCommandOld(cmd, pos, value, oldByte5) + buildVeteranCommandNew(cmd, pos, value, oldByte5, newByte6))

    private fun buildVeteranCommandOld(cmdByte: Int, valuePosition: Int, value: Int, byte5: Int = 0x01): ByteArray {
        val payload = ByteArray(valuePosition + 1) { 0x80.toByte() }
        payload[0] = 0x4C; payload[1] = 0x6B; payload[2] = 0x41; payload[3] = 0x70
        payload[4] = cmdByte.toByte(); payload[5] = byte5.toByte(); payload[valuePosition] = value.toByte()
        return appendVeteranCrc(payload)
    }

    private fun buildVeteranCommandNew(cmdByte: Int, valuePosition: Int, value: Int, byte5: Int = 0x01, byte6: Int = 0x00): ByteArray {
        val payload = ByteArray(valuePosition + 1) { 0x80.toByte() }
        payload[0] = 0x4C; payload[1] = 0x64; payload[2] = 0x41; payload[3] = 0x70
        payload[4] = cmdByte.toByte(); payload[5] = byte5.toByte(); payload[6] = byte6.toByte(); payload[valuePosition] = value.toByte()
        return appendVeteranCrc(payload)
    }

    private fun appendVeteranCrc(payload: ByteArray): ByteArray {
        val crc = CRC32().apply { update(payload) }.value
        return payload + byteArrayOf(((crc shr 24) and 0xFF).toByte(), ((crc shr 16) and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte(), (crc and 0xFF).toByte())
    }

    private fun sendStream(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + 20, bytes.size)
            chunks += bytes.copyOfRange(offset, end)
            offset = end
        }
        if (!sendCommand(chunks.first())) return false
        chunks.drop(1).forEachIndexed { index, chunk -> commandHandler.postDelayed({ sendCommand(chunk) }, 55L * (index + 1)) }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun sendCommand(bytes: ByteArray): Boolean {
        if (!canConnect() || WheelRepository.linkState.value != LinkState.CONNECTED) return false
        val currentGatt = gatt ?: return false
        val characteristic = wheelCharacteristic ?: return false
        return if (Build.VERSION.SDK_INT >= 33) {
            currentGatt.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run { characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE; characteristic.value = bytes; currentGatt.writeCharacteristic(characteristic) }
        }
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(callbackGatt: BluetoothGatt, status: Int, newState: Int) {
            if (gatt != null && callbackGatt !== gatt) return
            when (newState) {
                android.bluetooth.BluetoothProfile.STATE_CONNECTED -> {
                    WheelRepository.setLink(LinkState.DISCOVERING, "Connected. Discovering services…")
                    callbackGatt.discoverServices()
                }
                android.bluetooth.BluetoothProfile.STATE_DISCONNECTED -> {
                    wheelCharacteristic = null
                    decoder.reset()
                    WheelRepository.clearTelemetry()
                    WheelRepository.clearBms()
                    runCatching { callbackGatt.close() }
                    if (callbackGatt === gatt) gatt = null
                    WheelRepository.setLink(LinkState.IDLE, "Disconnected (status $status) · auto reconnect armed")
                    scheduleReconnect()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(callbackGatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) { WheelRepository.setLink(LinkState.ERROR, "Service discovery failed: $status"); return }
            val service: BluetoothGattService = callbackGatt.getService(SERVICE_UUID) ?: run { WheelRepository.setLink(LinkState.ERROR, "FFE0 service not found"); return }
            val characteristic = service.getCharacteristic(CHAR_UUID) ?: run { WheelRepository.setLink(LinkState.ERROR, "FFE1 characteristic not found"); return }
            wheelCharacteristic = characteristic
            callbackGatt.setCharacteristicNotification(characteristic, true)
            val cccd = characteristic.getDescriptor(CCCD_UUID) ?: run { WheelRepository.setLink(LinkState.ERROR, "CCCD descriptor not found"); return }
            val wrote = if (Build.VERSION.SDK_INT >= 33) {
                callbackGatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION") run { cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; callbackGatt.writeDescriptor(cccd) }
            }
            if (!wrote) WheelRepository.setLink(LinkState.ERROR, "Could not enable FFE1 notifications")
        }

        override fun onDescriptorWrite(callbackGatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid == CCCD_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    rememberConnectedWheel()
                    WheelRepository.setLink(LinkState.CONNECTED, "Veteran stream armed · waiting for DC 5A 5C")
                } else WheelRepository.setLink(LinkState.ERROR, "Notification setup failed: $status")
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION") onBytes(characteristic.value ?: return)
        }
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) = onBytes(value)
    }

    private fun onBytes(bytes: ByteArray) {
        WheelRepository.setRawPacket(bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }.take(240))
        val decoded = decoder.feed(bytes)
        decoded.forEach(WheelRepository::publishTelemetry)
        if (decoded.isNotEmpty()) decoder.latestBmsSnapshot()?.let(WheelRepository::publishBms)
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val CHAR_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
