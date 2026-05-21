package com.example.areacodeblocker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

const val PREFS_NAME = "area_code_prefs"
const val PREF_AREA_CODES = "area_codes"
const val PREF_BLOCKED_TIMESTAMPS = "blocked_timestamps"
const val PREF_NOTIFICATIONS_ENABLED = "notifications_enabled"
const val PREF_REVERSE_MODE = "reverse_mode"
const val PREF_BLOCKING_ENABLED = "blocking_enabled"

class AreaCodeScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "AreaCodeBlocker"
        private const val CHANNEL_ID = "blocked_calls"
        private const val NOTIF_ID = 2
        private const val ONE_YEAR_MS = 365L * 24 * 60 * 60 * 1000
    }

    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondAllow(callDetails)
            return
        }

        val rawNumber = callDetails.handle?.schemeSpecificPart
        if (rawNumber.isNullOrBlank()) {
            respondAllow(callDetails)
            return
        }

        val digits = rawNumber.filter { it.isDigit() }
        Log.d(TAG, "Incoming: $rawNumber")

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_BLOCKING_ENABLED, true)) {
            Log.d(TAG, "Blocking is paused, allowing call")
            respondAllow(callDetails)
            return
        }

        val areaCodes = getAreaCodes()
        val reverseMode = prefs.getBoolean(PREF_REVERSE_MODE, false)
        val areaCode = extractAreaCode(digits)

        val shouldBlock = if (reverseMode) {
            // Block unless: area code is in the allow-list, in contacts, or unrecognized format
            areaCode != null && areaCode !in areaCodes && !isInContacts(rawNumber)
        } else {
            // Block if area code is in the blocked list and not in contacts
            areaCode != null && areaCode in areaCodes && !isInContacts(rawNumber)
        }

        if (!shouldBlock) {
            Log.d(TAG, "Allowing call")
            respondAllow(callDetails)
            return
        }

        Log.d(TAG, "Rejecting call silently")
        recordBlock()
        notifyBlocked(rawNumber)
        respondReject(callDetails)
    }

    private fun recordBlock() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val cutoff = now - ONE_YEAR_MS
        val existing = prefs.getStringSet(PREF_BLOCKED_TIMESTAMPS, emptySet()) ?: emptySet()
        val updated = existing.filterTo(mutableSetOf()) { (it.toLongOrNull() ?: 0L) >= cutoff }
        updated.add(now.toString())
        prefs.edit().putStringSet(PREF_BLOCKED_TIMESTAMPS, updated).apply()
    }

    private fun notifyBlocked(number: String) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_NOTIFICATIONS_ENABLED, true)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Blocked calls", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call blocked")
            .setContentText("Rejected call from $number")
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification)
    }

    private fun getAreaCodes(): Set<String> =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getStringSet(PREF_AREA_CODES, emptySet()) ?: emptySet()

    private fun extractAreaCode(digits: String): String? = when {
        digits.length == 11 && digits.startsWith("1") -> digits.substring(1, 4)
        digits.length == 10 -> digits.substring(0, 3)
        else -> null
    }

    private fun isInContacts(phoneNumber: String): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return true

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        return try {
            contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID),
                null, null, null)?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Contacts lookup failed", e)
            true
        }
    }

    private fun respondAllow(callDetails: Call.Details) =
        respondToCall(callDetails, CallResponse.Builder()
            .setDisallowCall(false).setRejectCall(false)
            .setSilenceCall(false).setSkipCallLog(false).setSkipNotification(false)
            .build())

    private fun respondReject(callDetails: Call.Details) =
        respondToCall(callDetails, CallResponse.Builder()
            .setDisallowCall(true).setRejectCall(true)
            .setSilenceCall(true).setSkipCallLog(false).setSkipNotification(true)
            .build())
}
