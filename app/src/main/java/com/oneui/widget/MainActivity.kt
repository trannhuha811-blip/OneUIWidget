package com.oneui.widget

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.oneui.widget.data.WeatherRepository
import com.oneui.widget.data.WidgetPreferences
import com.oneui.widget.databinding.ActivityMainBinding
import com.oneui.widget.lunar.LunarCalendarConverter
import com.oneui.widget.ui.OneUIWidgetProvider
import com.oneui.widget.worker.WidgetUpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: WidgetPreferences

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            Toast.makeText(this, "Đã cấp quyền vị trí!", Toast.LENGTH_SHORT).show()
            updateUIState()
            OneUIWidgetProvider.updateAllWidgets(this)
        }
    }

    private val requestCalendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Đã cấp quyền đọc lịch!", Toast.LENGTH_SHORT).show()
            updateUIState()
            OneUIWidgetProvider.updateAllWidgets(this)
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = WidgetPreferences(this)

        // Khởi tạo các dịch vụ nền
        WidgetUpdateWorker.enqueuePeriodicWork(this)
        com.oneui.widget.receiver.MidnightAlarmReceiver.scheduleMidnightAlarm(this)

        setupListeners()
        updateUIState()
        loadPreviewData()

        // Xin quyền thông báo trên Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
    }

    private fun setupListeners() {
        // Nút cấp quyền Vị trí
        binding.btnGrantLocation.setOnClickListener {
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        // Nút cấp quyền Đọc lịch
        binding.btnGrantCalendar.setOnClickListener {
            requestCalendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        }

        // Nút tắt tối ưu hóa pin
        binding.btnBatteryOptimization.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }

        // Tùy chỉnh độ mờ nền (0% - 50%)
        binding.seekOpacity.progress = prefs.backgroundOpacity
        binding.tvOpacityVal.text = "${prefs.backgroundOpacity}%"
        binding.seekOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvOpacityVal.text = "$progress%"
                prefs.backgroundOpacity = progress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                OneUIWidgetProvider.updateAllWidgets(this@MainActivity)
            }
        })

        // Chuyển đổi GPS tự động vs Cố định
        binding.switchAutoLocation.isChecked = prefs.useAutoLocation
        binding.layoutFixedCity.visibility = if (prefs.useAutoLocation) View.GONE else View.VISIBLE
        binding.edtFixedCity.setText(prefs.fixedCityName)

        binding.switchAutoLocation.setOnCheckedChangeListener { _, isChecked ->
            prefs.useAutoLocation = isChecked
            binding.layoutFixedCity.visibility = if (isChecked) View.GONE else View.VISIBLE
            OneUIWidgetProvider.updateAllWidgets(this)
        }

        binding.edtFixedCity.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = binding.edtFixedCity.text?.toString()?.trim()
                if (!text.isNullOrEmpty()) {
                    prefs.fixedCityName = text
                    OneUIWidgetProvider.updateAllWidgets(this)
                }
            }
        }

        // Nút thêm Widget vào màn hình chính (One UI Home Launcher pin widget)
        binding.btnPinWidget.setOnClickListener {
            pinWidgetToHomeScreen()
        }

        // Nút làm mới dữ liệu widget ngay
        binding.btnRefreshNow.setOnClickListener {
            Toast.makeText(this, "Đang đồng bộ dữ liệu...", Toast.LENGTH_SHORT).show()
            OneUIWidgetProvider.updateAllWidgets(this)
            loadPreviewData()
        }
    }

    private fun updateUIState() {
        // Kiểm tra quyền vị trí
        val hasLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasLocation) {
            binding.btnGrantLocation.text = "Đã cấp"
            binding.btnGrantLocation.isEnabled = false
        } else {
            binding.btnGrantLocation.text = "Cấp quyền"
            binding.btnGrantLocation.isEnabled = true
        }

        // Kiểm tra quyền lịch
        val hasCalendar = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCalendar) {
            binding.btnGrantCalendar.text = "Đã cấp"
            binding.btnGrantCalendar.isEnabled = false
        } else {
            binding.btnGrantCalendar.text = "Cấp quyền"
            binding.btnGrantCalendar.isEnabled = true
        }

        // Kiểm tra tối ưu pin
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(packageName) ?: false
        } else true

        if (isIgnoringBattery) {
            binding.btnBatteryOptimization.text = "Đã bật"
            binding.btnBatteryOptimization.isEnabled = false
        } else {
            binding.btnBatteryOptimization.text = "Cài đặt"
            binding.btnBatteryOptimization.isEnabled = true
        }
    }

    private fun loadPreviewData() {
        val lunar = LunarCalendarConverter.getTodayLunar()
        val cal = Calendar.getInstance()
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val m = cal.get(Calendar.MONTH) + 1
        val y = cal.get(Calendar.YEAR)

        binding.tvPreviewInfo.text = "Hôm nay: Ngày $d tháng $m năm $y"
        binding.tvLunarPreview.text = "Lịch Âm: ${lunar.day}/${lunar.month} • Năm ${lunar.yearCanChi} • Can Chi: ${lunar.dayCanChi} (Tiết: ${lunar.solarTerm})"

        lifecycleScope.launch {
            val repo = WeatherRepository(this@MainActivity)
            val weather = withContext(Dispatchers.IO) { repo.getWeatherData() }
            binding.tvPreviewInfo.text = "Thời tiết: ${weather.current.temp}°C, ${weather.current.conditionDescription} (${weather.current.locationName})"
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                } catch (e2: Exception) {
                    Toast.makeText(this, "Vui lòng tắt tối ưu pin trong Cài đặt hệ thống.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun pinWidgetToHomeScreen() {
        val appWidgetManager = getSystemService(AppWidgetManager::class.java)
        val provider = ComponentName(this, OneUIWidgetProvider::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
                val successCallback = PendingIntent.getBroadcast(
                    this,
                    3001,
                    Intent(this, OneUIWidgetProvider::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                appWidgetManager.requestPinAppWidget(provider, null, successCallback)
                Toast.makeText(this, "Đã gửi yêu cầu thêm Widget vào màn hình chính!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Vui lòng nhấn giữ trên màn hình chính và chọn Widget.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Vui lòng nhấn giữ trên màn hình chính và chọn Widget.", Toast.LENGTH_LONG).show()
        }
    }
}
