package at.plankt0n.openbm64

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import at.plankt0n.openbm64.db.MeasurementDbHelper

class SettingsFragment : Fragment() {

    private lateinit var prefs: SharedPreferences
    private lateinit var saveExternalSwitch: Switch
    private lateinit var externalLayout: View
    private lateinit var externalPath: TextView

    private val openDirectory =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                requireContext().contentResolver.takePersistableUriPermission(uri, flags)
                prefs.edit().putString(KEY_DIR, uri.toString()).apply()
                updateExternalPath(uri)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)

        saveExternalSwitch = view.findViewById(R.id.switch_save_external)
        externalLayout = view.findViewById(R.id.layout_external)
        externalPath = view.findViewById(R.id.text_external_path)

        saveExternalSwitch.isChecked = prefs.getBoolean(KEY_SAVE_EXTERNAL, false)
        externalLayout.isVisible = saveExternalSwitch.isChecked

        prefs.getString(KEY_DIR, null)?.let { updateExternalPath(Uri.parse(it)) }

        saveExternalSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_SAVE_EXTERNAL, isChecked).apply()
            externalLayout.isVisible = isChecked
            if (isChecked && prefs.getString(KEY_DIR, null) == null) {
                openDirectory.launch(null)
            }
        }

        view.findViewById<Button>(R.id.button_choose_folder).setOnClickListener {
            openDirectory.launch(null)
        }

        view.findViewById<Button>(R.id.button_clear_db).setOnClickListener {
            confirmAndClearDatabase()
        }

        return view
    }

    private fun confirmAndClearDatabase() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.confirm_delete)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                MeasurementDbHelper(requireContext()).writableDatabase.use {
                    it.delete("measurements", null, null)
                }
                deleteCsv()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteCsv() {
        val dirUri = prefs.getString(KEY_DIR, null) ?: return
        val dir = DocumentFile.fromTreeUri(requireContext(), Uri.parse(dirUri)) ?: return
        dir.findFile("measurements.csv")?.delete()
    }

    private fun updateExternalPath(uri: Uri) {
        externalPath.text = uri.path
    }

    companion object {
        const val KEY_SAVE_EXTERNAL = "save_external"
        const val KEY_DIR = "external_dir"
    }
}
