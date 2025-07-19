package at.plankt0n.openbm64

import android.content.Context
import java.io.File

object StorageHelper {
    fun internalCsvFile(context: Context): File {
        val base = context.externalMediaDirs.firstOrNull() ?: context.filesDir
        val dir = File(base, "openbm64/log")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "measurements.csv")
    }
}
