package com.lition.racetrixpro

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import java.util.*
import kotlin.concurrent.thread

class BleManager(private val context: Context, private val callback: BluetoothCallback) {

    // --- 【修改点1】协议指定的 UUID (Nordic UART Service) ---
    private val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val RX_CHAR_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // 手机写给设备
    private val TX_CHAR_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // 设备通知手机

    private val bluetoothAdapter: BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var bleScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var currentPayloadSize = 20

    // ... ScanCallback 保持不变 ...
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            callback.onDeviceFound(result.device)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                callback.onMessage("> CONNECTED. REQUESTING PRIORITY...")
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                if (!gatt.requestMtu(247)) { // ESP32 通常支持到 247 或 512
                    gatt.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                callback.onConnectionStateChanged(false)
                close()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                currentPayloadSize = mtu - 3
                callback.onMessage("> MTU: $mtu")
            }
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                // 注意：我们要监听的是 TX (设备发给手机)
                val txChar = service?.getCharacteristic(TX_CHAR_UUID)
                if (txChar != null) {
                    gatt.setCharacteristicNotification(txChar, true)
                    val descriptor = txChar.descriptors.firstOrNull()
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                    callback.onConnectionStateChanged(true)

                    // 【协议推荐】连接建立后，发送同步指令
                    // 这里稍微延时一下确保通道建立
                    Thread.sleep(100)
                    sendData("CMD:SYNC") // 获取初始配置
                    Thread.sleep(100);
                    sendData("CMD:REPORT") // 获取硬件状态
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val bytes = characteristic.value
            // 强制指定编码，不要用默认的
            val data = String(bytes, Charsets.UTF_8) // 或者 Charsets.US_ASCII
            callback.onDataReceived(data)
        }
    }

    // ... startScan, stopScan, connect, disconnect, close 保持不变 ...
    @SuppressLint("MissingPermission")
    fun startScan() { bleScanner = bluetoothAdapter?.bluetoothLeScanner; bleScanner?.startScan(scanCallback) }
    @SuppressLint("MissingPermission")
    fun stopScan() { bleScanner?.stopScan(scanCallback) }
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) { bluetoothGatt = device.connectGatt(context, false, gattCallback) }
    @SuppressLint("MissingPermission")
    fun disconnect() { bluetoothGatt?.disconnect() }
    @SuppressLint("MissingPermission")
    fun close() { bluetoothGatt?.close(); bluetoothGatt = null }

    @SuppressLint("MissingPermission")
    fun sendData(rawData: String) {
        val gatt = bluetoothGatt ?: return
        val service = gatt.getService(SERVICE_UUID) ?: return
        // 注意：写入的是 RX (手机发给设备)
        val rxChar = service.getCharacteristic(RX_CHAR_UUID) ?: return

        // --- 【修改点2】强制添加换行符 \n (协议要求) ---
        val dataToSend = if (rawData.endsWith("\n")) rawData else "$rawData\n"

        Thread {
            val bytes = dataToSend.toByteArray()
            var offset = 0
            while (offset < bytes.size) {
                val length = Math.min(bytes.size - offset, currentPayloadSize)
                val chunk = ByteArray(length)
                System.arraycopy(bytes, offset, chunk, 0, length)

                rxChar.value = chunk
                rxChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                gatt.writeCharacteristic(rxChar)

                offset += length
                // --- 【修改点3】流控 (协议建议 20-50ms) ---
                try { Thread.sleep(30) } catch (e: Exception) {}
            }
            callback.onDataSent(rawData) // 回调时不带 \n 方便看日志
        }.start()
    }
}