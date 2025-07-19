package at.plankt0n.openbm64

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.fragment.app.Fragment
import at.plankt0n.openbm64.db.MeasurementDbHelper

class HistoryFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_history, container, false)
        val listView: ListView = view.findViewById(R.id.list_history)
        val dbHelper = MeasurementDbHelper(requireContext())
        val measurements = dbHelper.getAll()
        listView.adapter = MeasurementAdapter(requireContext(), measurements)
        return view
    }
}
