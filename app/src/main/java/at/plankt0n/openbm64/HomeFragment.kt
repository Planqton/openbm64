package at.plankt0n.openbm64

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import at.plankt0n.openbm64.db.BleParser
import at.plankt0n.openbm64.db.MeasurementDbHelper
import java.util.UUID

class HomeFragment : Fragment() {

    private var gatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private val disconnectTimeout = 60_000L
    private val timeoutRunnable = Runnable {
        appendLog("Timeout - disconnecting")
        gatt?.disconnect()
    }
    private val deviceAddress = "A4:C1:38:A5:20:BB"
    private val serviceUuid = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")
    private val measUuid = UUID.fromString("00002a35-0000-1000-8000-00805f9b34fb")
    private val cccUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val TAG = "HomeFragment"
    private var logView: TextView? = null
    private var dbHelper: MeasurementDbHelper? = null

    private fun appendLog(text: String) {
        activity?.runOnUiThread {
            val tv = logView ?: return@runOnUiThread
            tv.append(text + "\n")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        view.findViewById<Button>(R.id.button_read).setOnClickListener { startReadingHistory() }
        dbHelper = MeasurementDbHelper(requireContext())
        logView = view.findViewById(R.id.text_log)
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(timeoutRunnable)
        gatt?.close()
        logView = null
        dbHelper = null
    }

    private fun startReadingHistory() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device = adapter.getRemoteDevice(deviceAddress)
        logView?.text = ""
        appendLog("Connecting...")
        gatt = device.connectGatt(requireContext(), false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        appendLog("Connected")
                        gatt.discoverServices()
                    } else {
                        Log.e(TAG, "Connection failed: $status")
                        appendLog("Connection failed: $status")
                        gatt.close()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected with status $status")
                    appendLog("Disconnected")
                    handler.removeCallbacks(timeoutRunnable)
                    gatt.close()
                }
                else -> if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "Error state $newState status $status")
                    appendLog("Error: $status")
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                appendLog("Service discovery failed")
                gatt.disconnect()
                return
            }
            val service = gatt.getService(serviceUuid)
            val measChar = service?.getCharacteristic(measUuid)
            if (measChar == null) {
                Log.e(TAG, "Measurement characteristic not found")
                appendLog("Characteristic missing")
                gatt.disconnect()
                return
            }
            gatt.setCharacteristicNotification(measChar, true)
            measChar.getDescriptor(cccUuid)?.let {
                it.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                gatt.writeDescriptor(it)
            } ?: run {
                appendLog("Descriptor missing")
                gatt.disconnect()
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                appendLog("Notifications enabled")
                handler.postDelayed(timeoutRunnable, disconnectTimeout)
            } else {
                Log.e(TAG, "Descriptor write failed: $status")
                appendLog("Descriptor error: $status")
                gatt.disconnect()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            when (characteristic.uuid) {
                measUuid -> {
                    val data = characteristic.value
                    val hex = data.joinToString(" ") { String.format("%02X", it) }
                    Log.d(TAG, "Record: $hex")
                    val m = BleParser.parseMeasurement(data)
                    if (m != null) {
                        Log.i(TAG, "Measurement: $m")
                        dbHelper?.insertMeasurement(m)
                        appendLog(m.toString())
                    } else {
                        Log.e(TAG, "Failed to parse measurement")
                        appendLog("Failed to parse measurement")
                    }
                }
            }
        }
    }
}
