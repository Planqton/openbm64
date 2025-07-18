package at.plankt0n.openbm64

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import at.plankt0n.openbm64.db.BleParser
import at.plankt0n.openbm64.db.MeasurementDbHelper

class HomeFragment : Fragment() {

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private var gatt: BluetoothGatt? = null
    private lateinit var dbHelper: MeasurementDbHelper

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
        dbHelper = MeasurementDbHelper(requireContext())
        startListening()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        gatt?.close()
    }

    private fun startListening() {
        statusText.text = getString(R.string.waiting_for_bm64)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        var address = prefs.getString("device_address", null)
        if (address == null) {
            address = findFirstBm64Address()?.also {
                prefs.edit().putString("device_address", it).apply()
            }
        }
        if (address == null) return
        statusText.text = getString(R.string.loading_data_from, address)
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
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.services.firstOrNull { it.uuid.toString().equals("00001810-0000-1000-8000-00805f9b34fb", true) }
            val characteristic = service?.getCharacteristic(java.util.UUID.fromString("00002a35-0000-1000-8000-00805f9b34fb"))
            if (characteristic != null) {
                gatt.setCharacteristicNotification(characteristic, true)
                characteristic.descriptors.firstOrNull()?.let { desc ->
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(desc)
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid.toString().equals("00002a35-0000-1000-8000-00805f9b34fb", true)) {
                val data = characteristic.value
                val m = BleParser.parseMeasurement(data)
                m?.let { dbHelper.insertMeasurement(it) }
            }
        }
    }
}
