package com.lition.racetrixpro

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class TrackSetupFragment : Fragment(R.layout.fragment_track_setup) {

    private lateinit var etRadius: EditText
    private lateinit var etStartLat: EditText
    private lateinit var etStartLon: EditText
    private lateinit var etEndLat: EditText
    private lateinit var etEndLon: EditText
    private lateinit var rgMode: RadioGroup
    private lateinit var rbCircuit: RadioButton
    private lateinit var rbSprint: RadioButton

    // 文件选择器 (用于导入)
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            parseAndLoadJson(uri)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mainActivity = activity as MainActivity

        // 1. 初始化控件
        etRadius = view.findViewById(R.id.et_track_radius)
        etStartLat = view.findViewById(R.id.et_start_lat)
        etStartLon = view.findViewById(R.id.et_start_lon)
        etEndLat = view.findViewById(R.id.et_end_lat)
        etEndLon = view.findViewById(R.id.et_end_lon)
        rgMode = view.findViewById(R.id.rg_track_mode)
        rbCircuit = view.findViewById(R.id.rb_circuit)
        rbSprint = view.findViewById(R.id.rb_sprint)

        // 2. 按钮事件绑定

        // 返回
        view.findViewById<View>(R.id.btn_back).setOnClickListener {
            mainActivity.supportFragmentManager.popBackStack()
        }

        // 导入 JSON
        view.findViewById<View>(R.id.btn_import_json).setOnClickListener {
            try {
                filePickerLauncher.launch("application/json")
            } catch (e: Exception) {
                filePickerLauncher.launch("*/*") // 兼容性回退
            }
            Toast.makeText(context, "Select Track File", Toast.LENGTH_SHORT).show()
        }

        // 导出 JSON (保存到 Downloads)
        view.findViewById<View>(R.id.btn_export_json).setOnClickListener {
            exportTrackToPublicStorage()
        }

        // 获取起点 (从 MainActivity 读取实时 GPS)
        view.findViewById<View>(R.id.btn_get_start_pos).setOnClickListener {
            if (mainActivity.lastLat != 0.0) {
                etStartLat.setText(mainActivity.lastLat.toString())
                etStartLon.setText(mainActivity.lastLon.toString())
                Toast.makeText(context, "Start Point Captured!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No GPS Data yet...", Toast.LENGTH_SHORT).show()
            }
        }

        // 获取终点
        view.findViewById<View>(R.id.btn_get_end_pos).setOnClickListener {
            if (mainActivity.lastLat != 0.0) {
                etEndLat.setText(mainActivity.lastLat.toString())
                etEndLon.setText(mainActivity.lastLon.toString())
                Toast.makeText(context, "End Point Captured!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No GPS Data yet...", Toast.LENGTH_SHORT).show()
            }
        }

        // 上传到 ESP32
        view.findViewById<View>(R.id.btn_track_upload).setOnClickListener {
            val radius = etRadius.text.toString().ifEmpty { "5.0" }
            val mode = if (rgMode.checkedRadioButtonId == R.id.rb_circuit) "0" else "1"
            val sLat = etStartLat.text.toString().ifEmpty { "0.000000" }
            val sLon = etStartLon.text.toString().ifEmpty { "0.000000" }
            val eLat = etEndLat.text.toString().ifEmpty { "0.000000" }
            val eLon = etEndLon.text.toString().ifEmpty { "0.000000" }

            // 指令格式: TRACK:SETUP=模式,半径,起点Lat,起点Lon,终点Lat,终点Lon
            val cmd = "TRACK:SETUP=$mode,$radius,$sLat,$sLon,$eLat,$eLon"
            mainActivity.sendCommand(cmd)
            Toast.makeText(context, "Config Uploaded!", Toast.LENGTH_SHORT).show()
        }

        // 重置
        view.findViewById<View>(R.id.btn_track_reset).setOnClickListener {
            mainActivity.sendCommand("TRACK:RESET")
            Toast.makeText(context, "Reset Command Sent", Toast.LENGTH_SHORT).show()
        }
    }

    // ==========================================
    // 逻辑 1: 导入 JSON
    // ==========================================
    private fun parseAndLoadJson(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            inputStream?.close()

            val json = JSONObject(sb.toString())

            // 兼容简单的格式和带 config 嵌套的格式
            val config = if (json.has("config")) json.getJSONObject("config") else json

            val type = config.optString("type", "CIRCUIT").uppercase()
            val radius = config.optDouble("radius", 10.0)

            val startObj = config.optJSONObject("start_point")
            val endObj = config.optJSONObject("end_point")

            if (type == "SPRINT" || type == "1") rbSprint.isChecked = true else rbCircuit.isChecked = true
            etRadius.setText(radius.toString())

            if (startObj != null) {
                etStartLat.setText(startObj.optDouble("lat", 0.0).toString())
                etStartLon.setText(startObj.optDouble("lon", 0.0).toString())
            }
            if (endObj != null) {
                etEndLat.setText(endObj.optDouble("lat", 0.0).toString())
                etEndLon.setText(endObj.optDouble("lon", 0.0).toString())
            }

            val trackName = json.optJSONObject("meta")?.optString("name") ?: json.optString("name", "Track")
            Toast.makeText(context, "Loaded: $trackName", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Invalid JSON File", Toast.LENGTH_SHORT).show()
        }
    }

    // ==========================================
    // 逻辑 2: 导出 JSON 到 Downloads (含防作弊签名)
    // ==========================================
    private fun exportTrackToPublicStorage() {
        // 收集数据
        val radiusStr = etRadius.text.toString().ifEmpty { "10.0" }
        val sLat = etStartLat.text.toString().toDoubleOrNull() ?: 0.0
        val sLon = etStartLon.text.toString().toDoubleOrNull() ?: 0.0
        val eLat = etEndLat.text.toString().toDoubleOrNull() ?: 0.0
        val eLon = etEndLon.text.toString().toDoubleOrNull() ?: 0.0
        val type = if (rbCircuit.isChecked) "CIRCUIT" else "SPRINT"

        // 构建 JSON
        val root = JSONObject()

        val meta = JSONObject()
        meta.put("name", "Track_" + SimpleDateFormat("MMdd_HHmm", Locale.US).format(Date()))
        meta.put("author", "Racetrix User")
        meta.put("create_time", System.currentTimeMillis())
        root.put("meta", meta)

        val config = JSONObject()
        config.put("type", type)
        config.put("radius", radiusStr.toDouble())
        config.put("start_point", JSONObject().apply { put("lat", sLat); put("lon", sLon) })
        config.put("end_point", JSONObject().apply { put("lat", eLat); put("lon", eLon) })
        root.put("config", config)

        // 生成签名
        val contentToSign = config.toString()
        val signature = generateSHA256(contentToSign)

        val security = JSONObject()
        security.put("algorithm", "SHA-256")
        security.put("signature", signature)
        root.put("security", security)

        // 保存文件
        val fileName = "Racetrix_${System.currentTimeMillis()}.json"
        saveToDownloads(fileName, root.toString(2))
    }

    private fun saveToDownloads(fileName: String, content: String) {
        try {
            val resolver = requireContext().contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Racetrix")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri).use { it?.write(content.toByteArray()) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                Toast.makeText(context, "Saved to Downloads/Racetrix", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Save Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateSHA256(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) { "" }
    }
}