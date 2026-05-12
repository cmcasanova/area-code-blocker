package com.example.areacodeblocker

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText

class MainActivity : ComponentActivity() {

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private lateinit var statusText: TextView
    private lateinit var areaCodeInput: TextInputEditText
    private lateinit var chipGroup: ChipGroup
    private lateinit var emptyText: TextView

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { updateAll() }

    private val contactsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { updateAll() }

    private val notificationsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { updateAll() }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri != null) exportAreaCodes(uri)
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importAreaCodes(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        areaCodeInput = findViewById(R.id.areaCodeInput)
        chipGroup = findViewById(R.id.chipGroup)
        emptyText = findViewById(R.id.emptyText)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_permissions) { showPermissionsSheet(); true }
            else false
        }

        findViewById<MaterialButton>(R.id.addAreaCodeButton).setOnClickListener { addAreaCode() }
        findViewById<MaterialButton>(R.id.importButton).setOnClickListener {
            importLauncher.launch(arrayOf("text/plain", "*/*"))
        }
        findViewById<MaterialButton>(R.id.exportButton).setOnClickListener {
            exportLauncher.launch("area_codes.txt")
        }
    }

    override fun onResume() {
        super.onResume()
        updateAll()
    }

    private fun updateAll() {
        updateStatus()
        refreshAreaCodeList()
    }

    private fun updateStatus() {
        val hasRole = isRoleHeld()
        val hasContacts = hasPerm(Manifest.permission.READ_CONTACTS)
        val hasNotif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPerm(Manifest.permission.POST_NOTIFICATIONS)
        val allGranted = hasRole && hasContacts && hasNotif
        val codes = getAreaCodes()

        statusText.text = when {
            !allGranted -> "⚠  Setup required — tap ⚙ to configure permissions."
            codes.isEmpty() -> "✅  Ready — add area codes above to start blocking."
            else -> "🛡  Active — blocking calls from ${codes.size} area code${if (codes.size == 1) "" else "s"}."
        }
    }

    private fun showPermissionsSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_permissions, null)
        sheet.setContentView(view)

        val roleBtn = view.findViewById<MaterialButton>(R.id.sheetRoleButton)
        val contactsBtn = view.findViewById<MaterialButton>(R.id.sheetContactsButton)
        val notifRow = view.findViewById<View>(R.id.sheetNotifRow)
        val notifBtn = view.findViewById<MaterialButton>(R.id.sheetNotifButton)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifRow.visibility = View.VISIBLE
        }

        fun refreshSheet() {
            val hasRole = isRoleHeld()
            val hasContacts = hasPerm(Manifest.permission.READ_CONTACTS)
            roleBtn.isEnabled = !hasRole
            roleBtn.text = if (hasRole) "✓  Granted" else "Grant Role"
            contactsBtn.isEnabled = !hasContacts
            contactsBtn.text = if (hasContacts) "✓  Granted" else "Grant Access"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasNotif = hasPerm(Manifest.permission.POST_NOTIFICATIONS)
                notifBtn.isEnabled = !hasNotif
                notifBtn.text = if (hasNotif) "✓  Granted" else "Grant Permission"
            }
        }

        refreshSheet()

        roleBtn.setOnClickListener { sheet.dismiss(); requestRole() }
        contactsBtn.setOnClickListener {
            sheet.dismiss()
            contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
        notifBtn.setOnClickListener {
            sheet.dismiss()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        sheet.show()
    }

    private fun addAreaCode() {
        val code = areaCodeInput.text.toString().trim().filter { it.isDigit() }
        if (code.length != 3) {
            Toast.makeText(this, "Enter a 3-digit area code", Toast.LENGTH_SHORT).show()
            return
        }
        val codes = getAreaCodes().toMutableSet()
        if (!codes.add(code)) {
            Toast.makeText(this, "$code is already in the list", Toast.LENGTH_SHORT).show()
            return
        }
        saveAreaCodes(codes)
        areaCodeInput.text?.clear()
        refreshAreaCodeList()
        updateStatus()
    }

    private fun removeAreaCode(code: String) {
        saveAreaCodes(getAreaCodes().toMutableSet().also { it.remove(code) })
        refreshAreaCodeList()
        updateStatus()
    }

    private fun refreshAreaCodeList() {
        chipGroup.removeAllViews()
        val codes = getAreaCodes().sorted()
        if (codes.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            chipGroup.visibility = View.GONE
            return
        }
        emptyText.visibility = View.GONE
        chipGroup.visibility = View.VISIBLE
        for (code in codes) {
            chipGroup.addView(Chip(this).apply {
                text = code
                isCloseIconVisible = true
                setOnCloseIconClickListener { removeAreaCode(code) }
            })
        }
    }

    private fun exportAreaCodes(uri: Uri) {
        val codes = getAreaCodes().sorted()
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.bufferedWriter().use { w -> codes.forEach { w.appendLine(it) } }
            }
            Toast.makeText(this, "Exported ${codes.size} area code(s)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importAreaCodes(uri: Uri) {
        try {
            val incoming = contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readLines()
                    .map { it.trim().filter { c -> c.isDigit() } }
                    .filter { it.length == 3 }
                    .toSet()
            } ?: emptySet()
            if (incoming.isEmpty()) {
                Toast.makeText(this, "No valid area codes found in file", Toast.LENGTH_SHORT).show()
                return
            }
            val codes = getAreaCodes().toMutableSet()
            val added = incoming.count { codes.add(it) }
            saveAreaCodes(codes)
            refreshAreaCodeList()
            updateStatus()
            Toast.makeText(this, "Imported $added new area code(s)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getAreaCodes(): Set<String> =
        prefs.getStringSet(PREF_AREA_CODES, emptySet()) ?: emptySet()

    private fun saveAreaCodes(codes: Set<String>) =
        prefs.edit().putStringSet(PREF_AREA_CODES, codes).apply()

    private fun hasPerm(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun isRoleHeld(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return (getSystemService(Context.ROLE_SERVICE) as RoleManager)
            .isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    private fun requestRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, "Requires Android 10+", Toast.LENGTH_LONG).show()
            return
        }
        val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING))
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        else
            Toast.makeText(this, "Call screening unavailable on this device", Toast.LENGTH_LONG).show()
    }
}
