package at.plankt0n.openbm64.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import at.plankt0n.openbm64.R
import at.plankt0n.openbm64.db.BleParser
import at.plankt0n.openbm64.db.Measurement
import at.plankt0n.openbm64.db.MeasurementDbHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Bm64Fetcher(
    private val context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onStatus(resId: Int)
        fun onSyncComplete(timestamp: String)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val retryDelayMs = 5000L
    private var gatt: BluetoothGatt? = null
    private val dbHelper = MeasurementDbHelper(context)
    private var deviceAddress: String? = null
    private val TAG = "Bm64Fetcher"

    fun start() {
        listener.onStatus(R.string.waiting_for_bm64)
        connect()
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        gatt?.close()
        gatt = null
    }

    private fun connect() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        var address = findFirstBm64Address()
        if (address == null) {
            address = prefs.getString("device_address", null)
        } else {
            prefs.edit().putString("device_address", address).apply()
        }
        if (address == null) {
            handler.postDelayed({ connect() }, retryDelayMs)
            return
        }
        deviceAddress = address
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device = adapter.getRemoteDevice(address)
        gatt = device.connectGatt(context, false, gattCallback)
    }

    private fun findFirstBm64Address(): String? {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        val device = adapter.bondedDevices.firstOrNull { it.name.equals("BM64", true) }
        return device?.address
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                g.close()
                listener.onStatus(R.string.waiting_for_bm64)
                handler.postDelayed({ connect() }, retryDelayMs)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = g.getService(java.util.UUID.fromString("00001810-0000-1000-8000-00805f9b34fb"))
                val measChar = service?.getCharacteristic(java.util.UUID.fromString("00002a35-0000-1000-8000-00805f9b34fb"))
                val racpChar = service?.getCharacteristic(java.util.UUID.fromString("00002a52-0000-1000-8000-00805f9b34fb"))

                if (measChar != null && racpChar != null) {
                    g.setCharacteristicNotification(measChar, true)
                    measChar.getDescriptor(java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))?.let {
                        it.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        g.writeDescriptor(it)
                    }

                    g.setCharacteristicNotification(racpChar, true)
                    racpChar.getDescriptor(java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))?.let {
                        it.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        g.writeDescriptor(it)
                    }

                    racpChar.value = byteArrayOf(0x01, 0x01)
                    g.writeCharacteristic(racpChar)

                    listener.onStatus(R.string.syncing_bm64)
                    return
                }
            }
            g.close()
            listener.onStatus(R.string.waiting_for_bm64)
            handler.postDelayed({ connect() }, retryDelayMs)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            when (characteristic.uuid.toString().lowercase()) {
                "00002a35-0000-1000-8000-00805f9b34fb" -> {
                    val data = characteristic.value
                    val m = BleParser.parseMeasurement(data)
                    m?.let {
                        Log.d(TAG, "Fetched blood pressure: ${'$'}{it.systole}/${'$'}{it.diastole} mmHg")
                        dbHelper.insertMeasurement(it)
                        ExcelExporter.appendMeasurement(context, it)
                    }
                }
                "00002a52-0000-1000-8000-00805f9b34fb" -> {
                    val data = characteristic.value
                    if (data.isNotEmpty() && data[0].toInt() == 0x06) {
                        val ts = SimpleDateFormat("dd.MM.yy HH:mm:ss", Locale.getDefault()).format(Date())
                        PreferenceManager.getDefaultSharedPreferences(context)
                            .edit().putString("last_sync", ts).apply()
                        listener.onSyncComplete(ts)
                        listener.onStatus(R.string.waiting_for_bm64)
                        handler.postDelayed({ connect() }, retryDelayMs)
                    }
                }
            }
        }
    }
}
