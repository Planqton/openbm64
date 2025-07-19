package at.plankt0n.openbm64

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.util.Log
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import at.plankt0n.openbm64.util.ExcelExporter
import at.plankt0n.openbm64.db.BleParser
import at.plankt0n.openbm64.db.MeasurementDbHelper

class HomeFragment : Fragment() {

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var lastSyncText: TextView
    private var gatt: BluetoothGatt? = null
    private lateinit var dbHelper: MeasurementDbHelper
    private val TAG = "HomeFragment"
    private val requestCode = 1002
    private var deviceAddress: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val retryDelayMs = 5000L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressBar = view.findViewById(R.id.progress_wait)
        statusText = view.findViewById(R.id.text_status)
        lastSyncText = view.findViewById(R.id.text_last_sync)
        dbHelper = MeasurementDbHelper(requireContext())

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val last = prefs.getString("last_sync", null)
        last?.let {
            lastSyncText.text = getString(R.string.last_successful_sync, it)
        }
        checkPermissionAndStart()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        gatt?.close()
    }

    private fun checkPermissionAndStart() {
        val required = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            required.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), requestCode)
        } else {
            startListening()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startListening()
        }
    }

    private fun startListening() {
        statusText.text = getString(R.string.waiting_for_bm64)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        var address = findFirstBm64Address()
        if (address == null) {
            address = prefs.getString("device_address", null)
        } else {
            prefs.edit().putString("device_address", address).apply()
        }
        if (address == null) {
            handler.postDelayed({ startListening() }, retryDelayMs)
            return
        }
        deviceAddress = address
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device = adapter.getRemoteDevice(address)
        gatt = device.connectGatt(requireContext(), false, gattCallback)
    }

    private fun findFirstBm64Address(): String? {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        val device = adapter.bondedDevices.firstOrNull { it.name.equals("BM64", true) }
        return device?.address
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                gatt.close()
                statusText.post { statusText.text = getString(R.string.waiting_for_bm64) }
                handler.postDelayed({ startListening() }, retryDelayMs)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(java.util.UUID.fromString("00001810-0000-1000-8000-00805f9b34fb"))
                val measChar = service?.getCharacteristic(java.util.UUID.fromString("00002a35-0000-1000-8000-00805f9b34fb"))
                val racpChar = service?.getCharacteristic(java.util.UUID.fromString("00002a52-0000-1000-8000-00805f9b34fb"))

                if (measChar != null && racpChar != null) {
                    // Enable indications on Blood Pressure Measurement
                    gatt.setCharacteristicNotification(measChar, true)
                    measChar.getDescriptor(java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))?.let {
                        it.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        gatt.writeDescriptor(it)
                    }

                    // Enable indications on Record Access Control Point
                    gatt.setCharacteristicNotification(racpChar, true)
                    racpChar.getDescriptor(java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))?.let {
                        it.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        gatt.writeDescriptor(it)
                    }

                    // Request all stored records
                    racpChar.value = byteArrayOf(0x01, 0x01)
                    gatt.writeCharacteristic(racpChar)

                    statusText.post {
                        statusText.text = getString(R.string.syncing_bm64)
                    }
                    return
                }
            }
            gatt.close()
            statusText.post { statusText.text = getString(R.string.waiting_for_bm64) }
            handler.postDelayed({ startListening() }, retryDelayMs)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            when (characteristic.uuid.toString().lowercase()) {
                "00002a35-0000-1000-8000-00805f9b34fb" -> {
                    val data = characteristic.value
                    val m = BleParser.parseMeasurement(data)
                    m?.let {
                        Log.d(TAG, "Fetched blood pressure: ${'$'}{it.systole}/${'$'}{it.diastole} mmHg")
                        dbHelper.insertMeasurement(it)
                        ExcelExporter.appendMeasurement(requireContext(), it)
                    }
                }
                "00002a52-0000-1000-8000-00805f9b34fb" -> {
                    val data = characteristic.value
                    if (data.isNotEmpty() && data[0].toInt() == 0x06) {
                        val ts = SimpleDateFormat("dd.MM.yy HH:mm:ss", Locale.getDefault()).format(Date())
                        val prefs = PreferenceManager.getDefaultSharedPreferences(this@HomeFragment.requireContext())
                        prefs.edit().putString("last_sync", ts).apply()
                        lastSyncText.post {
                            lastSyncText.text = getString(R.string.last_successful_sync, ts)
                        }
                        statusText.post { statusText.text = getString(R.string.waiting_for_bm64) }
                        handler.postDelayed({ startListening() }, retryDelayMs)
                    }
                }
            }
        }
    }
}
