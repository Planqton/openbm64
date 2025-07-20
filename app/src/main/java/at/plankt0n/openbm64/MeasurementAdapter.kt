package at.plankt0n.openbm64

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import at.plankt0n.openbm64.db.Measurement

class MeasurementAdapter(
    private val items: MutableList<Measurement>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<MeasurementAdapter.ViewHolder>() {

    class ViewHolder(view: View, onClick: (Int) -> Unit) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.root_layout)
        val timestamp: TextView = view.findViewById(R.id.cell_timestamp)
        val systole: TextView = view.findViewById(R.id.cell_systole)
        val diastole: TextView = view.findViewById(R.id.cell_diastole)
        val map: TextView = view.findViewById(R.id.cell_map)
        val pulse: TextView = view.findViewById(R.id.cell_pulse)
        val info: TextView = view.findViewById(R.id.cell_info)
        init {
            view.setOnClickListener { onClick(adapterPosition) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_measurement, parent, false)
        return ViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.timestamp.text = item.timestamp
        holder.systole.text = item.systole.toString()
        holder.diastole.text = item.diastole.toString()
        holder.map.text = item.map.toString()
        holder.pulse.text = item.pulse?.toString() ?: ""
        if (item.info.isNullOrBlank()) {
            holder.info.visibility = View.GONE
        } else {
            holder.info.visibility = View.VISIBLE
            holder.info.text = item.info
        }
        val ctx = holder.itemView.context
        if (item.invalid) {
            holder.root.setBackgroundColor(
                ContextCompat.getColor(ctx, R.color.invalid_bg)
            )
        } else {
            holder.root.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    override fun getItemCount(): Int = items.size
}
