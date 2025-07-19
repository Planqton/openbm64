package at.plankt0n.openbm64.util

import android.content.Context
import at.plankt0n.openbm64.db.Measurement
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ExcelExporter {
    private const val FILE_NAME = "log.xlsx"
    private val HEADERS = listOf("Zeitstempel", "Systole", "Diastole", "MAP", "Puls")

    fun appendMeasurement(context: Context, m: Measurement) {
        val file = File(context.getExternalFilesDir(null), FILE_NAME)
        val workbook: Workbook
        val sheet: org.apache.poi.ss.usermodel.Sheet
        if (file.exists()) {
            FileInputStream(file).use { fis ->
                workbook = XSSFWorkbook(fis)
            }
            sheet = workbook.getSheetAt(0)
        } else {
            workbook = XSSFWorkbook()
            sheet = workbook.createSheet("Messwerte")
            val headerRow = sheet.createRow(0)
            HEADERS.forEachIndexed { index, title ->
                headerRow.createCell(index).setCellValue(title)
            }
        }

        val row = sheet.createRow(sheet.lastRowNum + 1)
        row.createCell(0).setCellValue(m.timestamp)
        row.createCell(1).setCellValue(m.systole.toDouble())
        row.createCell(2).setCellValue(m.diastole.toDouble())
        row.createCell(3).setCellValue(m.map)
        m.pulse?.let { row.createCell(4).setCellValue(it.toDouble()) }

        FileOutputStream(file).use { fos ->
            workbook.write(fos)
        }
        workbook.close()
    }
}
