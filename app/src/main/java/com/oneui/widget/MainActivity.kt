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
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.oneui.widget.data.CalendarEventHelper
import com.oneui.widget.data.WeatherRepository
import com.oneui.widget.data.WeatherUtils
import com.oneui.widget.data.WidgetPreferences
import com.oneui.widget.databinding.ActivityMainBinding
import com.oneui.widget.lunar.LunarCalendarConverter
import com.oneui.widget.ui.CalendarGridRenderer
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
            loadPreviewData()
            OneUIWidgetProvider.updateAllWidgets(this)
        }
    }

    private val requestCalendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Đã cấp quyền đọc lịch!", Toast.LENGTH_SHORT).show()
            updateUIState()
            loadPreviewData()
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
        try {
            WidgetUpdateWorker.enqueuePeriodicWork(this)
            com.oneui.widget.receiver.MidnightAlarmReceiver.scheduleMidnightAlarm(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Lỗi khởi tạo dịch vụ nền", e)
        }

        setupListeners()
        updateUIState()
        loadPreviewData()

        // Cập nhật lại toàn bộ widget ngoài màn hình chính ngay lập tức
        OneUIWidgetProvider.updateAllWidgets(this)

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
        loadPreviewData()
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
            loadPreviewData()
        }

        binding.edtFixedCity.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = binding.edtFixedCity.text?.toString()?.trim()
                if (!text.isNullOrEmpty()) {
                    prefs.fixedCityName = text
                    OneUIWidgetProvider.updateAllWidgets(this)
                    loadPreviewData()
                }
            }
        }

        // Nút thêm Widget vào màn hình chính
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
        val hasLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        binding.btnGrantLocation.text = if (hasLocation) "Đã cấp" else "Cấp quyền"
        binding.btnGrantLocation.isEnabled = !hasLocation

        val hasCalendar = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        binding.btnGrantCalendar.text = if (hasCalendar) "Đã cấp" else "Cấp quyền"
        binding.btnGrantCalendar.isEnabled = !hasCalendar

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(packageName) ?: false
        } else true

        binding.btnBatteryOptimization.text = if (isIgnoringBattery) "Đã bật" else "Cài đặt"
        binding.btnBatteryOptimization.isEnabled = !isIgnoringBattery
    }

    /**
     * Nạp dữ liệu hiển thị trực tiếp lên khung Preview Widget bên trong App
     */
    private fun loadPreviewData() {
        try {
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
            val month = cal.get(Calendar.MONTH) + 1
            val dayOfWeekInt = cal.get(Calendar.DAY_OF_WEEK)

            val preview = binding.widgetPreviewLayout

            // Lời chào & Ngày giờ
            preview.tvGreeting.text = WeatherUtils.getGreetingByHour(hour)
            preview.tvSolarMonth.text = "THÁNG $month"
            preview.tvSolarDay.text = dayOfMonth.toString()
            preview.tvDayOfWeek.text = when (dayOfWeekInt) {
                Calendar.MONDAY -> "Thứ Hai"
                Calendar.TUESDAY -> "Thứ Ba"
                Calendar.WEDNESDAY -> "Thứ Tư"
                Calendar.THURSDAY -> "Thứ Năm"
                Calendar.FRIDAY -> "Thứ Sáu"
                Calendar.SATURDAY -> "Thứ Bảy"
                Calendar.SUNDAY -> "Chủ Nhật"
                else -> "Thứ Hai"
            }

            // Lịch âm Can Chi
            try {
                val lunar = LunarCalendarConverter.getTodayLunar()
                preview.tvLunarDayMonth.text = lunar.dayMonthText
                preview.tvLunarCanchi.text = lunar.canChiText
            } catch (e: Exception) {
                preview.tvLunarDayMonth.text = "$dayOfMonth/$month"
                preview.tvLunarCanchi.text = "ÂL"
            }

            // Lưới lịch tháng
            try {
                val bitmap = CalendarGridRenderer.renderCalendarBitmap(this, 420, 300)
                preview.imgCalendarGrid.setImageBitmap(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Sự kiện & Ngày lễ (3 dòng)
            try {
                val eventHelper = CalendarEventHelper(this)
                val events = eventHelper.getUpcomingEvents(2)
                if (events.isNotEmpty()) {
                    preview.tvEvent1Title.text = events[0].title
                    preview.tvEvent1Time.text = events[0].dateInfo
                    preview.tvEvent1Source.text = events[0].sourceInfo
                }
                if (events.size > 1) {
                    preview.tvEvent2Title.text = events[1].title
                    preview.tvEvent2Time.text = events[1].dateInfo
                    preview.tvEvent2Source.text = events[1].sourceInfo
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Thời tiết nhanh từ cache/mặc định trước
            val weatherRepo = WeatherRepository(this)
            val initWeather = weatherRepo.getCachedOrDefaultWeather()
            bindWeatherToPreview(initWeather)

            // Sau đó gọi mạng ngầm nạp thời tiết mới
            lifecycleScope.launch {
                try {
                    val freshWeather = withContext(Dispatchers.IO) { weatherRepo.getWeatherData() }
                    bindWeatherToPreview(freshWeather)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Lỗi tải thời tiết", e)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Lỗi loadPreviewData", e)
        }
    }

    private fun bindWeatherToPreview(weather: com.oneui.widget.data.WidgetWeatherData) {
        try {
            val preview = binding.widgetPreviewLayout
            preview.tvMainTemp.text = "${weather.current.temp}°"
            preview.tvWeatherDesc.text = weather.current.conditionDescription
            preview.tvLocationName.text = weather.current.locationName
            preview.tvSunriseInfo.text = "${weather.current.minTempToday}°C"
            preview.tvSunsetInfo.text = "${weather.current.maxTempToday}°C"

            preview.ivBottomTodayIcon.setImageResource(WeatherUtils.getWeatherIcon(weather.current.weatherCode))
            preview.tvBottomTodayTemp.text = "${weather.current.temp}°C"
            preview.tvBottomTodayMinmax.text = "${weather.current.minTempToday}°  ${weather.current.maxTempToday}°"

            val f = weather.forecast6Days
            if (f.size >= 6) {
                preview.tvF1Day.text = f[0].dayOfWeek
                preview.ivF1Icon.setImageResource(f[0].getWeatherIconRes())
                preview.tvF1Min.text = "↓ ${f[0].minTemp}°"
                preview.tvF1Max.text = "↑ ${f[0].maxTemp}°"

                preview.tvF2Day.text = f[1].dayOfWeek
                preview.ivF2Icon.setImageResource(f[1].getWeatherIconRes())
                preview.tvF2Min.text = "↓ ${f[1].minTemp}°"
                preview.tvF2Max.text = "↑ ${f[1].maxTemp}°"

                preview.tvF3Day.text = f[2].dayOfWeek
                preview.ivF3Icon.setImageResource(f[2].getWeatherIconRes())
                preview.tvF3Min.text = "↓ ${f[2].minTemp}°"
                preview.tvF3Max.text = "↑ ${f[2].maxTemp}°"

                preview.tvF4Day.text = f[3].dayOfWeek
                preview.ivF4Icon.setImageResource(f[3].getWeatherIconRes())
                preview.tvF4Min.text = "↓ ${f[3].minTemp}°"
                preview.tvF4Max.text = "↑ ${f[3].maxTemp}°"

                preview.tvF5Day.text = f[4].dayOfWeek
                preview.ivF5Icon.setImageResource(f[4].getWeatherIconRes())
                preview.tvF5Min.text = "↓ ${f[4].minTemp}°"
                preview.tvF5Max.text = "↑ ${f[4].maxTemp}°"

                preview.tvF6Day.text = f[5].dayOfWeek
                preview.ivF6Icon.setImageResource(f[5].getWeatherIconRes())
                preview.tvF6Min.text = "↓ ${f[5].minTemp}°"
                preview.tvF6Max.text = "↑ ${f[5].maxTemp}°"
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Lỗi bindWeatherToPreview", e)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = getSystemService(AppWidgetManager::class.java)
            val provider = ComponentName(this, OneUIWidgetProvider::class.java)

            if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
                val successCallback = PendingIntent.getBroadcast(
                    this,
                    3001,
                    Intent(this, OneUIWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val result = appWidgetManager.requestPinAppWidget(provider, null, successCallback)
                if (result) {
                    Toast.makeText(this, "Đã gửi yêu cầu thêm Widget vào màn hình chính!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Launcher không hỗ trợ ghim tiện ích. Hãy nhấn giữ màn hình chính → Tiện ích.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Vui lòng nhấn giữ trên màn hình chính → Tiện ích → One UI Widget.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Vui lòng nhấn giữ trên màn hình chính → Tiện ích → One UI Widget.", Toast.LENGTH_LONG).show()
        }
    }
}
