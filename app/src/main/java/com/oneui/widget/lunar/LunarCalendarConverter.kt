package com.oneui.widget.lunar

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Thuật toán tính Lịch Âm Việt Nam chuẩn thiên văn của Hồ Ngọc Đức.
 * Chạy offline 100%, không cần kết nối mạng.
 * Hỗ trợ tính chính xác ngày, tháng âm lịch, tháng nhuận, Can Chi Ngày/Tháng/Năm, Tiết khí.
 */
object LunarCalendarConverter {

    private const val PI_RAD = PI / 180.0
    private const val TIME_ZONE_VN = 7.0 // Múi giờ Việt Nam UTC+7

    val CAN = arrayOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
    val CHI = arrayOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")

    val TIET_KHI = arrayOf(
        "Xuân phân", "Thanh minh", "Cốc vũ", "Lập hạ", "Tiểu mãn", "Mang chủng",
        "Hạ chí", "Tiểu thử", "Đại thử", "Lập thu", "Xử thử", "Bạch lộ",
        "Thu phân", "Hàn lộ", "Sương giáng", "Lập đông", "Tiểu tuyết", "Đại tuyết",
        "Đông chí", "Tiểu hàn", "Đại hàn", "Lập xuân", "Vũ thủy", "Kinh trập"
    )

    data class LunarDate(
        val day: Int,
        val month: Int,
        val year: Int,
        val isLeap: Boolean,
        val dayCanChi: String,
        val monthCanChi: String,
        val yearCanChi: String,
        val solarTerm: String
    ) {
        /**
         * Hiển thị dạng viên thuốc (Pill Badge) theo đúng ảnh mẫu: "15/10 Bính Ngọ"
         */
        val pillText: String
            get() = "$day/$month${if (isLeap) " (Nhuận)" else ""} $dayCanChi"

        val shortDate: String
            get() = "$day/$month"
    }

    /**
     * Chuyển đổi ngày dương lịch sang ngày âm lịch
     */
    fun convertSolarToLunar(solarDay: Int, solarMonth: Int, solarYear: Int): LunarDate {
        val jd = jdFromDate(solarDay, solarMonth, solarYear)
        val k = floor((jd - 2415021.076998695) / 29.530588853).toInt()

        var nm = getNewMoonDay(k + 1, TIME_ZONE_VN)
        var nextNm = nm
        var currentK = k
        if (nm > jd) {
            nextNm = nm
            currentK = k
            nm = getNewMoonDay(currentK, TIME_ZONE_VN)
        } else {
            nextNm = getNewMoonDay(currentK + 2, TIME_ZONE_VN)
            currentK += 1
        }

        val day = jd - nm + 1

        // Tìm tháng 11 âm lịch của năm này hoặc năm trước (chứa ngày Đông chí)
        // Đông chí có solar longitude = 270 (tức sunLongitudeIndex = 9 trong 12 cung)
        val a11 = getSunLongitudeIndex(findDongChi(solarYear - 1), TIME_ZONE_VN)
        val b11 = getSunLongitudeIndex(findDongChi(solarYear), TIME_ZONE_VN)

        // Tính tháng và năm âm lịch
        val lunarMonthInfo = computeLunarMonthAndYear(solarDay, solarMonth, solarYear, jd)
        val lunarMonth = lunarMonthInfo.first
        val isLeap = lunarMonthInfo.second
        val lunarYear = lunarMonthInfo.third

        // Tính Can Chi
        val dayCan = CAN[(jd + 9) % 10]
        val dayChi = CHI[(jd + 1) % 12]
        val dayCanChi = "$dayCan $dayChi"

        val yearCan = CAN[(lunarYear + 6) % 10]
        val yearChi = CHI[(lunarYear + 8) % 12]
        val yearCanChi = "$yearCan $yearChi"

        val monthCan = CAN[(lunarYear * 12 + lunarMonth + 3) % 10]
        val monthChi = CHI[(lunarMonth + 1) % 12]
        val monthCanChi = "$monthCan $monthChi"

        val solarTermIndex = getSolarTermIndex(jd, TIME_ZONE_VN)
        val solarTerm = TIET_KHI[solarTermIndex]

        return LunarDate(
            day = day,
            month = lunarMonth,
            year = lunarYear,
            isLeap = isLeap,
            dayCanChi = dayCanChi,
            monthCanChi = monthCanChi,
            yearCanChi = yearCanChi,
            solarTerm = solarTerm
        )
    }

