package com.lition.racetrixpro

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.ArrayList

class MainActivity : AppCompatActivity(), BluetoothCallback {

    // --- 管理器 ---
    lateinit var bleManager: BleManager
    lateinit var classicManager: ClassicBtManager
    private lateinit var prefs: SharedPreferences
    var lastSdStatus: Int = -1
    var lastVoltage: Float = 0f
    var isDeviceConnected = false
    var lastLat = 0.0;
    var lastLon = 0.0;


    // --- 数据 ---
    val scannedDevices = ArrayList<BluetoothDevice>()
    lateinit var deviceAdapter: DeviceAdapter // 公开给 Fragment 使用
    var isBleMode = true
    var isScanning = false
    var lastConnectedAddress: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = getSharedPreferences("RacetrixPrefs", Context.MODE_PRIVATE)
        lastConnectedAddress = prefs.getString("LAST_DEVICE_ADDR", null)

        bleManager = BleManager(this, this)
        classicManager = ClassicBtManager(this, this)

        // 初始化适配器
        deviceAdapter = DeviceAdapter(this, scannedDevices, lastConnectedAddress)

        checkAndRequestPermissions()

        // 默认显示选择页
        if (savedInstanceState == null) {
            goToSelection()
        }

        // 演示模式入口 (点击Logo)
    }

    // --- 页面导航 ---

    fun onModeSelected(isPro: Boolean) {
        isBleMode = isPro
        val title = if (isPro) "RACETRIX PRO" else "STANDARD EDITION"

        val fragment = DashboardFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null) // 允许返回
            .commit()

        // 等 Fragment 加载完更新标题和开始扫描
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            startScan()
        }, 100)
    }

    fun goToSettings() {
        val dashboardFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        val settingsFragment = SettingsFragment()

        val transaction = supportFragmentManager.beginTransaction()

        // 如果当前有仪表盘，先隐藏它（但不销毁）
        if (dashboardFragment != null) {
            transaction.hide(dashboardFragment)
        }

        transaction
            .add(R.id.fragment_container, settingsFragment) // 叠加在上层
            .addToBackStack(null) // 加入回退栈，按返回键可以回来
            .commit()
    }

    // --- 业务逻辑 ---

    fun onDeviceSelected(position: Int) {
        if (position < scannedDevices.size) {
            stopScan()
            val device = scannedDevices[position]
            saveConnectedDevice(device.address)

            // 获取 DashboardFragment 更新日志
            (getCurrentFragment() as? DashboardFragment)?.appendLog("Connecting to ${device.address}...")

            if (isBleMode) bleManager.connect(device) else classicManager.connect(device)
        }
    }

    fun toggleScan() {
        if (isScanning) stopScan() else startScan()
    }

    fun sendCommand(cmd: String) {
        if (isBleMode) bleManager.sendData(cmd) else classicManager.sendData(cmd)
    }

    private fun startScan() {
        scannedDevices.clear()
        deviceAdapter.notifyDataSetChanged()
        isScanning = true

        if (isBleMode) {
//            appendLog("Scanning for Pro (BLE)...")
            bleManager.startScan()
        } else {
//            appendLog("Scanning for Standard (Classic)...")
            classicManager.startScan()
        }
    }

    private fun stopScan() {
        isScanning = false
        if (isBleMode) bleManager.stopScan() else classicManager.stopScan()
    }

    // --- 页面跳转逻辑 ---

    fun goToDashboard() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, DashboardFragment())
            .addToBackStack(null)
            .commit()

        // 进仪表盘 0.1秒后自动开始扫描
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            startScan()
        }, 100)
    }

    // --- 跳转到赛道初始化页面 ---
    fun goToTrackSetup() {
        supportFragmentManager.beginTransaction()
            // 替换当前的 Fragment 为 TrackSetupFragment
            .replace(R.id.fragment_container, TrackSetupFragment())
            // 加入回退栈，这样按手机返回键或页面左上角的返回按钮时，能回到仪表盘
            .addToBackStack(null)
            .commit()
    }

    // 1. 找到你的 goToSelection 方法，修改如下：
    fun goToSelection() {
        stopScan()
        if (isBleMode) bleManager.disconnect() else classicManager.disconnect()

        // 【新增】 断开时，标记为 false
        isDeviceConnected = false

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SelectionFragment())
            .commit()
    }

    private fun disconnect() {
        if (isBleMode) bleManager.disconnect() else classicManager.close()
    }

    private fun saveConnectedDevice(address: String) {
        lastConnectedAddress = address
        prefs.edit().putString("LAST_DEVICE_ADDR", address).apply()
        deviceAdapter.lastAddress = address
        deviceAdapter.notifyDataSetChanged()
    }

    // --- BluetoothCallback ---

    override fun onDeviceFound(device: BluetoothDevice) {
        runOnUiThread {
            if (!scannedDevices.any { it.address == device.address }) {
                if (lastConnectedAddress != null && device.address.equals(lastConnectedAddress, ignoreCase = true)) {
                    scannedDevices.add(0, device)
                } else {
                    scannedDevices.add(device)
                }
                deviceAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onConnectionStateChanged(isConnected: Boolean) {
        runOnUiThread {
            // 【新增】 记住最新的状态！
            this.isDeviceConnected = isConnected

            // 原有的更新 UI 代码
            val dashboard = supportFragmentManager.findFragmentById(R.id.fragment_container) as? DashboardFragment
            dashboard?.updateConnectionStatus(isConnected)
        }
    }
    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02X ", b))
        }
        return sb.toString()
    }

    // --- 强力调试版解析器 ---
    private fun parseDashboardData(rawData: String) {
        val data = rawData.trim()
        // 获取当前显示的 Fragment (可能是 Dashboard 也可能是 Settings)
        // 注意：因为我们现在用了 add/hide，Dashboard 可能还在，所以我们要更灵活地获取
        val dashboardFrag = supportFragmentManager.fragments.firstOrNull { it is DashboardFragment && it.isVisible } as? DashboardFragment
        val settingsFrag = supportFragmentManager.fragments.firstOrNull { it is SettingsFragment && it.isVisible } as? SettingsFragment

        // ==========================================
        // 1. 遥测数据 (TLM) -> 给 Dashboard
        // ==========================================
        if (data.startsWith("TLM:")) {
            val parts = data.split(",")
            try {
                // 1. 解析基础数据
                val speedStr = parts[0].substringAfter("TLM:")
                val speed = speedStr.trim().toDoubleOrNull() ?: 0.0
                val sats = parts[1].trim()
                val status = parts[2].trim() // 1=Run, 0=Stop
                val mode = parts[3].trim()   // -1=Roam, 0=Circuit, 1=Sprint
                val timeMs = parts[4].trim().toLongOrNull() ?: 0L

                // 2. 【新增】解析经纬度 (Index 5 & 6)
                val lat = parts[5].trim().toDoubleOrNull() ?: 0.0
                val lon = parts[6].trim().toDoubleOrNull() ?: 0.0

                // 3. 【新增】存入全局缓存 (给 TrackSetupFragment 使用)
                // 只有当坐标有效(不为0)时才更新
                if (lat != 0.0 && lon != 0.0) {
                    this.lastLat = lat
                    this.lastLon = lon
                }

                // 4. 数据转换 (用于 UI 显示)
                val statusStr = if (status == "1") "RECORDING" else "STAND BY"

                // 【修改点 2】模式显示
                // 只要 ESP32 发送 0 或 1，无论是否在跑圈，这里都会显示模式名称
                // 如果显示的是 ROAMING，说明 ESP32 发送的是 -1
                val modeStr: String
                modeStr = when(mode) {
                    "0" -> "CIRCUIT" // 圈赛
                    "1" -> "SPRINT"  // 拉力/直线
                    "-1" -> "ROAMING" // 漫游
                    else -> "UNKNOW"
                }


                // 格式化时间 MM:SS.ms
                val min = (timeMs / 1000) / 60
                val sec = (timeMs / 1000) % 60
                val ms = (timeMs % 1000) / 10
                val timeStr = String.format("%02d:%02d.%02d", min, sec, ms)

                // 5. 更新仪表盘 UI
                val dashboard = supportFragmentManager.findFragmentById(R.id.fragment_container) as? DashboardFragment
                if (dashboard != null && dashboard.isVisible) {
                    dashboard.updateDashboard(speed, sats, statusStr, modeStr, timeStr, lat, lon)

                    // 【可选】在底部日志打印坐标，方便调试
                    // 正式版觉得刷屏可以注释掉这行
                    dashboard.appendLog("GPS: $lat, $lon")
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // ==========================================
        // 2. 配置同步流 (VOL, SWAP...) -> 保存并给 Settings
        // ==========================================
        else if (data.startsWith("VOL:") || data.startsWith("SWAP:") ||
            data.startsWith("INV_") || data.startsWith("GPS10:")) {

            val editor = prefs.edit()

            if (data.startsWith("VOL:")) {
                val vol = data.substringAfter("VOL:").toIntOrNull() ?: 0
                editor.putInt("CFG_VOL", vol)
                settingsFrag?.updateVolume(vol) // 如果设置页开着，实时刷新它
            }
            else if (data.startsWith("SWAP:")) {
                val v = (data.substringAfter("SWAP:").toIntOrNull() ?: 0) == 1
                editor.putBoolean("CFG_SWAP", v)
                settingsFrag?.updateSwitch(R.id.sw_swap_xy, v)
            }
            else if (data.startsWith("INV_X:")) {
                val v = (data.substringAfter("INV_X:").toIntOrNull() ?: 0) == 1
                editor.putBoolean("CFG_INV_X", v)
                settingsFrag?.updateSwitch(R.id.sw_inv_x, v)
            }
            else if (data.startsWith("INV_Y:")) {
                val v = (data.substringAfter("INV_Y:").toIntOrNull() ?: 0) == 1
                editor.putBoolean("CFG_INV_Y", v)
                settingsFrag?.updateSwitch(R.id.sw_inv_y, v)
            }
            else if (data.startsWith("GPS10:")) {
                val v = (data.substringAfter("GPS10:").toIntOrNull() ?: 0) == 1
                editor.putBoolean("CFG_GPS10", v)
                settingsFrag?.updateSwitch(R.id.sw_gps_10hz, v)
            }

            editor.apply() // 立即保存到手机存储
        }

        // ==========================================
        // 3. 系统报告 (SYS) -> 给 Dashboard
        // ==========================================
        else if (data.startsWith("SYS:")) {
            var sdStatus = -1; var voltage = -1f
            // 1. 解析 SD (去除空格，防止解析失败)
            if (data.contains("SD=")) {
                val afterSd = data.substringAfter("SD=")
                val sdStr = if (afterSd.contains(",")) afterSd.substringBefore(",") else afterSd
                sdStatus = sdStr.trim().toIntOrNull() ?: -1
            }

            // 2. 解析 BAT
            if (data.contains("BAT=")) {
                val afterBat = data.substringAfter("BAT=")
                val batStr = if (afterBat.contains(",")) afterBat.substringBefore(",") else afterBat
                voltage = batStr.trim().toFloatOrNull() ?: -1f
            }

            // 【重点】只调用 fragment 的方法，不要在这里操作 View！
            // 如果你之前在这里写了 if (ivStatusSd...) 请务必删除！
//            dashboardFrag?.updateSystemInfo(sdStatus, voltage)
            if (sdStatus != -1) this.lastSdStatus = sdStatus
            if (voltage > 0) this.lastVoltage = voltage
            dashboardFrag?.updateSystemInfo(sdStatus)
        }

        // ==========================================
        // 4. 指令反馈
        // ==========================================
        else if (data.startsWith("OK:")) {
            val msg = data.substringAfter("OK:")
            dashboardFrag?.showCommandFeedback(msg, false)
            // 如果设置保存成功，也可以给个 Toast
            if (msg.contains("SAVED")) Toast.makeText(this, "Settings Saved to ESP32 Flash", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDataReceived(data: String) {
        val rawBytes = data.toByteArray(Charsets.ISO_8859_1) // 尝试还原原始字节
        val hexString = bytesToHex(rawBytes)
        android.util.Log.e("BLE_DEBUG", "收到长度: ${rawBytes.size}")
        android.util.Log.e("BLE_DEBUG", "原始 Hex: $hexString")
        android.util.Log.e("BLE_DEBUG", "解析文本: $data")

        // 在屏幕日志显示
        (getCurrentFragment() as? DashboardFragment)?.appendLog("data: $data")
        runOnUiThread {
            // 解析数据并分发给 DashboardFragment
            parseDashboardData(data)
            // 这里也可以增加分发给 SettingsFragment 的逻辑 (例如处理 SYNC 回传)
        }
    }

    override fun onDataSent(data: String) {}
    override fun onMessage(message: String) {
        runOnUiThread {
            (getCurrentFragment() as? DashboardFragment)?.appendLog(message)
        }
    }

    // --- 辅助 ---
    private fun getCurrentFragment() = supportFragmentManager.findFragmentById(R.id.fragment_container)

//    private fun checkAndRequestPermissions() {
//        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
//        } else {
//            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
//        }
//        ActivityCompat.requestPermissions(this, permissions, 1001)
//    }
// 在 MainActivity 类中添加这个检查函数
    private fun checkAndRequestPermissions() {
    // 根据安卓版本，决定要申请哪些权限
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12 (S) 及以上：申请 新蓝牙权限 + 定位
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        // Android 11 及以下：申请 定位权限 (旧蓝牙权限不需要动态申请)
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    // 检查是否有缺失的权限
    val missingPermissions = requiredPermissions.filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }

    if (missingPermissions.isNotEmpty()) {
        // 如果有缺失，一次性申请
        ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 1001)
    } else {
        // 权限都齐了，可以初始化蓝牙扫描
        // initBluetooth()
    }
}
    // 列表适配器 (内部类)
    inner class DeviceAdapter(
        context: Context,
        private val devices: ArrayList<BluetoothDevice>,
        var lastAddress: String?
    ) : ArrayAdapter<BluetoothDevice>(context, 0, devices) {

        @SuppressLint("MissingPermission")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_device_card, parent, false)

            if (position >= devices.size) return view
            val device = getItem(position) ?: return view

            val root = view.findViewById<ConstraintLayout>(R.id.card_root)
            val tvName = view.findViewById<TextView>(R.id.tv_device_name)
            val tvAddr = view.findViewById<TextView>(R.id.tv_device_address)
            val tvBadge = view.findViewById<TextView>(R.id.tv_last_badge)
            val ivIcon = view.findViewById<ImageView>(R.id.iv_icon)

            val nameStr = if (device.name.isNullOrEmpty()) "UNKNOWN TARGET" else device.name
            val addrStr = device.address

            tvName.text = nameStr
            tvAddr.text = addrStr

            if (lastAddress != null && addrStr.equals(lastAddress, ignoreCase = true)) {
                root.setBackgroundResource(R.drawable.bg_device_card_active)
                tvBadge.visibility = View.VISIBLE
                tvName.setTextColor(Color.parseColor("#00E676"))
                ivIcon.setColorFilter(Color.parseColor("#00E676"))
            } else {
                root.setBackgroundResource(R.drawable.bg_device_card)
                tvBadge.visibility = View.GONE
                tvName.setTextColor(Color.WHITE)
                ivIcon.setColorFilter(Color.parseColor("#666666"))
            }

            return view
        }
    }
}