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
import at.plankt0n.openbm64.BuildConfig
import at.plankt0n.openbm64.db.MeasurementDbHelper
import at.plankt0n.openbm64.StorageHelper

class SettingsFragment : Fragment() {

    private lateinit var prefs: SharedPreferences
    private lateinit var saveExternalSwitch: Switch
    private lateinit var externalLayout: View
    private lateinit var externalPath: TextView
    private lateinit var aboutLayout: View
    private lateinit var versionText: TextView
    private var updatingSwitch = false
    private var pendingEnable = false

    private val openDirectory =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                requireContext().contentResolver.takePersistableUriPermission(uri, flags)

                val previous = prefs.getString(KEY_DIR, null)
                prefs.edit().putString(KEY_DIR, uri.toString()).apply()
                updateExternalPath(uri)

                if (pendingEnable) {
                    moveFileToExternal(uri)
                    prefs.edit().putBoolean(KEY_SAVE_EXTERNAL, true).apply()
                    externalLayout.isVisible = true
                    setSwitchChecked(true)
                    pendingEnable = false
                } else if (prefs.getBoolean(KEY_SAVE_EXTERNAL, false) && previous != null && previous != uri.toString()) {
                    moveExternalFile(Uri.parse(previous), uri)
                }
            } else if (pendingEnable) {
                // user cancelled folder selection
                setSwitchChecked(false)
                pendingEnable = false
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
        aboutLayout = view.findViewById(R.id.layout_about)
        versionText = view.findViewById(R.id.text_version)

        versionText.text = getString(R.string.version_format, BuildConfig.VERSION_NAME)
        aboutLayout.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Planqton/openbm64"))
            startActivity(intent)
        }

        val dir = prefs.getString(KEY_DIR, null)
        val enabledPref = prefs.getBoolean(KEY_SAVE_EXTERNAL, false)
        val enabled = enabledPref && dir != null
        if (enabled != enabledPref) {
            prefs.edit().putBoolean(KEY_SAVE_EXTERNAL, enabled).apply()
        }
        saveExternalSwitch.isChecked = enabled
        externalLayout.isVisible = enabled

        dir?.let { updateExternalPath(Uri.parse(it)) }

        saveExternalSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (updatingSwitch) return@setOnCheckedChangeListener
            if (isChecked) confirmEnableExternal() else confirmDisableExternal()
        }

        view.findViewById<Button>(R.id.button_choose_folder).setOnClickListener {
            openDirectory.launch(null)
        }

        view.findViewById<Button>(R.id.button_clear_db).setOnClickListener {
            confirmAndClearDatabase()
        }

        return view
    }

    private fun confirmEnableExternal() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.confirm_switch_external)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val existing = prefs.getString(KEY_DIR, null)
                if (existing == null) {
                    pendingEnable = true
                    openDirectory.launch(null)
                } else {
                    moveFileToExternal(Uri.parse(existing))
                    prefs.edit().putBoolean(KEY_SAVE_EXTERNAL, true).apply()
                    externalLayout.isVisible = true
                    setSwitchChecked(true)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                setSwitchChecked(false)
            }
            .show()
    }

    private fun confirmDisableExternal() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.confirm_switch_internal)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.getString(KEY_DIR, null)?.let { moveFileToInternal(Uri.parse(it)) }
                prefs.edit().putBoolean(KEY_SAVE_EXTERNAL, false).apply()
                externalLayout.isVisible = false
                setSwitchChecked(false)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                setSwitchChecked(true)
            }
            .show()
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
        StorageHelper.internalCsvFile(requireContext()).delete()
        val dirUri = prefs.getString(KEY_DIR, null)
        if (dirUri != null) {
            val dir = DocumentFile.fromTreeUri(requireContext(), Uri.parse(dirUri)) ?: return
            dir.findFile("measurements.csv")?.delete()
        }
    }

    private fun updateExternalPath(uri: Uri) {
        externalPath.text = uri.path
    }

    private fun setSwitchChecked(checked: Boolean) {
        updatingSwitch = true
        saveExternalSwitch.isChecked = checked
        updatingSwitch = false
    }

    private fun moveFileToExternal(uri: Uri) {
        val internal = StorageHelper.internalCsvFile(requireContext())
        if (!internal.exists()) return
        val dir = DocumentFile.fromTreeUri(requireContext(), uri) ?: return
        var file = dir.findFile("measurements.csv")
        if (file == null) {
            file = dir.createFile("text/csv", "measurements.csv")
        }
        file?.uri?.let { dest ->
            requireContext().contentResolver.openOutputStream(dest, "w")?.use { out ->
                internal.inputStream().use { it.copyTo(out) }
            }
            internal.delete()
        }
    }

    private fun moveFileToInternal(uri: Uri) {
        val internal = StorageHelper.internalCsvFile(requireContext())
        val dir = DocumentFile.fromTreeUri(requireContext(), uri) ?: return
        val file = dir.findFile("measurements.csv") ?: return
        requireContext().contentResolver.openInputStream(file.uri)?.use { input ->
            internal.outputStream().use { output -> input.copyTo(output) }
        }
        file.delete()
    }

    private fun moveExternalFile(fromUri: Uri, toUri: Uri) {
        val fromDir = DocumentFile.fromTreeUri(requireContext(), fromUri) ?: return
        val toDir = DocumentFile.fromTreeUri(requireContext(), toUri) ?: return
        val src = fromDir.findFile("measurements.csv") ?: return
        var dst = toDir.findFile("measurements.csv")
        if (dst == null) {
            dst = toDir.createFile("text/csv", "measurements.csv")
        }
        dst?.let { dest ->
            requireContext().contentResolver.openInputStream(src.uri)?.use { input ->
                requireContext().contentResolver.openOutputStream(dest.uri, "w")?.use { output ->
                    input.copyTo(output)
                }
            }
            src.delete()
        }
    }

    companion object {
        const val KEY_SAVE_EXTERNAL = "save_external"
        const val KEY_DIR = "external_dir"
    }
}

