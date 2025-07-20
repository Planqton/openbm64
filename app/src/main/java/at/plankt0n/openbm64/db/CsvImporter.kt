package at.plankt0n.openbm64.db

import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

object CsvImporter {
    private const val TAG = "CsvImporter"

    fun importFromFile(file: File, db: MeasurementDbHelper) {
        if (!file.exists()) return
        file.bufferedReader().use { reader ->
            reader.forEachLine { line ->
                parseLine(line)?.let { db.insertMeasurementIfNotExists(it) }
            }
        }
    }

    fun importFromStream(stream: InputStream, db: MeasurementDbHelper) {
        BufferedReader(InputStreamReader(stream)).use { reader ->
            reader.forEachLine { line ->
                parseLine(line)?.let { db.insertMeasurementIfNotExists(it) }
            }
        }
    }

    fun importFromDocument(context: Context, uri: Uri, db: MeasurementDbHelper) {
        val doc = DocumentFile.fromTreeUri(context, uri) ?: return
        val file = doc.findFile("measurements.csv") ?: return
        context.contentResolver.openInputStream(file.uri)?.use { importFromStream(it, db) }
    }

    private fun parseLine(line: String): Measurement? {
        val parts = line.trim().split(',')
        if (parts.size < 6) return null
        return try {
            val timestamp = parts[0]
            val systole = parts[1].toInt()
            val diastole = parts[2].toInt()
            val map = parts[3].toDouble()
            val pulseStr = parts[4]
            val pulse = if (pulseStr.isBlank()) null else pulseStr.toInt()
            val (invalid, rawHex) = if (parts.size >= 7) {
                val invalidFlag = parts[5].trim()
                val inv = invalidFlag == "1" || invalidFlag.equals("true", ignoreCase = true)
                inv to parts[6]
            } else {
                false to parts[5]
            }
            val raw = hexToBytes(rawHex)
            Measurement(
                timestamp = timestamp,
                systole = systole,
                diastole = diastole,
                map = map,
                pulse = pulse,
                raw = raw,
                invalid = invalid
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse line: $line", e)
            null
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val data = ByteArray(hex.length / 2)
        var i = 0
        while (i < hex.length) {
            val byte = hex.substring(i, i + 2).toInt(16).toByte()
            data[i / 2] = byte
            i += 2
        }
        return data
    }
}
