package com.lotus.abs.ui.screens

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.lotus.abs.ui.viewmodel.HomeState
import com.lotus.abs.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header
        Text(
            stringResource(R.string.app_name),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                if (state.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            val pairedAddresses = state.pairedDevices.map { it.address }.toSet()
            val unpairScannedDevices = state.scannedDevices.filter { it.address !in pairedAddresses }

            if (state.pairedDevices.isEmpty() && unpairScannedDevices.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        if (state.isScanning) "Scanning for nearby BLE & Bluetooth devices..." else "No devices found. Tap 'Scan for Devices' below.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurface
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
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
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
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
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
            // Show connection info when connected
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
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.disconnect))
                }
            } else {
                if (state.isScanning) {
                    Button(
                        onClick = { viewModel.stopScan() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Stop Scan")
                    }
                } else {
                    Button(
                        onClick = { viewModel.refreshDeviceList() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Scan for Devices")
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(state: HomeState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.connection_status),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                val statusColor = when (state.connectionStatus) {
                    BluetoothManager.ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                    BluetoothManager.ConnectionState.CONNECTING -> Color(0xFFFFC107)
                    BluetoothManager.ConnectionState.ERROR -> Color(0xFFF44336)
                    else -> Color(0xFF9E9E9E)
                }

                val statusText = when (state.connectionStatus) {
                    BluetoothManager.ConnectionState.CONNECTED -> stringResource(R.string.connected)
                    BluetoothManager.ConnectionState.CONNECTING -> stringResource(R.string.scanning)
                    BluetoothManager.ConnectionState.ERROR -> "Error"
                    else -> stringResource(R.string.disconnected)
                }

                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(statusColor, shape = RoundedCornerShape(6.dp))
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(statusText, fontSize = 12.sp, color = statusColor)
            }

            if (state.connectionStatus == BluetoothManager.ConnectionState.CONNECTED) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    "Battery: ${String.format("%.2f", state.batteryVoltage)}V",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
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
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    device.name ?: "Unnamed Device",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    device.address,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun EcuInfoCard(state: HomeState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                stringResource(R.string.device_info),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Device: ${state.selectedDevice?.name ?: "Unknown"}", fontSize = 14.sp)
            Text("Address: ${state.selectedDevice?.address ?: "N/A"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state.ecuInfo, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
