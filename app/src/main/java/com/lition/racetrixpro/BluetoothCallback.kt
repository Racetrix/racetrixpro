package com.lition.racetrixpro

import android.bluetooth.BluetoothDevice

interface BluetoothCallback {
    // 扫描到一个设备时触发
    fun onDeviceFound(device: BluetoothDevice)

    // 连接状态改变 (true: 已连接, false: 断开)
    fun onConnectionStateChanged(isConnected: Boolean)

    // 收到数据 (RX)
    fun onDataReceived(data: String)

    // 发送数据成功 (TX)
    fun onDataSent(data: String)

    // 发生错误或普通日志
    fun onMessage(message: String)
}