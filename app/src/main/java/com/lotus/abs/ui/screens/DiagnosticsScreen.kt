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
        MacroCommand("Read DTC", "18 02"),
        MacroCommand("Clear DTC", "14 FF FF 00")
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
                "Command & Log",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = { viewModel.clearTerminal() }) {
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
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF080808)),
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
            "Quick Commands",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            items(macroCommands) { macro ->
                Card(
                    modifier = Modifier
                        .clickable { viewModel.sendCommand(macro.command) }
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(macro.command, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
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
                    Text("Type command (e.g. 1A80)...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { viewModel.sendCustomCommand() }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { viewModel.sendCustomCommand() },
                enabled = state.customCommand.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(56.dp)
            ) {
                Text("Send", fontWeight = FontWeight.Bold)
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
