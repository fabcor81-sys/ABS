package com.lotus.abs.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lotus.abs.data.entity.DTCRecord
import com.lotus.abs.ui.theme.LotusYellow
import com.lotus.abs.bluetooth.BluetoothManager
import com.lotus.abs.ui.viewmodel.DiagnosticsViewModel
import com.lotus.abs.ui.viewmodel.TerminalEntry
import com.lotus.abs.ui.viewmodel.TerminalEntryType

data class MacroCommand(val name: String, val command: String)

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel) {
    val state by viewModel.state.collectAsState()
    val terminalListState = rememberLazyListState()
    var confirmClear by remember { mutableStateOf(false) }
    val connected = state.connectionState == BluetoothManager.ConnectionState.CONNECTED

    // Auto-scroll terminal log to bottom when new entries arrive
    LaunchedEffect(state.terminalLog) {
        if (state.terminalLog.isNotEmpty()) {
            terminalListState.animateScrollToItem(state.terminalLog.size - 1)
        }
    }

    val macroCommands = listOf(
        MacroCommand("Leggi DTC", "18 02"),
        MacroCommand("Identificazione ECU", "1A 80")
    )

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Conferma cancellazione DTC") },
            text = { Text("Inviare il comando di cancellazione alla ECU ABS? La cronologia locale sarà eliminata solo dopo una risposta positiva confermata.") },
            confirmButton = {
                Button(onClick = { confirmClear = false; viewModel.clearAllDTCs() }) { Text("CANCELLA DTC") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annulla") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Diagnostica ABS / DTC",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Lettura, decodifica e cancellazione errori ECU",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            if (connected) "Adattatore collegato • terminale in sola lettura" else "Adattatore scollegato • connetti prima di inviare comandi",
            modifier = Modifier.padding(bottom = 10.dp),
            color = if (connected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            fontSize = 12.sp
        )
        state.lastActionMessage?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(message, modifier = Modifier.padding(12.dp),
                    color = if (state.lastActionSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    fontSize = 12.sp)
            }
        }

        // Section 1: Detected DTC Errors Card
        if (state.dtcList.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "DTC Warning",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Errori Rilevati (${state.dtcList.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = { confirmClear = true },
                            enabled = state.canClearDtc && !state.isSending,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Cancella DTC ECU", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.dtcList.forEach { record ->
                            DTCItemCard(record = record, onDelete = { viewModel.deleteDTC(record) })
                        }
                    }
                }
            }
        }

        if (connected && !state.canClearDtc) {
            Text("Il trasporto USB KKL attuale è in sola lettura: cancellazione DTC disabilitata.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp))
        }

        // Section 2: Terminal Window
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Terminal Log",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = { viewModel.clearTerminal() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Pulisci Log",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF080808)),
            shape = RoundedCornerShape(8.dp)
        ) {
            LazyColumn(
                state = terminalListState,
                modifier = Modifier.fillMaxSize().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.terminalLog, key = { it.id }) { entry ->
                    TerminalLine(entry)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Macro Action Chips
        Text(
            "Comandi diagnostici di lettura",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            items(macroCommands) { macro ->
                Card(
                    modifier = Modifier
                        .clickable(enabled = connected && !state.isSending) { viewModel.sendCommand(macro.command) }
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(macro.command, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(macro.name, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Custom Command Input Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = state.customCommand,
                onValueChange = { viewModel.updateCustomCommand(it) },
                placeholder = {
                    Text("Solo lettura (es. 18 02)...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { if (connected && !state.isSending) viewModel.sendCustomCommand() }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { viewModel.sendCustomCommand() },
                enabled = connected && !state.isSending && state.customCommand.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(52.dp)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Invia")
            }
        }
    }
}

@Composable
fun DTCItemCard(record: DTCRecord, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFFDC2626),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = record.code,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "Stato: ${record.status}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = record.description,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Rimuovi dalla cronologia locale",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TerminalLine(entry: TerminalEntry) {
    val textColor = when (entry.type) {
        TerminalEntryType.SENT -> Color(0xFFFF9800)       // Orange/Yellow
        TerminalEntryType.RECEIVED -> Color(0xFF00E676)   // Green
        TerminalEntryType.SYSTEM -> Color(0xFFBDBDBD)     // Light Grey
        TerminalEntryType.ERROR -> Color(0xFFE53935)      // Red
    }

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "[${entry.timestamp}] ",
            color = Color(0xFF666666),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = entry.text,
            color = textColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (entry.type == TerminalEntryType.SENT) FontWeight.Bold else FontWeight.Normal
        )
    }
}
