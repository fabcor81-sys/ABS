package com.lotus.abs.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lotus.abs.ui.viewmodel.LiveDataViewModel

@Composable
fun LiveDataScreen(viewModel: LiveDataViewModel) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(
                "Live Data / Info", 
                fontSize = 24.sp, 
                fontWeight = FontWeight.Bold, 
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { viewModel.togglePolling() }) {
                Text(if (state.isPolling) "Stop Polling" else "Start Polling")
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { DataCard("ECU Identification", state.ecuId) }
            item { DataCard("Design Code", state.designCode) }
            item { DataCard("Hardware Version", state.hwVersion) }
            item { DataCard("Software Version", state.swVersion) }
            item { DataCard("Battery Voltage", state.batteryVoltage) }
            item { DataCard("Front Left Wheel Speed", state.speedFL) }
            item { DataCard("Front Right Wheel Speed", state.speedFR) }
            item { DataCard("Rear Left Wheel Speed", state.speedRL) }
            item { DataCard("Rear Right Wheel Speed", state.speedRR) }
        }
    }
}

@Composable
fun DataCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceBetween, 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}
