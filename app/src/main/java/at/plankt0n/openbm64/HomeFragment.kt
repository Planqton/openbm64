package at.plankt0n.openbm64

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.os.Bundle
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
    private val deviceAddress = "A4:C1:38:A5:20:BB"
    private val serviceUuid = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")
    private val measUuid = UUID.fromString("00002a35-0000-1000-8000-00805f9b34fb")
    private val TAG = "HomeFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        view.findViewById<Button>(R.id.button_read).setOnClickListener { startReading() }
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        gatt?.close()
    }

    private fun startReading() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device = adapter.getRemoteDevice(deviceAddress)
        gatt = device.connectGatt(requireContext(), false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                gatt.discoverServices()
            } else {
                Log.e(TAG, "Connection failed: $status")
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                gatt.disconnect()
                return
            }
            val characteristic = gatt.getService(serviceUuid)?.getCharacteristic(measUuid)
            if (characteristic == null) {
                Log.e(TAG, "Measurement characteristic not found")
                gatt.disconnect()
                return
            }
            gatt.setCharacteristicNotification(characteristic, true)
            characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))?.let {
                it.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                gatt.writeDescriptor(it)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == measUuid) {
                val data = characteristic.value
                val m = BleParser.parseMeasurement(data)
                if (m != null) {
                    Log.i(TAG, "Measurement: $m")
                } else {
                    Log.e(TAG, "Failed to parse measurement")
                }
                gatt.disconnect()
            }
        }
    }
}
