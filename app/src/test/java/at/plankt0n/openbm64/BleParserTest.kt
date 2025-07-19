package at.plankt0n.openbm64

import at.plankt0n.openbm64.db.BleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleParserTest {
    @Test
    fun parse_full_measurement() {
        val data = byteArrayOf(
            0x1E,
            0x78,0x00,  // systolic 120
            0x50,0x00,  // diastolic 80
            0x00,0x00,  // MAP ignored
            0xE8.toByte(),0x07, // year 2024
            0x05, // month May
            0x0A, // day 10
            0x0F, // hour 15
            0x1E, // minute 30
            0x00, // second 0
            0x46,0x00, // pulse 70
            0x01, // user id
            0x00,0x00 // status
        )
        val m = BleParser.parseMeasurement(data)!!
        assertEquals("2024-05-10 15:30:00", m.timestamp)
        assertEquals(120, m.systole)
        assertEquals(80, m.diastole)
        assertEquals(93.33, m.map, 0.01)
        assertEquals(70, m.pulse)
    }

    @Test
    fun parse_invalid_data_returns_null() {
        val invalid = byteArrayOf(0x00)
        val m = BleParser.parseMeasurement(invalid)
        assertNull(m)
    }
}
