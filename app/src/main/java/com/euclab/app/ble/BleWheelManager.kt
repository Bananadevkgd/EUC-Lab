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
import androidx.core.app.ActivityCompat
import com.euclab.app.data.BleCandidate
import com.euclab.app.data.LinkState
import com.euclab.app.data.WheelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class BleWheelManager(private val context: Context) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetoothManager?.adapter
    private val decoder = VeteranFrameDecoder()
    private var gatt: BluetoothGatt? = null

    private val devicesByAddress = linkedMapOf<String, android.bluetooth.BluetoothDevice>()
    private val candidateMap = linkedMapOf<String, BleCandidate>()
    private val _candidates = MutableStateFlow<List<BleCandidate>>(emptyList())
    val candidates: StateFlow<List<BleCandidate>> = _candidates.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::handleScanResult)
        }

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

    fun canConnect(): Boolean {
        return Build.VERSION.SDK_INT < 31 ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

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
        gatt?.close()
        WheelRepository.setLink(LinkState.CONNECTING, "Connecting to ${candidateMap[address]?.name ?: address}…")
        gatt = device.connectGatt(context, false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        decoder.reset()
        WheelRepository.setLink(LinkState.IDLE, "Disconnected")
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
                    decoder.reset()
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
            if (!wrote) {
                WheelRepository.setLink(LinkState.ERROR, "Could not enable FFE1 notifications")
            }
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
        ) {
            onBytes(value)
        }
    }

    private fun onBytes(bytes: ByteArray) {
        WheelRepository.setRawPacket(bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }.take(240))
        decoder.feed(bytes).forEach(WheelRepository::publishTelemetry)
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val CHAR_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
