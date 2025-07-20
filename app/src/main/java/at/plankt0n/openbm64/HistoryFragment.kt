package at.plankt0n.openbm64

import android.app.AlertDialog
import android.widget.EditText
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import at.plankt0n.openbm64.db.Measurement
import at.plankt0n.openbm64.db.MeasurementDbHelper

class HistoryFragment : Fragment() {

    private lateinit var dbHelper: MeasurementDbHelper
    private lateinit var measurements: MutableList<Measurement>
    private lateinit var adapter: MeasurementAdapter

    private fun loadMeasurements(): MutableList<Measurement> {
        dbHelper = MeasurementDbHelper(requireContext())
        return dbHelper.getAll().toMutableList()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_history, container, false)
        val listView: ListView = view.findViewById(R.id.list_history)
        val countView: TextView = view.findViewById(R.id.text_count)
        measurements = loadMeasurements()
        countView.text = getString(R.string.history_count, measurements.size)
        adapter = MeasurementAdapter(requireContext(), measurements)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val m = measurements[position]
            val edit = EditText(requireContext()).apply { setText(m.info ?: "") }
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.edit_info)
                .setView(edit)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    m.info = edit.text.toString()
                    dbHelper.updateInfo(m.id, m.info ?: "")
                    adapter.notifyDataSetChanged()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::dbHelper.isInitialized) {
            dbHelper.close()
        }
    }
}

