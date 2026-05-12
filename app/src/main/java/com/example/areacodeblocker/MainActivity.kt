package com.example.areacodeblocker

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private lateinit var statusText: TextView
    private lateinit var areaCodeInput: TextInputEditText
    private lateinit var chipGroup: ChipGroup
    private lateinit var emptyText: TextView
    private lateinit var statToday: TextView
    private lateinit var stat24h: TextView
    private lateinit var stat30d: TextView
    private lateinit var stat1y: TextView
    private lateinit var notifSwitch: MaterialSwitch
    private lateinit var reverseSwitch: MaterialSwitch

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { updateAll() }

    private val contactsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { updateAll() }

    private val notificationsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { updateAll() }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) exportData(uri)
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importData(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText   = findViewById(R.id.statusText)
        areaCodeInput = findViewById(R.id.areaCodeInput)
        chipGroup    = findViewById(R.id.chipGroup)
        emptyText    = findViewById(R.id.emptyText)
        statToday    = findViewById(R.id.statToday)
        stat24h      = findViewById(R.id.stat24h)
        stat30d      = findViewById(R.id.stat30d)
        stat1y       = findViewById(R.id.stat1y)
        notifSwitch   = findViewById(R.id.notifSwitch)
        reverseSwitch = findViewById(R.id.reverseSwitch)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_permissions) { showPermissionsSheet(); true }
            else false
        }

        notifSwitch.isChecked = prefs.getBoolean(PREF_NOTIFICATIONS_ENABLED, true)
        notifSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(PREF_NOTIFICATIONS_ENABLED, checked).apply()
        }

        reverseSwitch.isChecked = prefs.getBoolean(PREF_REVERSE_MODE, false)
        reverseSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(PREF_REVERSE_MODE, checked).apply()
            updateStatus()
        }

        findViewById<MaterialButton>(R.id.addAreaCodeButton).setOnClickListener { addAreaCode() }
        findViewById<MaterialButton>(R.id.importButton).setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
        findViewById<MaterialButton>(R.id.exportButton).setOnClickListener {
            exportLauncher.launch("area_code_blocker_backup.json")
        }
    }

    override fun onResume() {
        super.onResume()
        updateAll()
    }

    private fun updateAll() {
        updateStatus()
        refreshAreaCodeList()
        updateStatistics()
    }

    private fun updateStatus() {
        val hasRole     = isRoleHeld()
        val hasContacts = hasPerm(Manifest.permission.READ_CONTACTS)
        val hasNotif    = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPerm(Manifest.permission.POST_NOTIFICATIONS)
        val allGranted  = hasRole && hasContacts && hasNotif
        val codes       = getAreaCodes()
        val reverse     = prefs.getBoolean(PREF_REVERSE_MODE, false)

        statusText.text = when {
            !allGranted          -> "⚠  Setup required — tap ⚙ to configure permissions."
            reverse && codes.isEmpty() ->
                "⚠  Allow-only mode is on but no area codes are set — all non-contact calls will be blocked."
            reverse              ->
                "🛡  Allow-only — permitting calls from ${codes.size} area code${if (codes.size == 1) "" else "s"} and contacts."
            codes.isEmpty()      -> "✅  Ready — add area codes above to start blocking."
            else                 ->
                "🛡  Active — blocking calls from ${codes.size} area code${if (codes.size == 1) "" else "s"}."
        }
    }

    private fun updateStatistics() {
        val timestamps = prefs.getStringSet(PREF_BLOCKED_TIMESTAMPS, emptySet()) ?: emptySet()
        val now = System.currentTimeMillis()

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        val ms24h = now - 24L  * 60 * 60 * 1000
        val ms30d = now - 30L  * 24 * 60 * 60 * 1000
        val ms1y  = now - 365L * 24 * 60 * 60 * 1000

        var today = 0; var h24 = 0; var d30 = 0; var y1 = 0
        for (ts in timestamps) {
            val t = ts.toLongOrNull() ?: continue
            if (t >= ms1y)      y1++
            if (t >= ms30d)     d30++
            if (t >= ms24h)     h24++
            if (t >= startOfDay) today++
        }

        statToday.text = today.toString()
        stat24h.text   = h24.toString()
        stat30d.text   = d30.toString()
        stat1y.text    = y1.toString()
    }

    private fun showPermissionsSheet() {
        val sheet = BottomSheetDialog(this)
        val view  = layoutInflater.inflate(R.layout.bottom_sheet_permissions, null)
        sheet.setContentView(view)

        val roleBtn     = view.findViewById<MaterialButton>(R.id.sheetRoleButton)
        val contactsBtn = view.findViewById<MaterialButton>(R.id.sheetContactsButton)
        val notifRow    = view.findViewById<View>(R.id.sheetNotifRow)
        val notifBtn    = view.findViewById<MaterialButton>(R.id.sheetNotifButton)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifRow.visibility = View.VISIBLE
        }

        fun refreshSheet() {
            val hasRole     = isRoleHeld()
            val hasContacts = hasPerm(Manifest.permission.READ_CONTACTS)
            roleBtn.isEnabled  = !hasRole
            roleBtn.text       = if (hasRole) "✓  Granted" else "Grant Role"
            contactsBtn.isEnabled = !hasContacts
            contactsBtn.text   = if (hasContacts) "✓  Granted" else "Grant Access"
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

    // ── Export / Import ──────────────────────────────────────────────────────

    private fun exportData(uri: Uri) {
        try {
            val codes      = getAreaCodes().sorted()
            val timestamps = prefs.getStringSet(PREF_BLOCKED_TIMESTAMPS, emptySet()) ?: emptySet()

            val json = JSONObject().apply {
                put("version", 1)
                put("areaCodes", JSONArray(codes))
                put("blockedTimestamps",
                    JSONArray(timestamps.mapNotNull { it.toLongOrNull() }.sorted()))
            }

            contentResolver.openOutputStream(uri)?.use { out ->
                out.bufferedWriter().use { it.write(json.toString(2)) }
            }
            Toast.makeText(this, "Exported ${codes.size} area code(s)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importData(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)
                ?.use { it.bufferedReader().readText() } ?: return
            if (text.trimStart().startsWith("{")) importJson(text) else importPlainText(text)
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importJson(text: String) {
        val json = JSONObject(text)

        val incomingCodes = mutableSetOf<String>()
        json.optJSONArray("areaCodes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.optString(i).trim().filter { it.isDigit() }
                if (c.length == 3) incomingCodes.add(c)
            }
        }

        val incomingTs = mutableSetOf<String>()
        val cutoff = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000
        json.optJSONArray("blockedTimestamps")?.let { arr ->
            for (i in 0 until arr.length()) {
                val t = arr.optLong(i, -1L)
                if (t >= cutoff) incomingTs.add(t.toString())
            }
        }

        val existingCodes = getAreaCodes().toMutableSet()
        val addedCodes    = incomingCodes.count { existingCodes.add(it) }
        saveAreaCodes(existingCodes)

        val existingTs = (prefs.getStringSet(PREF_BLOCKED_TIMESTAMPS, emptySet()) ?: emptySet()).toMutableSet()
        val addedTs    = incomingTs.count { existingTs.add(it) }
        prefs.edit().putStringSet(PREF_BLOCKED_TIMESTAMPS, existingTs).apply()

        refreshAreaCodeList(); updateStatus(); updateStatistics()
        Toast.makeText(this,
            "Imported $addedCodes area code(s), $addedTs block record(s)",
            Toast.LENGTH_SHORT).show()
    }

    private fun importPlainText(text: String) {
        val incoming = text.lines()
            .map { it.trim().filter { c -> c.isDigit() } }
            .filter { it.length == 3 }
            .toSet()
        if (incoming.isEmpty()) {
            Toast.makeText(this, "No valid area codes found in file", Toast.LENGTH_SHORT).show()
            return
        }
        val codes = getAreaCodes().toMutableSet()
        val added = incoming.count { codes.add(it) }
        saveAreaCodes(codes)
        refreshAreaCodeList(); updateStatus()
        Toast.makeText(this, "Imported $added new area code(s)", Toast.LENGTH_SHORT).show()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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
