package com.lotus.abs.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lotus.abs.R
import com.lotus.abs.ui.theme.LotusYellow
import com.lotus.abs.ui.viewmodel.BleedViewModel
import com.lotus.abs.ui.viewmodel.WheelLocation

@Composable
fun BleedScreen(viewModel: BleedViewModel) {
    val state by viewModel.state.collectAsState()
    val terminalListState = rememberLazyListState()

    LaunchedEffect(state.terminalLog) {
        if (state.terminalLog.isNotEmpty()) {
            terminalListState.animateScrollToItem(state.terminalLog.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Service / Bleed",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Active operation banner with Emergency Stop
        if (state.activeOperation != null) {
            EmergencyActiveBanner(
                activeOperation = state.activeOperation!!,
                timeRemainingSec = state.timeRemainingSec,
                onEmergencyStop = { viewModel.emergencyStop() }
            )
        }

        // Car Layout Diagram Box with 4 Wheel Buttons
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            // Lotus Exige S2 Top Down Image
            Image(
                painter = painterResource(id = R.drawable.exige_s2_top),
                contentDescription = "Lotus Exige S2 Top Down View",
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(0.66f)
            )

            // Wheel Selection Overlay Buttons anchored to 4 corners
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Front Left (LF) & Front Right (RF)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    WheelButton(
                        wheel = WheelLocation.FRONT_LEFT,
                        onClick = { viewModel.selectWheel(WheelLocation.FRONT_LEFT) }
                    )
                    WheelButton(
                        wheel = WheelLocation.FRONT_RIGHT,
                        onClick = { viewModel.selectWheel(WheelLocation.FRONT_RIGHT) }
                    )
                }

                // Bottom Row: Rear Left (LR) & Rear Right (RR)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    WheelButton(
                        wheel = WheelLocation.REAR_LEFT,
                        onClick = { viewModel.selectWheel(WheelLocation.REAR_LEFT) }
                    )
                    WheelButton(
                        wheel = WheelLocation.REAR_RIGHT,
                        onClick = { viewModel.selectWheel(WheelLocation.REAR_RIGHT) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // System General Actions
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Comandi Generali ABS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.sendCommand("30 49 01") },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Stato Valvole", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.runBleedSequence("PUMP", "30 02 07 01", "30 02 07 00", 15) },
                        enabled = !state.isRunningSequence,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Pompa (15s)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.runBleedSequence("RELAY", "30 03 07 01", "30 03 07 00", 15) },
                        enabled = !state.isRunningSequence,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Relè (15s)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun WheelButton(wheel: WheelLocation, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1E293B),
            contentColor = LotusYellow
        ),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(LotusYellow)),
        modifier = Modifier
            .width(135.dp)
            .height(54.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(wheel.shortName, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text(
                when (wheel) {
                    WheelLocation.FRONT_LEFT -> "Anteriore SX"
                    WheelLocation.FRONT_RIGHT -> "Anteriore DX"
                    WheelLocation.REAR_LEFT -> "Posteriore SX"
                    WheelLocation.REAR_RIGHT -> "Posteriore DX"
                },
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun WheelDetailScreen(
    viewModel: BleedViewModel,
    wheel: WheelLocation,
    onBack: () -> Unit,
    isTestMode: Boolean = false,
    isSlowMode: Boolean = false
) {
    val state by viewModel.state.collectAsState()
    val terminalListState = rememberLazyListState()

    LaunchedEffect(state.terminalLog.size) {
        if (state.terminalLog.isNotEmpty()) {
            terminalListState.animateScrollToItem(state.terminalLog.size - 1)
        }
    }

    val headerTitle = when {
        isTestMode && isSlowMode -> "Bleeding Test Slow - ${wheel.title} (${wheel.shortName})"
        isTestMode -> "Bleeding Test - ${wheel.title} (${wheel.shortName})"
        isSlowMode -> "Bleed Slow - ${wheel.title} (${wheel.shortName})"
        else -> "${wheel.title} (${wheel.shortName})"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header with Back Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Indietro",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = headerTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (isSlowMode) "Tempistiche rilassate (Pausa 3s tra comandi AT)" else "Tempistiche veloci ed ottimizzate",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Active Operation & Emergency Stop Banner
        if (state.activeOperation != null) {
            EmergencyActiveBanner(
                activeOperation = state.activeOperation!!,
                timeRemainingSec = state.timeRemainingSec,
                onEmergencyStop = { viewModel.emergencyStop() }
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Main Wheel Bleed Macro Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            LotusYellow.copy(alpha = 0.8f),
                            RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isTestMode) "Macro Pulsazione Idraulica (${wheel.shortName})" else "Macro Spurgo Ruota (${wheel.shortName})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = LotusYellow,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            text = if (isTestMode)
                                "DUMP ON ➔ Pump ON ➔ 6x Cicli Pulsazione ISO (300ms ON / 300ms OFF) ➔ Shutdown Sicurezza (Pump, DUMP & ISO OFF)"
                            else if (isSlowMode)
                                "Esecuzione con pause di 3 secondi tra comandi ATZ, SH, WM, SP, DTC."
                            else
                                "Esecuzione con pause ottimizzate < 2.5 secondi totali.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Button(
                            onClick = {
                                when {
                                    isTestMode && isSlowMode -> viewModel.runWheelBleedPulseTestMacroSlow(wheel)
                                    isTestMode -> viewModel.runWheelBleedPulseTestMacro(wheel)
                                    isSlowMode -> viewModel.runWheelBleedMacroSlow(wheel)
                                    else -> viewModel.runWheelBleedMacro(wheel)
                                }
                            },
                            enabled = !state.isRunningSequence,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LotusYellow,
                                contentColor = Color.Black
                            )
                        ) {
                            Text(
                                text = if (isSlowMode) "AVVIA MACRO (SLOW 3s)" else "AVVIA MACRO (FAST)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            // Manual Valve Commands
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Comandi Manuali Valvole",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Text("Valvola DUMP (${wheel.shortName})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)) {
                            Button(
                                onClick = { viewModel.sendCommand(wheel.dumpOnCommand) },
                                enabled = !state.isRunningSequence,
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("DUMP OPEN (${wheel.dumpOnCommand})", fontSize = 10.sp) }

                            Button(
                                onClick = { viewModel.sendCommand(wheel.dumpOffCommand) },
                                enabled = !state.isRunningSequence,
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("DUMP CLOSE (${wheel.dumpOffCommand})", fontSize = 10.sp) }
                        }

                        Text("Valvola ISO (${wheel.shortName})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            Button(
                                onClick = { viewModel.sendCommand(wheel.isoOnCommand) },
                                enabled = !state.isRunningSequence,
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("ISO OPEN (${wheel.isoOnCommand})", fontSize = 10.sp) }

                            Button(
                                onClick = { viewModel.sendCommand(wheel.isoOffCommand) },
                                enabled = !state.isRunningSequence,
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("ISO CLOSE (${wheel.isoOffCommand})", fontSize = 10.sp) }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Integrated Terminal Screen Header & Box
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Schermo Terminale (Log)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = { viewModel.clearTerminal() },
                modifier = Modifier.size(28.dp)
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
                .height(160.dp)
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
    }
}

@Composable
fun EmergencyActiveBanner(
    activeOperation: String,
    timeRemainingSec: Int,
    onEmergencyStop: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "OPERAZIONE IN CORSO",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    activeOperation,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 15.sp
                )
                if (timeRemainingSec > 0) {
                    Text(
                        "Tempo rimanente: ${timeRemainingSec}s",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Button(
                onClick = onEmergencyStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFDC2626),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Stop d'Emergenza",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("EMERGENCY STOP", fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
