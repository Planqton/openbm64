package at.plankt0n.openbm64.db

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

object CsvImporter {
    private const val TAG = "CsvImporter"

    fun readFromFile(file: File): MutableList<Measurement> {
        if (!file.exists()) return mutableListOf()
        file.bufferedReader().use { reader ->
            return readFromReader(reader)
        }
    }

    fun readFromStream(stream: InputStream): MutableList<Measurement> {
        return readFromReader(BufferedReader(InputStreamReader(stream)))
    }

    fun readFromDocument(context: Context, uri: Uri): MutableList<Measurement> {
        val list = mutableListOf<Measurement>()
        val doc = DocumentFile.fromTreeUri(context, uri) ?: return list
        val file = doc.findFile("measurements.csv") ?: return list
        context.contentResolver.openInputStream(file.uri)?.use { input ->
            list.addAll(readFromStream(input))
        }
        return list
    }

    fun existsInFile(file: File, m: Measurement): Boolean {
        if (!file.exists()) return false
        var found = false
        file.forEachLine { line ->
            if (!found) {
                parseLine(line)?.let {
                    if (it.timestamp == m.timestamp && it.systole == m.systole && it.diastole == m.diastole) {
                        found = true
                    }
                }
            }
        }
        return found
    }

    fun appendToFile(file: File, m: Measurement) {
        file.appendText(measurementToLine(m))
    }

    fun appendToDocument(context: Context, uri: Uri, m: Measurement) {
        val doc = DocumentFile.fromTreeUri(context, uri) ?: return
        var file = doc.findFile("measurements.csv")
        if (file == null) {
            file = doc.createFile("text/csv", "measurements.csv")
        }
        file?.uri?.let { u ->
            context.contentResolver.openOutputStream(u, "wa")?.use { out ->
                out.write(measurementToLine(m).toByteArray())
            }
        }
    }

    fun writeFile(file: File, list: List<Measurement>) {
        file.writeText(list.joinToString(separator = "") { measurementToLine(it) })
    }

    fun writeDocument(context: Context, uri: Uri, list: List<Measurement>) {
        val doc = DocumentFile.fromTreeUri(context, uri) ?: return
        var file = doc.findFile("measurements.csv")
        if (file == null) {
            file = doc.createFile("text/csv", "measurements.csv")
        }
        file?.uri?.let { u ->
            context.contentResolver.openOutputStream(u, "w")?.use { out ->
                list.forEach { out.write(measurementToLine(it).toByteArray()) }
            }
        }
    }

    private fun readFromReader(reader: BufferedReader): MutableList<Measurement> {
        val list = mutableListOf<Measurement>()
        reader.forEachLine { line ->
            parseLine(line)?.let { list.add(it) }
        }
        return list
    }

    private fun measurementToLine(m: Measurement): String {
        val rawHex = m.raw.joinToString("") { String.format("%02X", it) }
        return "${m.timestamp},${m.systole},${m.diastole},${m.map},${m.pulse ?: ""},${m.invalid},$rawHex\n"
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
