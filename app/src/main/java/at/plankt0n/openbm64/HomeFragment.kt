package at.plankt0n.openbm64

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import at.plankt0n.openbm64.util.Bm64Fetcher

class HomeFragment : Fragment(), Bm64Fetcher.Listener {

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var lastSyncText: TextView
    private val requestCode = 1002
    private lateinit var fetcher: Bm64Fetcher

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
        fetcher = Bm64Fetcher(requireContext(), this)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val last = prefs.getString("last_sync", null)
        last?.let {
            lastSyncText.text = getString(R.string.last_successful_sync, it)
        }
        checkPermissionAndStart()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fetcher.stop()
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
            fetcher.start()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            fetcher.start()
        }
    }


    override fun onStatus(resId: Int) {
        statusText.post { statusText.text = getString(resId) }
    }

    override fun onSyncComplete(timestamp: String) {
        lastSyncText.post {
            lastSyncText.text = getString(R.string.last_successful_sync, timestamp)
        }
    }
}
