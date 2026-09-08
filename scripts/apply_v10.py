from pathlib import Path
import runpy


# v0.0.10 builds on all v0.0.9 changes.
runpy.run_path("scripts/apply_v9.py", run_name="__main__")


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one match, got {count}: {old[:140]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_all(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"{path}: missing {old!r}")
    p.write_text(text.replace(old, new), encoding="utf-8")


ble = "app/src/main/java/com/euclab/app/ble/BleWheelManager.kt"

replace_once(
    ble,
    '''    private var wheelCharacteristic: BluetoothGattCharacteristic? = null
    private val commandHandler = Handler(Looper.getMainLooper())''',
    '''    private var wheelCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var currentTransportLabel = "Unknown BLE"
    private var detectedProtocolLabel = "Unknown protocol"
    private val commandHandler = Handler(Looper.getMainLooper())''',
)

replace_once(
    ble,
    '''        val likely = uuids.contains(SERVICE_UUID) ||
            name.contains("Veteran", ignoreCase = true) ||
            name.contains("Sherman", ignoreCase = true) ||
            name.contains("Leaper", ignoreCase = true) ||
            name.startsWith("LK", ignoreCase = true)

        devicesByAddress[device.address] = device
        candidateMap[device.address] = BleCandidate(name, device.address, result.rssi, likely)
        _candidates.value = candidateMap.values
            .sortedWith(compareByDescending<BleCandidate> { it.likelyEuc }.thenByDescending { it.rssi })
            .take(20)''',
    '''        val likely = uuids.contains(SERVICE_UUID) ||
            name.contains("Veteran", ignoreCase = true) ||
            name.contains("Sherman", ignoreCase = true) ||
            name.contains("Leaper", ignoreCase = true) ||
            name.startsWith("LK", ignoreCase = true) ||
            name.contains("Begode", ignoreCase = true) ||
            name.contains("Gotway", ignoreCase = true) ||
            name.contains("Extreme Bull", ignoreCase = true) ||
            name.contains("KingSong", ignoreCase = true) ||
            name.startsWith("KS-", ignoreCase = true) ||
            name.contains("Inmotion", ignoreCase = true) ||
            name.contains("NOSFET", ignoreCase = true)

        devicesByAddress[device.address] = device
        // LinkedHashMap keeps the first-seen position stable. RSSI/flags may update,
        // but a device no longer jumps around or disappears behind a take(20) cap.
        candidateMap[device.address] = BleCandidate(name, device.address, result.rssi, likely)
        _candidates.value = candidateMap.values.toList()''',
)

replace_all(
    ble,
    '''        wheelCharacteristic = null''',
    '''        wheelCharacteristic = null
        notifyCharacteristic = null
        currentTransportLabel = "Unknown BLE"
        detectedProtocolLabel = "Unknown protocol"''',
)

replace_once(
    ble,
    '''        override fun onServicesDiscovered(callbackGatt: BluetoothGatt, status: Int) {
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
        }''',
    '''        override fun onServicesDiscovered(callbackGatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                WheelRepository.setLink(LinkState.ERROR, "Service discovery failed: $status")
                return
            }

            val services = callbackGatt.services.orEmpty()
            val allCharacteristics = services.flatMap { it.characteristics.orEmpty() }
            val knownFfe1 = callbackGatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)

            val gattSummary = services.joinToString(" | ") { service ->
                val shortService = service.uuid.toString().substringBefore("-0000-1000").takeLast(8)
                val chars = service.characteristics.orEmpty().joinToString(",") { ch ->
                    ch.uuid.toString().substringBefore("-0000-1000").takeLast(8)
                }
                "$shortService:[$chars]"
            }
            WheelRepository.setRawPacket("GATT ${gattSummary.take(235)}")

            val notifyMask = BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE
            val writeMask = BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE

            val notify = knownFfe1 ?: allCharacteristics.firstOrNull { (it.properties and notifyMask) != 0 }
            if (notify == null) {
                WheelRepository.setLink(LinkState.ERROR, "Connected, but no notify/indicate characteristic found · GATT captured")
                return
            }

            val write = if (knownFfe1 != null) knownFfe1
            else allCharacteristics.firstOrNull { (it.properties and writeMask) != 0 }

            notifyCharacteristic = notify
            wheelCharacteristic = write
            currentTransportLabel = if (knownFfe1 != null) "FFE0/FFE1" else "Generic BLE"
            detectedProtocolLabel = "Unknown protocol"

            if (!callbackGatt.setCharacteristicNotification(notify, true)) {
                WheelRepository.setLink(LinkState.ERROR, "Could not arm BLE notifications · GATT captured")
                return
            }

            val cccd = notify.getDescriptor(CCCD_UUID) ?: run {
                WheelRepository.setLink(LinkState.ERROR, "Notify characteristic has no CCCD · GATT captured")
                return
            }
            val indicationOnly = (notify.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 &&
                (notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0
            val enableValue = if (indicationOnly) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

            val wrote = if (Build.VERSION.SDK_INT >= 33) {
                callbackGatt.writeDescriptor(cccd, enableValue) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION") run { cccd.value = enableValue; callbackGatt.writeDescriptor(cccd) }
            }
            if (!wrote) WheelRepository.setLink(LinkState.ERROR, "Could not enable BLE notifications · GATT captured")
        }''',
)

replace_once(
    ble,
    '''        override fun onDescriptorWrite(callbackGatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid == CCCD_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    rememberConnectedWheel()
                    WheelRepository.setLink(LinkState.CONNECTED, "Veteran stream armed · waiting for DC 5A 5C")
                } else WheelRepository.setLink(LinkState.ERROR, "Notification setup failed: $status")
            }
        }''',
    '''        override fun onDescriptorWrite(callbackGatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid == CCCD_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    rememberConnectedWheel()
                    val writeNote = if (wheelCharacteristic == null) " · read-only" else ""
                    WheelRepository.setLink(
                        LinkState.CONNECTED,
                        "$currentTransportLabel connected$writeNote · $detectedProtocolLabel · RAW diagnostics active"
                    )
                } else WheelRepository.setLink(LinkState.ERROR, "Notification setup failed: $status")
            }
        }''',
)

replace_once(
    ble,
    '''    private fun onBytes(bytes: ByteArray) {
        WheelRepository.setRawPacket(bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }.take(240))
        val decoded = decoder.feed(bytes)
        decoded.forEach(WheelRepository::publishTelemetry)
        if (decoded.isNotEmpty()) decoder.latestBmsSnapshot()?.let(WheelRepository::publishBms)
    }''',
    '''    private fun onBytes(bytes: ByteArray) {
        WheelRepository.setRawPacket(bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }.take(240))

        // v0.0.10 keeps Veteran decoding as the first known decoder, but unknown BLE
        // streams stay connected and visible instead of being rejected. A protocol
        // router will add Begode/KingSong/Inmotion decoders on top of this foundation.
        val decoded = decoder.feed(bytes)
        decoded.forEach(WheelRepository::publishTelemetry)
        if (decoded.isNotEmpty()) {
            decoder.latestBmsSnapshot()?.let(WheelRepository::publishBms)
            if (detectedProtocolLabel != "Veteran / LeaperKim") {
                detectedProtocolLabel = "Veteran / LeaperKim"
                WheelRepository.setLink(LinkState.CONNECTED, "$currentTransportLabel connected · $detectedProtocolLabel detected")
            }
        }
    }''',
)

replace_once(
    ble,
    '''    private fun sendCommand(bytes: ByteArray): Boolean {
        if (!canConnect() || WheelRepository.linkState.value != LinkState.CONNECTED) return false
        val currentGatt = gatt ?: return false''',
    '''    private fun sendCommand(bytes: ByteArray): Boolean {
        if (!canConnect() || WheelRepository.linkState.value != LinkState.CONNECTED) return false
        // Never send Veteran/LeaperKim control packets to an unknown wheel merely
        // because it exposed a writable BLE characteristic. Commands unlock only
        // after a valid Veteran frame has positively identified the protocol.
        if (detectedProtocolLabel != "Veteran / LeaperKim") return false
        val currentGatt = gatt ?: return false''',
)

ui = "app/src/main/java/com/euclab/app/AppUiV5.kt"
replace_all(ui, "v0.0.9", "v0.0.10")

gradle = "app/build.gradle.kts"
replace_once(gradle, "        versionCode = 9", "        versionCode = 10")
replace_once(gradle, '        versionName = "0.0.9"', '        versionName = "0.0.10"')
replace_all(gradle, "// v0.0.9:", "// v0.0.10:")

print("EUC Lab v0.0.10 universal BLE foundation patch applied")
