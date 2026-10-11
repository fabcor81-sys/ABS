package com.lotus.abs.ui.screens

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lotus.abs.R
import com.lotus.abs.bluetooth.BluetoothManager
import com.lotus.abs.ui.viewmodel.ConnectionScreenState
import com.lotus.abs.ui.viewmodel.ConnectionViewModel

@Composable
fun ConnectionScreen(viewModel: ConnectionViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // App Title / Navigation Header
        if (state.connectionStatus != BluetoothManager.ConnectionState.DISCONNECTED) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                IconButton(onClick = { viewModel.disconnect() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    "Back to Scanned Devices",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { viewModel.disconnect() }
                        .padding(start = 4.dp)
                )
            }
        }

        // Connection Status Card
        ConnectionStatusCard(state)
        Spacer(modifier = Modifier.height(16.dp))

        // Device List
        if (state.connectionStatus == BluetoothManager.ConnectionState.DISCONNECTED) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    "Available Devices",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )

                if (state.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            val pairedAddresses = state.pairedDevices.map { it.address }.toSet()
            val unpairScannedDevices = state.scannedDevices.filter { it.address !in pairedAddresses }

            if (state.pairedDevices.isEmpty() && unpairScannedDevices.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        if (state.isScanning) "Scanning for nearby BLE & Bluetooth devices..." else "No devices found. Tap 'Scan for Devices' below.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    if (unpairScannedDevices.isNotEmpty()) {
                        item {
                            Text(
                                "Discovered BLE Devices",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(unpairScannedDevices) { device ->
                            DeviceCard(
                                device = device,
                                badgeText = "BLE",
                                onClick = { viewModel.connect(device) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }

                    if (state.pairedDevices.isNotEmpty()) {
                        item {
                            Text(
                                "Paired Devices",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(state.pairedDevices) { device ->
                            DeviceCard(
                                device = device,
                                badgeText = "Paired",
                                onClick = { viewModel.connect(device) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Show connection info when connected or connecting
            EcuInfoCard(state)
            Spacer(modifier = Modifier.weight(1f))
        }

        // Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.connectionStatus == BluetoothManager.ConnectionState.CONNECTED) {
                Button(
                    onClick = { viewModel.disconnect() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect", fontWeight = FontWeight.Bold)
                }
            } else if (state.connectionStatus == BluetoothManager.ConnectionState.ERROR) {
                Button(
                    onClick = { viewModel.disconnect() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text("Back to Scanned Devices")
                }
            } else {
                if (state.isScanning) {
                    Button(
                        onClick = { viewModel.stopScan() },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Text("Stop Scan", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { viewModel.refreshDeviceList() },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Text("Scan for Devices", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(state: ConnectionScreenState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            // Icon Background Circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF1E293B), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ELM327 BLE",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = when (state.connectionStatus) {
                        BluetoothManager.ConnectionState.CONNECTED -> MaterialTheme.colorScheme.tertiary
                        BluetoothManager.ConnectionState.CONNECTING -> MaterialTheme.colorScheme.primary
                        BluetoothManager.ConnectionState.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    
                    val statusText = when (state.connectionStatus) {
                        BluetoothManager.ConnectionState.CONNECTED -> "Connected"
                        BluetoothManager.ConnectionState.CONNECTING -> "Connecting..."
                        BluetoothManager.ConnectionState.ERROR -> "Error"
                        else -> "Disconnected"
                    }

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(statusColor, RoundedCornerShape(5.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(statusText, fontSize = 14.sp, color = statusColor, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: BluetoothDevice,
    badgeText: String,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Card(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.name ?: "Unnamed Device",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    device.address,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun EcuInfoCard(state: ConnectionScreenState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Target ECU",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            Text("Device: ${state.selectedDevice?.name ?: "Unknown"}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Text("Address: ${state.selectedDevice?.address ?: "N/A"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Protocol: KWP2000", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Baud: 10400", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state.ecuInfo, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.tertiary)
        }
    }
}
