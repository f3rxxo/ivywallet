package com.ivy.receiptscanner.notification

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Lets the user pick which apps' notifications to read (e.g. 3 banking
 * apps) and turn the feature on/off. Notification access itself has to be
 * granted manually in system settings — Android doesn't allow requesting
 * it via a normal runtime permission dialog, so this screen deep-links
 * there instead.
 */
@Composable
fun NotificationListenerSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationListenerSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var newPackageText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Auto-detect from notifications", style = MaterialTheme.typography.headlineSmall)

        Text(
            "Reads notifications ONLY from the apps you add below — everything " +
                "else on your phone is ignored. When a transaction-looking " +
                "notification comes in, you'll get a tap-to-review prompt; " +
                "nothing is ever saved automatically.",
            style = MaterialTheme.typography.bodySmall
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = state.enabled,
                onCheckedChange = { viewModel.setEnabled(it) }
            )
            Spacer(Modifier.width(8.dp))
            Text("Enabled")
        }

        OutlinedButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }) {
            Text("Grant notification access in system settings")
        }
        Text(
            "Required once — find \"Ivy Wallet receipt scanner\" in that list " +
                "and turn it on. Android doesn't let apps request this permission " +
                "directly.",
            style = MaterialTheme.typography.bodySmall
        )

        HorizontalDivider()

        Text("Apps to listen to", style = MaterialTheme.typography.titleMedium)
        Text(
            "Add the package name of each banking/payment app, e.g. " +
                "com.chase.sig.android. Find it via the app's Play Store URL " +
                "(...details?id=PACKAGE_NAME) or Settings -> Apps -> [app] -> " +
                "Advanced -> \"App details in store\".",
            style = MaterialTheme.typography.bodySmall
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newPackageText,
                onValueChange = { newPackageText = it },
                label = { Text("Package name") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (newPackageText.isNotBlank()) {
                    viewModel.addPackage(newPackageText.trim())
                    newPackageText = ""
                }
            }) {
                Text("Add")
            }
        }

        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(state.packages) { pkg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(pkg, style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { viewModel.removePackage(pkg) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove")
                    }
                }
            }
        }

        TextButton(onClick = onBack) {
            Text("Back")
        }
    }
}
