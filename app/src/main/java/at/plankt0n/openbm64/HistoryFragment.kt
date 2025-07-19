package at.plankt0n.openbm64

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import at.plankt0n.openbm64.db.Measurement
import java.io.File

class HistoryFragment : Fragment() {

    private fun parseCsvLine(line: String): Measurement? {
        val parts = line.split(",")
        if (parts.size < 4) return null
        val pulse = parts.getOrNull(4)?.takeIf { it.isNotEmpty() }?.toIntOrNull()
        val raw = if (parts.size > 5) {
            parts[5].chunked(2).mapNotNull {
                it.toIntOrNull(16)?.toByte()
            }.toByteArray()
        } else ByteArray(0)
        return Measurement(
            timestamp = parts[0],
            systole = parts[1].toIntOrNull() ?: return null,
            diastole = parts[2].toIntOrNull() ?: return null,
            map = parts[3].toDoubleOrNull() ?: return null,
            pulse = pulse,
            raw = raw
        )
    }

    private fun loadMeasurements(): List<Measurement> {
        val p = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val list = mutableListOf<Measurement>()
        if (p.getBoolean(SettingsFragment.KEY_SAVE_EXTERNAL, false)) {
            val dirUri = p.getString(SettingsFragment.KEY_DIR, null)
            if (dirUri != null) {
                val dir = DocumentFile.fromTreeUri(requireContext(), Uri.parse(dirUri))
                val file = dir?.findFile("measurements.csv")
                file?.uri?.let { uri ->
                    requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
                        lines.mapNotNull { parseCsvLine(it) }.forEach { list.add(it) }
                    }
                }
            }
        } else {
            val file = File(requireContext().filesDir, "measurements.csv")
            if (file.exists()) {
                file.bufferedReader().useLines { lines ->
                    lines.mapNotNull { parseCsvLine(it) }.forEach { list.add(it) }
                }
            }
        }
        return list
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_history, container, false)
        val listView: ListView = view.findViewById(R.id.list_history)
        val measurements = loadMeasurements()
        listView.adapter = MeasurementAdapter(requireContext(), measurements)
        return view
    }
}

