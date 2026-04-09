package org.fossify.phone.extensions

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Context.KEYGUARD_SERVICE
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.widget.Toast
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.telecomManager
import org.fossify.commons.helpers.KEY_PHONE
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.helpers.Config
import org.fossify.phone.models.SIMAccount

val Context.config: Config get() = Config.newInstance(applicationContext)

val Context.audioManager: AudioManager
    get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

val Context.powerManager: PowerManager
    get() = getSystemService(Context.POWER_SERVICE) as PowerManager

val Context.keyguardManager: KeyguardManager
    get() = getSystemService(KEYGUARD_SERVICE) as KeyguardManager

@SuppressLint("MissingPermission")
fun Context.getAvailableSIMCardLabels(): List<SIMAccount> {
    val simAccounts = mutableListOf<SIMAccount>()
    try {
        telecomManager.callCapablePhoneAccounts.forEachIndexed { index, account ->
            val phoneAccount = telecomManager.getPhoneAccount(account)
            var label = phoneAccount.label.toString()
            var address = phoneAccount.address.toString()
            if (address.startsWith("tel:") && address.substringAfter("tel:").isNotEmpty()) {
                address = Uri.decode(address.substringAfter("tel:"))
                label += " ($address)"
            }

            simAccounts.add(
                SIMAccount(
                    id = index + 1,
                    handle = phoneAccount.accountHandle,
                    label = label,
                    phoneNumber = address.substringAfter("tel:"),
                    color = phoneAccount.highlightColor
                )
            )
        }
    } catch (ignored: Exception) {
    }

    return simAccounts
}

@SuppressLint("MissingPermission")
fun Context.areMultipleSIMsAvailable(): Boolean {
    return try {
        telecomManager.callCapablePhoneAccounts.size > 1
    } catch (ignored: Exception) {
        false
    }
}

fun Context.clearMissedCalls() {
    ensureBackgroundThread {
        try {
            // notification cancellation triggers MissedCallNotifier.clearMissedCalls() which, in turn,
            // should update the database and reset the cached missed call count in MissedCallNotifier.java
            // https://android.googlesource.com/platform/packages/services/Telecomm/+/master/src/com/android/server/telecom/ui/MissedCallNotifierImpl.java#170
            telecomManager.cancelMissedCallsNotification()
        } catch (ignored: Exception) {
        }
    }
}

fun Context.canLaunchAccountsConfiguration(): Boolean {
    return Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)
        .resolveActivity(packageManager) != null
}

fun Context.launchAccountsConfiguration() {
    startActivity(Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS))
}

fun Activity.startAddContactIntent(phoneNumber: String) {
    val fossifyPackage = "org.fossify.contacts"

    val intent = Intent().apply {
        action = Intent.ACTION_INSERT_OR_EDIT
        type = "vnd.android.cursor.item/contact"
        putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
    }

    try {
        // Attempt to force Fossify Contacts
        val fossifyIntent = Intent(intent).apply {
            setPackage(fossifyPackage)
        }
        startActivity(fossifyIntent)
    } catch (e: Exception) {
        // Fallback: If Fossify is missing, let the system decide (Vivo app)
        try {
            intent.setPackage(null) // Clear explicit package to use default
            startActivity(intent)
        } catch (fallbackException: Exception) {
            Toast.makeText(this, "No contact app found", Toast.LENGTH_SHORT).show()
        }
    }
}
