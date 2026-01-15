package com.lition.racetrixpro

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment

class SelectionFragment : Fragment(R.layout.fragment_selection) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as MainActivity

        // 1. 点击 Pro -> 走 BLE 逻辑
        view.findViewById<View>(R.id.card_pro).setOnClickListener {
            mainActivity.isBleMode = true
            mainActivity.goToDashboard()
        }

        // 2. 点击 Standard -> 走 经典蓝牙 逻辑
        view.findViewById<View>(R.id.card_standard).setOnClickListener {
            mainActivity.isBleMode = false
            mainActivity.goToDashboard()
        }
    }
}