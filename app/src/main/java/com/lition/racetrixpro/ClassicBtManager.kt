package com.lition.racetrixpro

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class ClassicBtManager(private val context: Context, private val callback: BluetoothCallback) {

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val bluetoothAdapter: BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var readThread: Thread? = null
    private var isConnected = false

    // 广播接收器
    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                device?.let { callback.onDeviceFound(it) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        // 注册广播
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(scanReceiver, filter)
        bluetoothAdapter?.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            context.unregisterReceiver(scanReceiver)
        } catch (e: Exception) {
            // 忽略未注册异常
        }
        bluetoothAdapter?.cancelDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        callback.onMessage("Connecting to ${device.name} (Classic)...")
        bluetoothAdapter?.cancelDiscovery() // 连接前必须停止扫描

        Thread {
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket?.connect()

                outputStream = socket?.outputStream
                isConnected = true

                // 连接成功，切换回主线程通知
                callback.onConnectionStateChanged(true)

                // 启动读取线程
                startReadThread(socket!!.inputStream)

            } catch (e: IOException) {
                callback.onMessage("Connection Failed: ${e.message}")
                close()
            }
        }.start()
    }

    private fun startReadThread(inputStream: InputStream) {
        readThread = Thread {
            val buffer = ByteArray(1024)
            while (isConnected) {
                try {
                    val bytes = inputStream.read(buffer)
                    if (bytes > 0) {
                        val data = String(buffer, 0, bytes)
                        callback.onDataReceived(data)
                    }
                } catch (e: IOException) {
                    close()
                    break
                }
            }
        }
        readThread?.start()
    }

    fun sendData(data: String) {
        if (!isConnected || outputStream == null) return
        try {
            outputStream?.write(data.toByteArray())
            callback.onDataSent(data)
        } catch (e: IOException) {
            callback.onMessage("Send Failed: ${e.message}")
        }
    }

    fun close() {
        isConnected = false
        try {
            socket?.close()
            readThread?.interrupt()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        callback.onConnectionStateChanged(false)
    }
    fun disconnect() {
        try {
            // 关闭 socket 就是断开经典蓝牙连接的标准做法
            socket?.close()
            socket = null

            // 可以在这里通知回调，状态已断开
            // callback.onConnectionStateChanged(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}