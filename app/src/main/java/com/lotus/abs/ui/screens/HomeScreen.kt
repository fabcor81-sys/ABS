package com.lotus.abs.ui.screens

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
            Text(
                "Available Devices",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (state.pairedDevices.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        "No paired devices found",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                LazyColumn {
                    items(state.pairedDevices) { device ->
                        DeviceCard(
                            device = device,
                            onClick = { viewModel.connect(device) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        )
                    }
                }
            }
        } else {
            // Show connection info when connected
            EcuInfoCard(state)
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
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
                Button(
                    onClick = { viewModel.refreshDeviceList() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Refresh")
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(state: HomeViewModel.HomeState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
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
fun DeviceCard(device: android.bluetooth.BluetoothDevice, onClick: () -> Unit, modifier: Modifier) {
    Card(
        modifier = modifier
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surface),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                device.name ?: "Unknown",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Text(
                device.address,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EcuInfoCard(state: HomeViewModel.HomeState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
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
