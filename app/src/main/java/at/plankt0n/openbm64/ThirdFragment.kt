package at.plankt0n.openbm64

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.fragment.app.Fragment
import at.plankt0n.openbm64.db.MeasurementDbHelper

class ThirdFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_third, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val listView = view.findViewById<ListView>(R.id.list_measurements)
        val db = MeasurementDbHelper(requireContext())
        val items = db.getAll().map { m ->
            "${m.timestamp}: ${m.systole}/${m.diastole} mmHg (MAP ${m.map})" + (m.pulse?.let { ", Puls $it" } ?: "")
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items)
        listView.adapter = adapter
    }
}
