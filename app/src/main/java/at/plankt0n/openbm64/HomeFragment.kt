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
import androidx.fragment.app.Fragment
import at.plankt0n.openbm64.db.BleParser
import java.util.UUID

class HomeFragment : Fragment() {

    private var gatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private val disconnectTimeout = 60_000L
    private val timeoutRunnable = Runnable { gatt?.disconnect() }
    private val deviceAddress = "A4:C1:38:A5:20:BB"
    private val serviceUuid = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")
    private val measUuid = UUID.fromString("00002a35-0000-1000-8000-00805f9b34fb")
    private val racpUuid = UUID.fromString("00002a52-0000-1000-8000-00805f9b34fb")
    private val cccUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private var descriptorsWritten = 0
    private val TAG = "HomeFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        view.findViewById<Button>(R.id.button_read).setOnClickListener { startReadingHistory() }
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(timeoutRunnable)
        gatt?.close()
    }

    private fun startReadingHistory() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device = adapter.getRemoteDevice(deviceAddress)
        gatt = device.connectGatt(requireContext(), false, gattCallback)
        handler.postDelayed(timeoutRunnable, disconnectTimeout)
        descriptorsWritten = 0
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        gatt.discoverServices()
                    } else {
                        Log.e(TAG, "Connection failed: $status")
                        gatt.close()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected with status $status")
                    gatt.close()
                }
                else -> if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "Error state $newState status $status")
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                gatt.disconnect()
                return
            }
            val service = gatt.getService(serviceUuid)
            val measChar = service?.getCharacteristic(measUuid)
            val racpChar = service?.getCharacteristic(racpUuid)
            if (measChar == null || racpChar == null) {
                Log.e(TAG, "Required characteristics not found")
                gatt.disconnect()
                return
            }
            gatt.setCharacteristicNotification(measChar, true)
            gatt.setCharacteristicNotification(racpChar, true)
            measChar.getDescriptor(cccUuid)?.let {
                it.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                gatt.writeDescriptor(it)
            }
            racpChar.getDescriptor(cccUuid)?.let {
                it.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                gatt.writeDescriptor(it)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                descriptorsWritten++
                if (descriptorsWritten == 2) {
                    requestAllRecords(gatt)
                }
            } else {
                Log.e(TAG, "Descriptor write failed: $status")
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
                    } else {
                        Log.e(TAG, "Failed to parse measurement")
                    }
                }
                racpUuid -> {
                    val data = characteristic.value
                    val hex = data.joinToString(" ") { String.format("%02X", it) }
                    Log.d(TAG, "RACP: $hex")
                    if (data.size >= 3 && data[0].toInt() == 0x06 && data[2].toInt() == 0x01) {
                        handler.removeCallbacks(timeoutRunnable)
                        gatt.disconnect()
                    }
                }
            }
        }

        private fun requestAllRecords(gatt: BluetoothGatt) {
            val charac = gatt.getService(serviceUuid)?.getCharacteristic(racpUuid) ?: return
            charac.value = byteArrayOf(0x01, 0x01)
            if (!gatt.writeCharacteristic(charac)) {
                Log.e(TAG, "Failed to write RACP")
            }
        }
    }
}
