package com.lotus.abs.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lotus.abs.ui.viewmodel.BleedViewModel

@Composable
fun BleedScreen(viewModel: BleedViewModel) {
    val state by viewModel.state.collectAsState()
    val terminalListState = rememberLazyListState()

    LaunchedEffect(state.terminalLog.size) {
        if (state.terminalLog.isNotEmpty()) {
            terminalListState.animateScrollToItem(state.terminalLog.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Bleed System", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Button(
                    onClick = { viewModel.sendCommand("30 49 01") }, 
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Read Valve Status (30 49 01)")
                }
            }
            item {
                BleedOperationCard("Front Left Wheel", "LF_DUMP (15s)", "LF_ISO (60s)", 
                    { viewModel.runBleedSequence("LF_DUMP", "30 11 07 01", "30 11 07 00", 15) },
                    { viewModel.runBleedSequence("LF_ISO", "30 12 07 01", "30 12 07 00", 60) },
                    state.activeOperation != null
                )
            }
            item {
                BleedOperationCard("Front Right Wheel", "RF_DUMP (15s)", "RF_ISO (60s)", 
                    { viewModel.runBleedSequence("RF_DUMP", "30 21 07 01", "30 21 07 00", 15) },
                    { viewModel.runBleedSequence("RF_ISO", "30 22 07 01", "30 22 07 00", 60) },
                    state.activeOperation != null
                )
            }
            item {
                BleedOperationCard("Rear Left Wheel", "LR_DUMP (15s)", "LR_ISO (60s)", 
                    { viewModel.runBleedSequence("LR_DUMP", "30 31 07 01", "30 31 07 00", 15) },
                    { viewModel.runBleedSequence("LR_ISO", "30 32 07 01", "30 32 07 00", 60) },
                    state.activeOperation != null
                )
            }
            item {
                BleedOperationCard("Rear Right Wheel", "RR_DUMP (15s)", "RR_ISO (60s)", 
                    { viewModel.runBleedSequence("RR_DUMP", "30 41 07 01", "30 41 07 00", 15) },
                    { viewModel.runBleedSequence("RR_ISO", "30 42 07 01", "30 42 07 00", 60) },
                    state.activeOperation != null
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.runBleedSequence("PUMP", "30 02 07 01", "30 02 07 00", 15) }, 
                        enabled = state.activeOperation == null, 
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pump Motor (15s)", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { viewModel.runBleedSequence("RELAY", "30 03 07 01", "30 03 07 00", 15) }, 
                        enabled = state.activeOperation == null, 
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Valve Relay (15s)", fontSize = 12.sp)
                    }
                }
            }
        }

        if (state.activeOperation != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), 
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(), 
                    horizontalArrangement = Arrangement.SpaceBetween, 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Running: ${state.activeOperation}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text("${state.timeRemainingSec}s", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().weight(0.5f).border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121212))
        ) {
            LazyColumn(state = terminalListState, modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(state.terminalLog, key = { it.id }) { entry ->
                    TerminalLine(entry)
                }
            }
        }
    }
}

@Composable
fun BleedOperationCard(title: String, btn1Text: String, btn2Text: String, onBtn1: () -> Unit, onBtn2: () -> Unit, disabled: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBtn1, enabled = !disabled, modifier = Modifier.weight(1f)) { 
                    Text(btn1Text, fontSize = 12.sp) 
                }
                Button(onClick = onBtn2, enabled = !disabled, modifier = Modifier.weight(1f)) { 
                    Text(btn2Text, fontSize = 12.sp) 
                }
            }
        }
    }
}
