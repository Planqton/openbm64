package at.plankt0n.openbm64.db

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar

object BleParser {
    fun parseMeasurement(data: ByteArray): Measurement? {
        return try {
            var index = 0
            val flags = data[index].toInt()
            index += 1

            val unitsKpa = flags and 0x01 != 0
            val timestampPresent = flags and 0x02 != 0
            val pulsePresent = flags and 0x04 != 0
            val userPresent = flags and 0x08 != 0
            val statusPresent = flags and 0x10 != 0

            val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            bb.position(index)
            var systolic = bb.short.toInt()
            var diastolic = bb.short.toInt()
            bb.short // MAP ignored
            var mapVal = diastolic + (systolic - diastolic) / 3.0

            if (unitsKpa) {
                systolic = Math.round(systolic * 7.50062f)
                diastolic = Math.round(diastolic * 7.50062f)
                mapVal *= 7.50062
            }
            mapVal = String.format("%.2f", mapVal).toDouble()

            val timestamp = if (timestampPresent) {
                val year = bb.short.toInt()
                val month = bb.get().toInt()
                val day = bb.get().toInt()
                val hour = bb.get().toInt()
                val minute = bb.get().toInt()
                val second = bb.get().toInt()
                String.format("%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, minute, second)
            } else {
                val now = Calendar.getInstance()
                String.format(
                    "%04d-%02d-%02d %02d:%02d:%02d",
                    now.get(Calendar.YEAR),
                    now.get(Calendar.MONTH) + 1,
                    now.get(Calendar.DAY_OF_MONTH),
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    now.get(Calendar.SECOND)
                )
            }

            val pulse = if (pulsePresent) {
                bb.short.toInt()
            } else null

            if (userPresent) bb.get()
            if (statusPresent) bb.short

            Measurement(
                timestamp = timestamp,
                systole = systolic,
                diastole = diastolic,
                map = mapVal,
                pulse = pulse,
                raw = data
            )
        } catch (e: Exception) {
            null
        }
    }
}
