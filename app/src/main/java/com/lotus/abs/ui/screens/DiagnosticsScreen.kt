package com.lotus.abs.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lotus.abs.ui.viewmodel.DiagnosticsViewModel
import com.lotus.abs.ui.viewmodel.TerminalEntry
import com.lotus.abs.ui.viewmodel.TerminalEntryType

data class MacroCommand(val name: String, val command: String)

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel) {
    val state by viewModel.state.collectAsState()
    val terminalListState = rememberLazyListState()

    // Auto-scroll terminal log to bottom when new entries arrive
    LaunchedEffect(state.terminalLog.size) {
        if (state.terminalLog.isNotEmpty()) {
            terminalListState.animateScrollToItem(state.terminalLog.size - 1)
        }
    }

    val macroCommands = listOf(
        MacroCommand("Reset ELM", "AT Z"),
        MacroCommand("Echo Off", "AT E0"),
        MacroCommand("Protocol KWP2000", "AT SP 6"),
        MacroCommand("Lotus ABS Header", "AT SH 81 28 F1"),
        MacroCommand("Read Voltage", "AT RV"),
        MacroCommand("Adapter Info", "ATI"),
        MacroCommand("Read DTC", "19 02 AF"),
        MacroCommand("Clear DTC", "14 FF FF 00"),
        MacroCommand("Read Sensors", "22 02 01")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Text(
                "Terminal & Diagnostics",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { viewModel.clearTerminal() }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear Terminal",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Terminal Console Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF121212)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (state.terminalLog.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Console empty. Send a command or tap a macro below.",
                        color = Color(0xFF666666),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                LazyColumn(
                    state = terminalListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.terminalLog, key = { it.id }) { entry ->
                        TerminalLine(entry)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Macro Action Chips
        Text(
            "Quick Macros",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            items(macroCommands) { macro ->
                AssistChip(
                    onClick = { viewModel.sendCommand(macro.command) },
                    label = {
                        Text(
                            macro.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
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
                    Text("Enter command (e.g. AT Z, 19 02)...", fontSize = 13.sp)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { viewModel.sendCustomCommand() }
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { viewModel.sendCustomCommand() },
                enabled = state.customCommand.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (state.customCommand.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Command",
                    tint = if (state.customCommand.isNotBlank()) MaterialTheme.colorScheme.onPrimary else Color.Gray
                )
            }
        }
    }
}

@Composable
fun TerminalLine(entry: TerminalEntry) {
    val textColor = when (entry.type) {
        TerminalEntryType.SENT -> Color(0xFF80CBC4)       // Cyan/Teal
        TerminalEntryType.RECEIVED -> Color(0xFFA5D6A7)   // Light Green
        TerminalEntryType.SYSTEM -> Color(0xFFFFCC80)     // Amber/Orange
        TerminalEntryType.ERROR -> Color(0xFFEF9A9A)      // Light Red
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
