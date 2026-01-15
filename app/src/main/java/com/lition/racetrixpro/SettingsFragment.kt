package com.lition.racetrixpro

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    // --- 控件变量 ---
    private lateinit var sbVolume: SeekBar
    private lateinit var tvVolVal: TextView

    private lateinit var swSwapXy: Switch
    private lateinit var swInvX: Switch
    private lateinit var swInvY: Switch
    private lateinit var swGps10: Switch

    // 【已删除】赛道相关变量 (rgTrackMode, etTrackRadius, btnTrackSetup, btnTrackReset)

    private lateinit var btnCmdSync: Button
    private lateinit var btnCmdReport: Button
    private lateinit var btnCmdSave: Button

    private var isUpdatingUi = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as MainActivity
        val prefs = mainActivity.getSharedPreferences("RacetrixPrefs", Context.MODE_PRIVATE)

        // 1. 初始化控件绑定
        val btnBack = view.findViewById<View>(R.id.btn_settings_back)

        sbVolume = view.findViewById(R.id.sb_volume)
        tvVolVal = view.findViewById(R.id.tv_vol_val)

        swSwapXy = view.findViewById(R.id.sw_swap_xy)
        swInvX = view.findViewById(R.id.sw_inv_x)
        swInvY = view.findViewById(R.id.sw_inv_y)
        swGps10 = view.findViewById(R.id.sw_gps_10hz)

        // 【已删除】赛道相关 findViewById

        btnCmdSync = view.findViewById(R.id.btn_cmd_sync)
        btnCmdReport = view.findViewById(R.id.btn_cmd_report)
        btnCmdSave = view.findViewById(R.id.btn_cmd_save)

        // 2. 回显本地保存的配置
        isUpdatingUi = true

        // 自动请求同步
        view.postDelayed({
            mainActivity.sendCommand("CMD:SYNC")
        }, 300)

        val savedVol = prefs.getInt("CFG_VOL", 15)
        sbVolume.progress = savedVol
        tvVolVal.text = savedVol.toString()

        swSwapXy.isChecked = prefs.getBoolean("CFG_SWAP", false)
        swInvX.isChecked = prefs.getBoolean("CFG_INV_X", false)
        swInvY.isChecked = prefs.getBoolean("CFG_INV_Y", false)
        swGps10.isChecked = prefs.getBoolean("CFG_GPS10", false)

        isUpdatingUi = false

        // 3. 绑定事件监听

        btnBack.setOnClickListener {
            mainActivity.supportFragmentManager.popBackStack()
        }

        sbVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) tvVolVal.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (!isUpdatingUi) {
                    mainActivity.sendCommand("SET:VOL=${seekBar?.progress}")
                }
            }
        })

        val switchListener = CompoundButton.OnCheckedChangeListener { v, isChecked ->
            if (!isUpdatingUi) {
                val valStr = if (isChecked) "1" else "0"
                when (v.id) {
                    R.id.sw_swap_xy -> mainActivity.sendCommand("SET:SWAP=$valStr")
                    R.id.sw_inv_x -> mainActivity.sendCommand("SET:INV_X=$valStr")
                    R.id.sw_inv_y -> mainActivity.sendCommand("SET:INV_Y=$valStr")
                    R.id.sw_gps_10hz -> mainActivity.sendCommand("SET:GPS10=$valStr")
                }
            }
        }
        swSwapXy.setOnCheckedChangeListener(switchListener)
        swInvX.setOnCheckedChangeListener(switchListener)
        swInvY.setOnCheckedChangeListener(switchListener)
        swGps10.setOnCheckedChangeListener(switchListener)

        // 【已删除】赛道相关按钮点击事件

        // 系统指令按钮
        btnCmdSync.setOnClickListener {
            mainActivity.sendCommand("CMD:SYNC")
            Toast.makeText(context, "Requesting Sync...", Toast.LENGTH_SHORT).show()
        }

        btnCmdReport.setOnClickListener {
            mainActivity.sendCommand("CMD:REPORT")
        }

        btnCmdSave.setOnClickListener {
            mainActivity.sendCommand("CMD:SAVE")
        }
    }

    // ==========================================
    // 供 MainActivity 调用的更新方法
    // ==========================================

    fun updateVolume(vol: Int) {
        activity?.runOnUiThread {
            isUpdatingUi = true
            sbVolume.progress = vol
            tvVolVal.text = vol.toString()
            isUpdatingUi = false
        }
    }

    fun updateSwitch(switchId: Int, isChecked: Boolean) {
        activity?.runOnUiThread {
            val switchView = view?.findViewById<Switch>(switchId)
            if (switchView != null) {
                isUpdatingUi = true
                switchView.isChecked = isChecked
                isUpdatingUi = false
            }
        }
    }
}