    fun getTodayLunar(): LunarDate {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+7"))
        return convertSolarToLunar(
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.YEAR)
        )
    }

    private fun computeLunarMonthAndYear(dd: Int, mm: Int, yy: Int, jd: Int): Triple<Int, Boolean, Int> {
        // Tìm ngày Sóc chứa Đông Chí năm trước và năm nay
        var k = floor((jd - 2415021.076998695) / 29.530588853).toInt()
        val off = jd - 2415021
        var nm = getNewMoonDay(k, TIME_ZONE_VN)
        if (nm > jd) {
            k--
            nm = getNewMoonDay(k, TIME_ZONE_VN)
        }

        // Lấy tháng 11 âm lịch năm trước
        val dongChiPrev = findDongChi(yy - 1)
        var kPrev11 = floor((dongChiPrev - 2415021.076998695) / 29.530588853).toInt()
        var nmPrev11 = getNewMoonDay(kPrev11, TIME_ZONE_VN)
        if (nmPrev11 > dongChiPrev) {
            kPrev11--
            nmPrev11 = getNewMoonDay(kPrev11, TIME_ZONE_VN)
        }

        // Lấy tháng 11 âm lịch năm nay
        val dongChiCurr = findDongChi(yy)
        var kCurr11 = floor((dongChiCurr - 2415021.076998695) / 29.530588853).toInt()
        var nmCurr11 = getNewMoonDay(kCurr11, TIME_ZONE_VN)
        if (nmCurr11 > dongChiCurr) {
            kCurr11--
            nmCurr11 = getNewMoonDay(kCurr11, TIME_ZONE_VN)
        }

        var startK: Int
        var lunarYear: Int
        if (k >= kCurr11) {
            startK = kCurr11
            lunarYear = yy
        } else {
            startK = kPrev11
            lunarYear = yy - 1
        }

        // Xác định xem có tháng nhuận giữa 2 tháng 11 hay không
        val endK = if (k >= kCurr11) {
            val dongChiNext = findDongChi(yy + 1)
            var knext = floor((dongChiNext - 2415021.076998695) / 29.530588853).toInt()
            if (getNewMoonDay(knext, TIME_ZONE_VN) > dongChiNext) knext--
            knext
        } else {
            kCurr11
        }

        val totalMonths = endK - startK
        val hasLeap = (totalMonths == 13)
        var leapMonthIndex = -1

        if (hasLeap) {
            // Tìm tháng đầu tiên không có trung khí (major solar term)
            for (i in 0 until totalMonths) {
                val nm1 = getNewMoonDay(startK + i, TIME_ZONE_VN)
                val nm2 = getNewMoonDay(startK + i + 1, TIME_ZONE_VN)
                val sunLong1 = getSunLongitudeIndex(nm1, TIME_ZONE_VN)
                val sunLong2 = getSunLongitudeIndex(nm2, TIME_ZONE_VN)
                if (sunLong1 == sunLong2) {
                    leapMonthIndex = i
                    break
                }
            }
        }

        val monthDiff = k - startK
        var month = (11 + monthDiff)
        var isLeap = false

        if (hasLeap && leapMonthIndex in 0..monthDiff) {
            if (monthDiff == leapMonthIndex) {
                isLeap = true
                month = (11 + monthDiff - 1)
            } else {
                month = (11 + monthDiff - 1)
            }
        }

        month = (month - 1) % 12 + 1

        // Nếu tháng âm lịch từ tháng 1 đến 10 thuộc về năm âm lịch tiếp theo
        val effectiveLunarYear = if (k >= kCurr11) {
            if (month >= 11) lunarYear else lunarYear + 1
        } else {
            if (month >= 11) lunarYear else lunarYear + 1
        }

        return Triple(month, isLeap, effectiveLunarYear)
    }

    private fun findDongChi(yy: Int): Int {
        return jdFromDate(22, 12, yy)
    }

    fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) {
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        }
        return jd
    }

    fun jdToDate(jd: Int): Triple<Int, Int, Int> {
        var a = jd + 32044
        var b = (4 * a + 3) / 146097
        var c = a - (146097 * b) / 4
        var d = (4 * c + 3) / 1461
        var e = c - (1461 * d) / 4
        var m = (5 * e + 2) / 153
        val day = e - (153 * m + 2) / 5 + 1
        val month = m + 3 - 12 * (m / 10)
        val year = 100 * b + d - 4800 + (m / 10)
        return Triple(day, month, year)
    }

    fun getNewMoonDay(k: Int, timeZone: Double): Int {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * PI_RAD)

        // Sun's mean anomaly
        val m = (359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3) * PI_RAD
        // Moon's mean anomaly
        val mPrime = (306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3) * PI_RAD
        // Moon's argument of latitude
        val f = (21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3) * PI_RAD
        // Longitude of ascending node
        val omega = (259.1833 - 0.05295392 * k + 0.002078 * t2 + 0.0000022 * t3) * PI_RAD

        // Perturbations
        var delta = (0.1734 - 0.000393 * t) * sin(m) +
                0.0021 * sin(2 * m) -
                0.4068 * sin(mPrime) +
                0.0161 * sin(2 * mPrime) -
                0.0004 * sin(3 * mPrime) +
                0.0104 * sin(2 * f) -
                0.0051 * sin(m + mPrime) -
                0.0074 * sin(m - mPrime) +
                0.0004 * sin(2 * f + m) -
                0.0004 * sin(2 * f - m) -
                0.0006 * sin(2 * f + mPrime) +
                0.0010 * sin(2 * f - mPrime) +
                0.0005 * sin(m + 2 * mPrime)

        val jd = jd1 + delta + timeZone / 24.0
        return floor(jd).toInt()
    }

    private fun getSunLongitudeIndex(jd: Int, timeZone: Double): Int {
        val sl = getSunLongitude(jd + 0.5 - timeZone / 24.0)
        return floor(sl / 30.0).toInt()
    }

    private fun getSolarTermIndex(jd: Int, timeZone: Double): Int {
        val sl = getSunLongitude(jd + 0.5 - timeZone / 24.0)
        return floor(sl / 15.0).toInt() % 24
    }

    private fun getSunLongitude(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525.0
        val t2 = t * t
        // Geometric mean longitude of the Sun
        var l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t2
        // Mean anomaly of the Sun
        val m = (357.52911 + 35999.05029 * t - 0.0001537 * t2) * PI_RAD
        // Equation of center
        val c = (1.914602 - 0.004817 * t - 0.000014 * t2) * sin(m) +
                (0.019993 - 0.000101 * t) * sin(2 * m) +
                0.000289 * sin(3 * m)
        // True longitude
        var sunLong = (l0 + c) % 360.0
        if (sunLong < 0) sunLong += 360.0
        return sunLong
    }
}
