package com.ivy.receiptscanner.notification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which apps' notifications BankNotificationListenerService should read.
 *
 * You find an app's package name via Settings -> Apps -> [the bank app] ->
 * look at the URL bar if you view it on the Play Store
 * (play.google.com/store/apps/details?id=THIS_IS_THE_PACKAGE_NAME), or by
 * running `adb shell pm list packages | grep -i bankname`.
 *
 * Examples: Chase is "com.chase.sig.android", Bank of America is
 * "com.infonow.bofa", Capital One is "com.konylabs.capitalone". Verify
 * yours — these vary and change over time.
 */
@Singleton
class NotificationListenerConfigStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val enabledKey = booleanPreferencesKey("receipt_scanner_notification_listener_enabled")
    private val packagesKey = stringSetPreferencesKey("receipt_scanner_notification_listener_packages")

    suspend fun isEnabled(): Boolean = dataStore.data.first()[enabledKey] ?: false

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
    }

    suspend fun getAllowedPackages(): Set<String> = dataStore.data.first()[packagesKey] ?: emptySet()

    suspend fun addPackage(packageName: String) {
        val trimmed = packageName.trim()
        if (trimmed.isBlank()) return
        dataStore.edit { prefs ->
            prefs[packagesKey] = (prefs[packagesKey] ?: emptySet()) + trimmed
        }
    }

    suspend fun removePackage(packageName: String) {
        dataStore.edit { prefs ->
            prefs[packagesKey] = (prefs[packagesKey] ?: emptySet()) - packageName
        }
    }
}
