package com.oneui.widget.data

/**
 * Chuẩn hóa và trích xuất tên đơn vị hành chính cấp Phường/Xã mới tại Việt Nam
 * từ kết quả Nominatim (OpenStreetMap) hoặc Android Geocoder.
 */
object LocationNameFormatter {

    private val WARD_PREFIXES = listOf("Phường ", "P. ", "P.", "Xã ", "X. ", "Thị trấn ", "Đặc khu ")

    private val NOMINATIM_WARD_KEYS = listOf(
        "suburb", "quarter", "town", "village", "county",
        "city_district", "municipality", "hamlet", "neighbourhood"
    )

    private val NOMINATIM_LOCALITY_FALLBACK_KEYS = listOf(
        "suburb", "town", "village", "city_district", "county"
    )

    private val NOMINATIM_PROVINCE_KEYS = listOf("city", "state", "province")

    fun hasWardOrCommunePrefix(name: String): Boolean {
        val trimmed = name.trim()
        return WARD_PREFIXES.any { trimmed.startsWith(it, ignoreCase = true) }
    }

    fun formatVietnameseWardOrCommune(rawName: String): String {
        val trimmed = rawName.trim()
        return when {
            trimmed.startsWith("Phường ", ignoreCase = true) ->
                "P. " + trimmed.substring(7).trim()
            trimmed.startsWith("P.", ignoreCase = true) && !trimmed.startsWith("P. ", ignoreCase = true) ->
                "P. " + trimmed.substring(2).trim()
            else -> trimmed
        }
    }

    /**
     * Trích xuất tên Phường/Xã mới từ bản đồ địa chỉ Nominatim.
     * Ưu tiên các trường có tiền tố Phường/Xã/Thị trấn/Đặc khu để không lấy nhầm Ấp/Khu phố hay Tỉnh.
     */
    fun fromNominatimAddress(address: Map<String, String>): String? {
        for (key in NOMINATIM_WARD_KEYS) {
            val value = address[key]?.trim()
            if (!value.isNullOrBlank() && hasWardOrCommunePrefix(value)) {
                return formatVietnameseWardOrCommune(value)
            }
        }

        for (key in NOMINATIM_LOCALITY_FALLBACK_KEYS) {
            val value = address[key]?.trim()
            if (!value.isNullOrBlank()) {
                return formatVietnameseWardOrCommune(value)
            }
        }

        for (key in NOMINATIM_PROVINCE_KEYS) {
            val value = address[key]?.trim()
            if (!value.isNullOrBlank()) {
                return value
            }
        }

        return null
    }

    /**
     * Trích xuất tên Phường/Xã từ các trường của Android Geocoder, có quét thêm addressLine
     * phòng trường hợp Google Geocoder để subLocality = null sau đợt sáp nhập hành chính.
     */
    fun fromGeocoderFields(
        subLocality: String?,
        locality: String?,
        subAdminArea: String?,
        adminArea: String?,
        addressLine: String?
    ): String? {
        if (!subLocality.isNullOrBlank()) {
            return formatVietnameseWardOrCommune(subLocality)
        }

        if (!addressLine.isNullOrBlank()) {
            val parts = addressLine.split(",")
            for (part in parts) {
                val trimmed = part.trim()
                if (hasWardOrCommunePrefix(trimmed)) {
                    return formatVietnameseWardOrCommune(trimmed)
                }
            }
        }

        val fallback = locality?.takeIf { it.isNotBlank() }
            ?: subAdminArea?.takeIf { it.isNotBlank() }
            ?: adminArea?.takeIf { it.isNotBlank() }
        return fallback?.let { formatVietnameseWardOrCommune(it) }
    }
}
