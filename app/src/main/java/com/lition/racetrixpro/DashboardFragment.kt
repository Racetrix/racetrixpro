package com.lition.racetrixpro

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    // 顶部控件
    private lateinit var tvStatusBt: TextView
    private lateinit var btnSettings: ImageButton
    private lateinit var btnTrackSetup: ImageButton

    // 扫描列表
    private lateinit var lvDevices: ListView
    private lateinit var tvDataLog: TextView

    // 驾驶界面容器
    private lateinit var containerDrivingUi: LinearLayout

    // === 仪表盘元素 ===
    private lateinit var tvSpeed: TextView // 大数字

    // 格子数据
    private lateinit var tvValSat: TextView
    private lateinit var tvValImu: TextView // IMU状态
    private lateinit var tvValSd: TextView  // SD状态
    private lateinit var tvValLap: TextView
    private lateinit var tvValLat: TextView // 纬度
    private lateinit var tvValLon: TextView // 经度
    private lateinit var tvValMode: TextView // Race Mode
    private lateinit var tvValTime: TextView // Lap Time

    // 滑动条与停止按钮
    private lateinit var containerSlide: View
    private lateinit var sbSlideStart: SeekBar
    private lateinit var tvSliderText: TextView
    private lateinit var btnStopRace: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mainActivity = activity as MainActivity

        initViews(view)

        // 1. 绑定列表点击
        lvDevices.adapter = mainActivity.deviceAdapter
        lvDevices.setOnItemClickListener { _, _, position, _ ->
            mainActivity.onDeviceSelected(position)
        }

        // 2. 绑定停止按钮逻辑 (点击停止)
        btnStopRace.setOnClickListener {
            // === 触发停止 ===
            mainActivity.sendCommand("RM:STOP")
            showCommandFeedback("STOPPING...", false)
            performHapticFeedback()

            // 立刻切换 UI 到滑动状态 (待机)
            switchUiToStandbyState()
        }

        // 3. 顶部按钮事件
        view.findViewById<View>(R.id.btn_back).setOnClickListener { mainActivity.goToSelection() }
        btnSettings.setOnClickListener { mainActivity.goToSettings() }
        btnTrackSetup.setOnClickListener { mainActivity.goToTrackSetup() }

        // 4. 设置滑动条逻辑 (整合后的版本)
        setupSliderLogic(mainActivity)

        // 5. 恢复状态
        updateSystemInfo(mainActivity.lastSdStatus)
        updateConnectionStatus(mainActivity.isDeviceConnected)
    }

    private fun initViews(view: View) {
        tvStatusBt = view.findViewById(R.id.tv_status_bt)
        btnSettings = view.findViewById(R.id.btn_settings)
        btnTrackSetup = view.findViewById(R.id.btn_track_setup_entry)

        lvDevices = view.findViewById(R.id.lv_devices)
        tvDataLog = view.findViewById(R.id.tv_data_log)
        containerDrivingUi = view.findViewById(R.id.container_driving_ui)

        tvSpeed = view.findViewById(R.id.tv_speed_value)

        tvValSat = view.findViewById(R.id.tv_val_sat)
        tvValImu = view.findViewById(R.id.tv_val_imu)
        tvValSd = view.findViewById(R.id.tv_val_sd)
        tvValLap = view.findViewById(R.id.tv_val_lap)
        tvValLat = view.findViewById(R.id.tv_val_lat)
        tvValLon = view.findViewById(R.id.tv_val_lon)
        tvValMode = view.findViewById(R.id.tv_val_long_g)
        tvValTime = view.findViewById(R.id.tv_val_lat_g)

        containerSlide = view.findViewById(R.id.container_slide_mechanism)
        sbSlideStart = view.findViewById(R.id.sb_slide_start)
        tvSliderText = view.findViewById(R.id.tv_slider_text)
        btnStopRace = view.findViewById(R.id.btn_stop_race)
    }

    // --- 辅助方法：切换到 [运行中] 状态 ---
    // 隐藏滑块，显示红色停止按钮
    private fun switchUiToRunningState() {
        activity?.runOnUiThread {
            containerSlide.visibility = View.GONE
            btnStopRace.visibility = View.VISIBLE
        }
    }

    // --- 辅助方法：切换到 [待机] 状态 ---
    // 隐藏停止按钮，显示滑块
    private fun switchUiToStandbyState() {
        activity?.runOnUiThread {
            btnStopRace.visibility = View.GONE
            containerSlide.visibility = View.VISIBLE
            resetSlider()
        }
    }

    private fun setupSliderLogic(mainActivity: MainActivity) {
        sbSlideStart.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) tvSliderText.alpha = 1.0f - (progress / 100f)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if ((seekBar?.progress ?: 0) > 85) {
                    // === 触发开始 ===
                    performHapticFeedback()

                    // 发送开始指令 (漫游/录制)
                    mainActivity.sendCommand("RM:START")
                    showCommandFeedback("STARTING...", false)

                    // 【关键】立刻切换 UI 到停止状态
                    switchUiToRunningState()

                    // 重置滑块位置 (为下次显示做准备)
                    resetSlider()
                } else {
                    // 滑动距离不够，回弹
                    resetSlider()
                }
            }
        })
    }

    private fun resetSlider() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sbSlideStart.setProgress(0, true)
        } else {
            sbSlideStart.progress = 0
        }
        tvSliderText.animate().alpha(1.0f).duration = 300
    }

    fun updateConnectionStatus(isConnected: Boolean) {
        val tvStatus = view?.findViewById<TextView>(R.id.tv_status_bt)
        val sliderContainer = view?.findViewById<View>(R.id.layout_slider_container)

        if (isConnected) {
            btnSettings.visibility = View.VISIBLE
            btnTrackSetup.visibility = View.VISIBLE
            tvStatus?.visibility = View.GONE
            lvDevices.visibility = View.GONE
            containerDrivingUi.visibility = View.VISIBLE

            // 连接成功后显示底部控制区
            sliderContainer?.visibility = View.VISIBLE

        } else {
            btnSettings.visibility = View.GONE
            btnTrackSetup.visibility = View.GONE
            lvDevices.visibility = View.VISIBLE
            containerDrivingUi.visibility = View.GONE

            // 断开连接时隐藏底部控制区
            sliderContainer?.visibility = View.GONE
        }
    }

    fun updateDashboard(speed: Double, sats: String, statusStr: String, modeStr: String, timeStr: String, lat: Double, lon: Double) {
        activity?.runOnUiThread {
            // 1. 速度
            tvSpeed.text = String.format("%.1f", speed)
            val color = when {
                speed < 50.0 -> Color.WHITE
                speed < 100.0 -> Color.parseColor("#FFD600")
                else -> Color.parseColor("#FF1744")
            }
            tvSpeed.setTextColor(color)

            // 2. 状态文字
            tvValSat.text = sats
            tvValLap.text = statusStr
            tvValLap.setTextColor(if(statusStr == "RECORDING") Color.parseColor("#00E676") else Color.parseColor("#FFD600"))

            tvValMode.text = modeStr
            tvValTime.text = timeStr
            tvValLat.text = String.format("%.6f", lat)
            tvValLon.text = String.format("%.6f", lon)

            tvValImu.text = "ACTIVE"
            tvValImu.setTextColor(Color.parseColor("#00E676"))

            // 3. 状态同步 (防止 UI 和实际状态不一致)
            // 如果设备已经在录制，确保显示的是停止按钮
            if (statusStr == "RECORDING") {
                if (btnStopRace.visibility != View.VISIBLE) {
                    switchUiToRunningState()
                }
            } else {
                if (containerSlide.visibility != View.VISIBLE) {
                    switchUiToStandbyState()
                }
            }
        }
    }

    fun updateSystemInfo(sdStatus: Int) {
        activity?.runOnUiThread {
            if (sdStatus == 1) {
                tvValSd.text = "READY"
                tvValSd.setTextColor(Color.parseColor("#00E676"))
            } else if (sdStatus == 0) {
                tvValSd.text = "NO CARD"
                tvValSd.setTextColor(Color.parseColor("#FF1744"))
            } else {
                tvValSd.text = "--"
                tvValSd.setTextColor(Color.GRAY)
            }
        }
    }

    private fun performHapticFeedback() {
        try {
            val vibrator = context?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            }
        } catch (e: Exception) {}
    }

    fun appendLog(msg: String) {
        activity?.runOnUiThread { tvDataLog.text = "> $msg" }
    }

    fun showCommandFeedback(msg: String, isError: Boolean) {
        activity?.runOnUiThread {
            val prefix = if (isError) "[ERR]" else "[OK]"
            tvDataLog.text = "> $prefix $msg"
        }
    }
}