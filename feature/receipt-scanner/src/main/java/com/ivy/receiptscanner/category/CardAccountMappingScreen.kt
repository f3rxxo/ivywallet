package com.ivy.receiptscanner.category

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Lets the user teach the scanner which card/account a receipt or
 * notification belongs to, e.g. "0502" (last 4 digits) or "SAVOR"
 * (card nickname) -> their "Capital One Savor" account in Ivy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardAccountMappingScreen(
    onBack: () -> Unit,
    viewModel: CardAccountMappingViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var matchText by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Card / account matching", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Add the last 4 digits of a card (as it appears on receipts/" +
                "notifications, e.g. \"0502\") or a nickname/bank name " +
                "(e.g. \"SAVOR\"), and pick which account it should map to. " +
                "Scanned receipts and notification screenshots will then " +
                "auto-select that account.",
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            value = matchText,
            onValueChange = { matchText = it },
            label = { Text("Last 4 digits or nickname") },
            modifier = Modifier.fillMaxWidth()
        )

        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { dropdownExpanded = it }
        ) {
            OutlinedTextField(
                value = state.accounts.firstOrNull { it.id.value.toString() == selectedAccountId }
                    ?.name?.value ?: "Select account",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                label = { Text("Account") }
            )
            ExposedDropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }
            ) {
                state.accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.name.value) },
                        onClick = {
                            selectedAccountId = account.id.value.toString()
                            dropdownExpanded = false
                        }
                    )
                }
            }
        }

        Button(
            onClick = {
                val accId = selectedAccountId
                if (matchText.isNotBlank() && accId != null) {
                    viewModel.addMapping(matchText, accId)
                    matchText = ""
                    selectedAccountId = null
                }
            }
        ) {
            Text("Add mapping")
        }

        HorizontalDivider()

        Text("Saved mappings", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(state.mappings) { mapping ->
                val accountName = state.accounts
                    .firstOrNull { it.id.value.toString() == mapping.accountId }
                    ?.name?.value ?: "(deleted account)"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${mapping.matchText} -> $accountName")
                    IconButton(onClick = { viewModel.removeMapping(mapping.matchText) }) {
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
