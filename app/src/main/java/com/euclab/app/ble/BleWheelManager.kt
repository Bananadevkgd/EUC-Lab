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
    }

    fun canScan(): Boolean {
        if (Build.VERSION.SDK_INT < 31) {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    }

    fun canConnect(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!canScan()) {
            WheelRepository.setLink(LinkState.ERROR, "Bluetooth permission is missing")
            return
        }
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            WheelRepository.setLink(LinkState.ERROR, "Bluetooth is unavailable or disabled")
            return
        }
        candidateMap.clear()
        devicesByAddress.clear()
        _candidates.value = emptyList()
        WheelRepository.setLink(LinkState.SCANNING, "Scanning nearby BLE devices…")
        scanner.startScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!canScan()) return
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
        decoder.reset()
        WheelRepository.clearTelemetry()
        WheelRepository.clearBms()
        wheelCharacteristic = null
        gatt?.close()
        WheelRepository.setLink(LinkState.CONNECTING, "Connecting to ${candidateMap[address]?.name ?: address}…")
        gatt = device.connectGatt(context, false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        wheelCharacteristic = null
        decoder.reset()
        WheelRepository.clearTelemetry()
        WheelRepository.clearBms()
        WheelRepository.setLink(LinkState.IDLE, "Disconnected")
    }

    /** Veteran/LeaperKim command channel. These command strings match the public Veteran protocol. */
    fun setLight(on: Boolean): Boolean {
        val ok = sendCommand(if (on) "SetLightON".encodeToByteArray() else "SetLightOFF".encodeToByteArray())
        if (ok) _lightOn.value = on
        return ok
    }

    fun beep(): Boolean {
        val old = buildVeteranCommandOld(0x0E, 9, 1, byte5 = 0x00)
        val newer = buildVeteranCommandNew(0x0E, 9, 1, byte5 = 0x00, byte6 = 0x00)
        return sendStream(old + newer)
    }

    fun setKeyToneVolume(percent: Int): Boolean {
        val value = percent.coerceIn(0, 100)
        return sendStream(buildVeteranCommandNew(0x1C, 23, value, byte5 = 0x01, byte6 = 0x02))
    }

    fun setPedalMode(mode: Int): Boolean = when (mode) {
        0 -> sendCommand("SETh".encodeToByteArray())
        1 -> sendCommand("SETm".encodeToByteArray())
        2 -> sendCommand("SETs".encodeToByteArray())
        else -> false
    }

    fun resetTrip(): Boolean = sendCommand("CLEARMETER".encodeToByteArray())

    private fun buildVeteranCommandOld(cmdByte: Int, valuePosition: Int, value: Int, byte5: Int = 0x01): ByteArray {
        val payload = ByteArray(valuePosition + 1) { 0x80.toByte() }
        payload[0] = 0x4C
        payload[1] = 0x6B
        payload[2] = 0x41
        payload[3] = 0x70
        payload[4] = cmdByte.toByte()
        payload[5] = byte5.toByte()
        payload[valuePosition] = value.toByte()
        return appendVeteranCrc(payload)
    }

    private fun buildVeteranCommandNew(cmdByte: Int, valuePosition: Int, value: Int, byte5: Int = 0x01, byte6: Int = 0x00): ByteArray {
        val payload = ByteArray(valuePosition + 1) { 0x80.toByte() }
        payload[0] = 0x4C
        payload[1] = 0x64
        payload[2] = 0x41
        payload[3] = 0x70
        payload[4] = cmdByte.toByte()
        payload[5] = byte5.toByte()
        payload[6] = byte6.toByte()
        payload[valuePosition] = value.toByte()
        return appendVeteranCrc(payload)
    }

    private fun appendVeteranCrc(payload: ByteArray): ByteArray {
        val crc = CRC32().apply { update(payload) }.value
        return payload + byteArrayOf(
            ((crc shr 24) and 0xFF).toByte(),
            ((crc shr 16) and 0xFF).toByte(),
            ((crc shr 8) and 0xFF).toByte(),
            (crc and 0xFF).toByte(),
        )
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
        chunks.drop(1).forEachIndexed { index, chunk ->
            commandHandler.postDelayed({ sendCommand(chunk) }, 55L * (index + 1))
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun sendCommand(bytes: ByteArray): Boolean {
        if (!canConnect() || WheelRepository.linkState.value != LinkState.CONNECTED) return false
        val currentGatt = gatt ?: return false
        val characteristic = wheelCharacteristic ?: return false
        return if (Build.VERSION.SDK_INT >= 33) {
            currentGatt.writeCharacteristic(
                characteristic,
                bytes,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                characteristic.value = bytes
                currentGatt.writeCharacteristic(characteristic)
            }
        }
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                android.bluetooth.BluetoothProfile.STATE_CONNECTED -> {
                    WheelRepository.setLink(LinkState.DISCOVERING, "Connected. Discovering services…")
                    gatt.discoverServices()
                }
                android.bluetooth.BluetoothProfile.STATE_DISCONNECTED -> {
                    wheelCharacteristic = null
                    decoder.reset()
                    WheelRepository.clearTelemetry()
                    WheelRepository.clearBms()
                    WheelRepository.setLink(LinkState.IDLE, "Disconnected (status $status)")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                WheelRepository.setLink(LinkState.ERROR, "Service discovery failed: $status")
                return
            }
            val service: BluetoothGattService = gatt.getService(SERVICE_UUID) ?: run {
                WheelRepository.setLink(LinkState.ERROR, "FFE0 service not found")
                return
            }
            val characteristic = service.getCharacteristic(CHAR_UUID) ?: run {
                WheelRepository.setLink(LinkState.ERROR, "FFE1 characteristic not found")
                return
            }
            wheelCharacteristic = characteristic
            gatt.setCharacteristicNotification(characteristic, true)
            val cccd = characteristic.getDescriptor(CCCD_UUID) ?: run {
                WheelRepository.setLink(LinkState.ERROR, "CCCD descriptor not found")
                return
            }
            val wrote = if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(cccd)
                }
            }
            if (!wrote) WheelRepository.setLink(LinkState.ERROR, "Could not enable FFE1 notifications")
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid == CCCD_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    WheelRepository.setLink(LinkState.CONNECTED, "Veteran stream armed · waiting for DC 5A 5C")
                } else {
                    WheelRepository.setLink(LinkState.ERROR, "Notification setup failed: $status")
                }
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            onBytes(characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) = onBytes(value)
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
