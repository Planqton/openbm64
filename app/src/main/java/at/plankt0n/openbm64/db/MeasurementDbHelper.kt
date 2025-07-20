package at.plankt0n.openbm64.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class MeasurementDbHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE_NAME(" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "$COLUMN_TIMESTAMP TEXT," +
                    "$COLUMN_SYSTOLE INTEGER," +
                    "$COLUMN_DIASTOLE INTEGER," +
                    "$COLUMN_MAP REAL," +
                    "$COLUMN_PULSE INTEGER," +
                    "$COLUMN_RAW BLOB," +
                    "$COLUMN_INFO TEXT," +
                    "$COLUMN_INVALID INTEGER DEFAULT 0" +
                    ")"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_INFO TEXT")
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_INVALID INTEGER DEFAULT 0")
        }
    }

    fun insertMeasurement(values: Measurement) {
        val cv = ContentValues().apply {
            put(COLUMN_TIMESTAMP, values.timestamp)
            put(COLUMN_SYSTOLE, values.systole)
            put(COLUMN_DIASTOLE, values.diastole)
            put(COLUMN_MAP, values.map)
            values.pulse?.let { put(COLUMN_PULSE, it) }
            put(COLUMN_RAW, values.raw)
            values.info?.let { put(COLUMN_INFO, it) }
            put(COLUMN_INVALID, if (values.invalid) 1 else 0)
        }
        writableDatabase.insert(TABLE_NAME, null, cv)
    }

    fun insertMeasurementIfNotExists(values: Measurement): Boolean {
        val exists = readableDatabase.query(
            TABLE_NAME,
            arrayOf(COLUMN_ID),
            "${COLUMN_TIMESTAMP}=? AND ${COLUMN_SYSTOLE}=? AND ${COLUMN_DIASTOLE}=?",
            arrayOf(values.timestamp, values.systole.toString(), values.diastole.toString()),
            null,
            null,
            null
        ).use { it.moveToFirst() }
        if (exists) return false
        insertMeasurement(values)
        return true
    }

    fun getAll(): List<Measurement> {
        val list = mutableListOf<Measurement>()
        val c: Cursor = readableDatabase.query(TABLE_NAME, null, null, null, null, null, "$COLUMN_ID DESC")
        c.use {
            while (it.moveToNext()) {
                val m = Measurement(
                    id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                    timestamp = it.getString(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    systole = it.getInt(it.getColumnIndexOrThrow(COLUMN_SYSTOLE)),
                    diastole = it.getInt(it.getColumnIndexOrThrow(COLUMN_DIASTOLE)),
                    map = it.getDouble(it.getColumnIndexOrThrow(COLUMN_MAP)),
                    pulse = if (it.isNull(it.getColumnIndexOrThrow(COLUMN_PULSE))) null else it.getInt(it.getColumnIndexOrThrow(COLUMN_PULSE)),
                    raw = it.getBlob(it.getColumnIndexOrThrow(COLUMN_RAW)),
                    info = it.getString(it.getColumnIndexOrThrow(COLUMN_INFO)),
                    invalid = it.getInt(it.getColumnIndexOrThrow(COLUMN_INVALID)) != 0
                )
                list.add(m)
            }
        }
        return list
    }
    fun updateInfo(id: Long, info: String) {
        val cv = ContentValues().apply { put(COLUMN_INFO, info) }
        writableDatabase.update(TABLE_NAME, cv, "$COLUMN_ID=?", arrayOf(id.toString()))
    }

    fun updateInvalid(id: Long, invalid: Boolean) {
        val cv = ContentValues().apply { put(COLUMN_INVALID, if (invalid) 1 else 0) }
        writableDatabase.update(TABLE_NAME, cv, "$COLUMN_ID=?", arrayOf(id.toString()))
    }


    companion object {
        private const val DATABASE_VERSION = 2
        private const val DATABASE_NAME = "measurements.db"
        private const val TABLE_NAME = "measurements"

        private const val COLUMN_ID = "id"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_SYSTOLE = "systole"
        private const val COLUMN_DIASTOLE = "diastole"
        private const val COLUMN_MAP = "map"
        private const val COLUMN_PULSE = "pulse"
        private const val COLUMN_INFO = "info"
        private const val COLUMN_INVALID = "invalid"
        private const val COLUMN_RAW = "raw"
    }
}
