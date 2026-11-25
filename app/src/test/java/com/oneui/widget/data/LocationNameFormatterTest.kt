package com.oneui.widget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationNameFormatterTest {

    @Test
    fun testNominatimUrbanNewWardHcmc() {
        val address = mapOf(
            "neighbourhood" to "Khu phố 35",
            "suburb" to "Phường Thạnh Mỹ Tây",
            "city" to "Thành phố Hồ Chí Minh",
            "country" to "Việt Nam"
        )
        assertEquals("P. Thạnh Mỹ Tây", LocationNameFormatter.fromNominatimAddress(address))
    }

    @Test
    fun testNominatimRuralNewCommuneDongNai() {
        val address = mapOf(
            "village" to "Ấp Xuân Tiến",
            "county" to "Xã Xuân Quế",
            "state" to "Thành phố Đồng Nai",
            "country" to "Việt Nam"
        )
        assertEquals("Xã Xuân Quế", LocationNameFormatter.fromNominatimAddress(address))
    }

    @Test
    fun testNominatimProvinceFallback() {
        val address = mapOf(
            "city" to "Hà Nội",
            "country" to "Việt Nam"
        )
        assertEquals("Hà Nội", LocationNameFormatter.fromNominatimAddress(address))
    }

    @Test
    fun testNominatimEmptyReturnsNull() {
        assertNull(LocationNameFormatter.fromNominatimAddress(emptyMap()))
    }

    @Test
    fun testGeocoderAddressLineExtractsWardWhenSubLocalityNull() {
        val result = LocationNameFormatter.fromGeocoderFields(
            subLocality = null,
            locality = null,
            subAdminArea = null,
            adminArea = "Thành phố Hồ Chí Minh",
            addressLine = "123 Điện Biên Phủ, Phường Thạnh Mỹ Tây, Thành phố Hồ Chí Minh, Việt Nam"
        )
        assertEquals("P. Thạnh Mỹ Tây", result)
    }

    @Test
    fun testGeocoderSubLocalityTakesPriority() {
        val result = LocationNameFormatter.fromGeocoderFields(
            subLocality = "Phường Hoàn Kiếm",
            locality = "Hà Nội",
            subAdminArea = null,
            adminArea = "Hà Nội",
            addressLine = null
        )
        assertEquals("P. Hoàn Kiếm", result)
    }
}
