package at.plankt0n.openbm64

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import at.plankt0n.openbm64.db.Measurement

class MeasurementAdapter(
    context: Context,
    measurements: List<Measurement>
) : ArrayAdapter<Measurement>(context, 0, measurements) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_measurement, parent, false)

        val item = getItem(position)!!
        view.findViewById<TextView>(R.id.cell_timestamp).text = item.timestamp
        view.findViewById<TextView>(R.id.cell_systole).text = item.systole.toString()
        view.findViewById<TextView>(R.id.cell_diastole).text = item.diastole.toString()
        view.findViewById<TextView>(R.id.cell_map).text = item.map.toString()
        view.findViewById<TextView>(R.id.cell_pulse).text = item.pulse?.toString() ?: ""
        val infoView = view.findViewById<TextView>(R.id.cell_info)
        if (item.info.isNullOrBlank()) {
            infoView.visibility = View.GONE
        } else {
            infoView.visibility = View.VISIBLE
            infoView.text = item.info
        }
        return view
    }
}
