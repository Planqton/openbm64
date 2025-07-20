package at.plankt0n.openbm64

import android.app.AlertDialog
import android.widget.EditText
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
        val listView: RecyclerView = view.findViewById(R.id.list_history)
        val countView: TextView = view.findViewById(R.id.text_count)
        measurements = loadMeasurements()
        countView.text = getString(R.string.history_count, measurements.size)
        adapter = MeasurementAdapter(measurements) { position ->
            val m = measurements[position]
            val edit = EditText(requireContext()).apply { setText(m.info ?: "") }
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.edit_info)
                .setView(edit)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    m.info = edit.text.toString()
                    dbHelper.updateInfo(m.id, m.info ?: "")
                    adapter.notifyItemChanged(position)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        listView.layoutManager = LinearLayoutManager(requireContext())
        listView.adapter = adapter

        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                val m = measurements[pos]
                m.invalid = !m.invalid
                dbHelper.updateInvalid(m.id, m.invalid)
                adapter.notifyItemChanged(pos)
            }
        })
        helper.attachToRecyclerView(listView)

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::dbHelper.isInitialized) {
            dbHelper.close()
        }
    }
}

