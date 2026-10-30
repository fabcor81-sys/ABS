package com.lotus.abs.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lotus.abs.R
import com.lotus.abs.bluetooth.BluetoothManager
import com.lotus.abs.transport.CommandCategory
import com.lotus.abs.ui.theme.LotusYellow
import com.lotus.abs.ui.viewmodel.BleedExecState
import com.lotus.abs.ui.viewmodel.BleedState
import com.lotus.abs.ui.viewmodel.BleedViewModel
import com.lotus.abs.ui.viewmodel.WheelLocation

private fun BleedState.isBusy(): Boolean =
    isRunningSequence || executionState == BleedExecState.STOPPING

@Composable
fun BleedScreen(viewModel: BleedViewModel, onOpenConnection: () -> Unit, isAdapterInitializing: Boolean = false, initializationFailed: Boolean = false) {
    val state by viewModel.state.collectAsState()
    val connectionStatus by viewModel.bluetoothManager.connectionState.collectAsState()
    val connected = connectionStatus == BluetoothManager.ConnectionState.CONNECTED
    val capable = connected && !isAdapterInitializing && !initializationFailed && viewModel.activeTransport.canExecute(CommandCategory.ACTUATOR_CONTROL)

    if (state.selectedWheel != null) {
        WheelDetailScreen(
            viewModel = viewModel,
            wheel = state.selectedWheel!!,
            isConnected = connected,
            actuatorAvailable = capable,
            onBack = { viewModel.selectWheel(null) }
        )
    } else {
        BleedSetupScreen(
            state = state,
            connected = connected,
            actuatorAvailable = capable,
            onChooseWheel = viewModel::selectSetupWheel,
            onChooseMode = viewModel::setSlowMode,
            onContinue = { state.setupWheel?.let(viewModel::selectWheel) },
            onOpenConnection = onOpenConnection
        )
    }
}

@Composable
private fun BleedSetupScreen(
    state: BleedState,
    connected: Boolean,
    actuatorAvailable: Boolean,
    onChooseWheel: (WheelLocation) -> Unit,
    onChooseMode: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onOpenConnection: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Assisted ABS Bleeding", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Select one wheel and an adapter initialization mode. The routine performs six ISO cycles and an experimental 5-second pump recovery interval.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )

        BleedOutcome(state)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (connected) "Adapter connected" else "Adapter disconnected",
                    fontWeight = FontWeight.Bold,
                    color = if (connected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                Text(
                    when {
                        !connected -> "Connect an adapter before sending ABS commands."
                        !actuatorAvailable -> "Actuator control is not enabled for this transport. You may review the procedure, but cannot start it."
                        else -> "Actuator control is available. ECU responses will be checked during the routine."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                if (!connected) {
                    TextButton(onClick = onOpenConnection) { Text("Go to Connection") }
                }
            }
        }

        Text("1. Select wheel", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Image(
                painter = painterResource(R.drawable.exige_s2_top),
                contentDescription = "Lotus Exige S2 top view, front at the top",
                modifier = Modifier.fillMaxWidth().height(175.dp).padding(8.dp),
                alignment = Alignment.Center
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WheelChoice(WheelLocation.FRONT_LEFT, state.setupWheel == WheelLocation.FRONT_LEFT, Modifier.weight(1f), onChooseWheel)
                WheelChoice(WheelLocation.FRONT_RIGHT, state.setupWheel == WheelLocation.FRONT_RIGHT, Modifier.weight(1f), onChooseWheel)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WheelChoice(WheelLocation.REAR_LEFT, state.setupWheel == WheelLocation.REAR_LEFT, Modifier.weight(1f), onChooseWheel)
                WheelChoice(WheelLocation.REAR_RIGHT, state.setupWheel == WheelLocation.REAR_RIGHT, Modifier.weight(1f), onChooseWheel)
            }
        }

        Text("2. Adapter initialization", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = !state.slowMode, onClick = { onChooseMode(false) },
                label = { Text("FAST INIT") }, modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = state.slowMode, onClick = { onChooseMode(true) },
                label = { Text("SLOW INIT") }, modifier = Modifier.weight(1f)
            )
        }
        Text(
            "FAST INIT uses shorter adapter setup delays; SLOW INIT uses longer delays. Both modes use the same six ISO pulses and pump recovery interval.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
        )
        Button(
            onClick = onContinue,
            enabled = state.setupWheel != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LotusYellow, contentColor = Color.Black)
        ) {
            Text("CONTINUE TO REVIEW", fontWeight = FontWeight.Black)
        }
        Text(
            "Note: The original Lotus Scan bleeding procedure requires all four caliper bleed nipples open. This single-wheel variant has not been validated as equivalent.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
        )
    }
}

@Composable
private fun WheelChoice(
    wheel: WheelLocation,
    selected: Boolean,
    modifier: Modifier,
    onClick: (WheelLocation) -> Unit
) {
    OutlinedButton(
        onClick = { onClick(wheel) }, modifier = modifier.heightIn(min = 64.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) LotusYellow else MaterialTheme.colorScheme.surface,
            contentColor = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface
        ),
        border = ButtonDefaults.outlinedButtonBorder
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(wheel.shortName, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text(
                when (wheel) {
                    WheelLocation.FRONT_LEFT -> "Front Left"
                    WheelLocation.FRONT_RIGHT -> "Front Right"
                    WheelLocation.REAR_LEFT -> "Rear Left"
                    WheelLocation.REAR_RIGHT -> "Rear Right"
                },
                fontSize = 11.sp, textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WheelDetailScreen(
    viewModel: BleedViewModel,
    wheel: WheelLocation,
    isConnected: Boolean,
    actuatorAvailable: Boolean,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val busy = state.isBusy()
    var confirmStart by remember { mutableStateOf(false) }
    var pressureReady by remember(wheel) { mutableStateOf(false) }
    var bleederReady by remember(wheel) { mutableStateOf(false) }
    var experimentalAcknowledged by remember(wheel) { mutableStateOf(false) }
    var showFullLog by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var manualRequest by remember { mutableStateOf<String?>(null) }
    var confirmAcknowledge by remember { mutableStateOf(false) }
    val requiresReview = state.executionState in setOf(BleedExecState.ERROR, BleedExecState.STOP_UNCONFIRMED, BleedExecState.COMPLETED_UNVERIFIED)
    val mode = if (state.slowMode) "SLOW INIT" else "FAST INIT"

    val attemptBack = {
        if (busy) Toast.makeText(context, "Stop the active routine before going back", Toast.LENGTH_SHORT).show()
        else onBack()
    }
    BackHandler(enabled = true) { attemptBack() }

    if (manualRequest != null) {
        val requested = manualRequest!!
        AlertDialog(
            onDismissRequest = { manualRequest = null },
            title = { Text("Confirm $requested manual test") },
            text = { Text("Activate the $requested valve on wheel ${wheel.shortName} for 1 second? The app will request and verify shutdown.") },
            confirmButton = {
                Button(onClick = {
                    manualRequest = null
                    if (requested == "ISO") {
                        viewModel.runBleedSequence("ISO ${wheel.shortName}", wheel.isoOnCommand, wheel.isoOffCommand, 1)
                    } else {
                        viewModel.runBleedSequence("DUMP ${wheel.shortName}", wheel.dumpOnCommand, wheel.dumpOffCommand, 1)
                    }
                }, enabled = actuatorAvailable && !busy && !requiresReview) {
                    Text("CONFIRM TEST")
                }
            },
            dismissButton = { TextButton(onClick = { manualRequest = null }) { Text("Cancel") } }
        )
    }

    if (confirmAcknowledge) {
        AlertDialog(
            onDismissRequest = { confirmAcknowledge = false },
            title = { Text("ABS outcome not verified") },
            text = { Text("Have you physically inspected the braking system and do you want to unlock further commands? Acknowledging this warning does not certify the hydraulic system as safe.") },
            confirmButton = {
                Button(onClick = { confirmAcknowledge = false; viewModel.acknowledgeOutcome() }) {
                    Text("I HAVE INSPECTED THE SYSTEM")
                }
            },
            dismissButton = { TextButton(onClick = { confirmAcknowledge = false }) { Text("Cancel") } }
        )
    }


    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("EXPERIMENTAL PROCEDURE — Safety notice") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Wheel: ${wheel.shortName} • $mode\n" +
                            "Complete these checks BEFORE activating the ABS pump or valves.",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Use only a suitable regulated low-pressure bleeding setup. " +
                            "Check the reservoir fluid level and prevent it from running dry.",
                        fontSize = 13.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = pressureReady, onCheckedChange = { pressureReady = it })
                        Text("Brake circuit prepared and correctly pressurized for the selected bleeding method.", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = bleederReady, onCheckedChange = { bleederReady = it })
                        Text("Bleed nipple OPEN on ${wheel.shortName}; collection hose and container secured.", fontSize = 13.sp)
                    }
                    Text(
                        "LOTUS SERVICE NOTES JJ.3: The official Lotus Scan automatic bleeding stage " +
                            "requires all FOUR caliper nipples open and gentle pedal cycling. " +
                            "This experimental single-wheel routine is NOT a validated substitute.",
                        color = MaterialTheme.colorScheme.error, fontSize = 12.sp
                    )
                    Text(
                        "USE AT YOUR OWN RISK. No warranty is provided. The developers and " +
                            "contributors disclaim liability for damage, personal injury or " +
                            "loss to the maximum extent permitted by applicable law.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = experimentalAcknowledged, onCheckedChange = { experimentalAcknowledged = it })
                        Text("I understand this routine is experimental, not Lotus-approved, and used at my own risk.", fontSize = 13.sp)
                    }
                    Text(
                        "6 ISO pulses (300 ms ON / 300 ms OFF), followed by 5 s of pump recovery. " +
                            "No actuator command is sent until you confirm.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pressureReady && bleederReady && experimentalAcknowledged &&
                            actuatorAvailable && !busy && !requiresReview
                        ) {
                            confirmStart = false
                            pressureReady = false
                            bleederReady = false
                            experimentalAcknowledged = false
                            if (state.slowMode) viewModel.runWheelBleedPulseTestMacroSlow(wheel)
                            else viewModel.runWheelBleedPulseTestMacro(wheel)
                        }
                    },
                    enabled = pressureReady && bleederReady && experimentalAcknowledged &&
                        actuatorAvailable && !busy && !requiresReview
                ) { Text("CONFIRM & START") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = attemptBack, enabled = !busy, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to wheel selection")
            }
            Column(Modifier.weight(1f)) {
                Text("Bleeding ${wheel.shortName} • $mode", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Hydraulic pulse routine", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        BleedOutcome(state)

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!busy) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().border(
                            1.dp, LotusYellow, RoundedCornerShape(10.dp)
                        ),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("EXPERIMENTAL PROCEDURE — NOT LOTUS-APPROVED", fontWeight = FontWeight.Black, color = LotusYellow)
                            Text(
                                "This single-wheel ABS bleeding routine is experimental and has not been " +
                                    "validated as equivalent to the official Lotus Scan procedure. " +
                                    "Use at your own risk.",
                                fontSize = 13.sp
                            )
                            Text(
                                "No warranty is provided. The developers and contributors disclaim liability " +
                                    "for damage, injury or loss to the maximum extent permitted by applicable law. " +
                                    "A pre-start checklist is required before any actuator command.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Selected sequence", fontWeight = FontWeight.Bold, color = LotusYellow)
                        Text("DUMP ON → Pump ON → 6 ISO pulses (300/300 ms) → DUMP OFF → ISO OFF → pump recovery 5 s → Pump OFF.",
                            fontSize = 13.sp)
                        Text(
                            if (isConnected) {
                                if (actuatorAvailable) "Adapter connected • actuator control available"
                                else "Actuator control unavailable: start disabled"
                            } else "No connection: start disabled",
                            color = if (actuatorAvailable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            if (busy) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (state.executionState == BleedExecState.STOPPING) "STOPPING" else "OPERATION IN PROGRESS",
                                fontWeight = FontWeight.Bold, color = LotusYellow)
                            Text(state.macroPhase, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            if (state.pulseCycle > 0 && state.macroPhase == "ISO pulsing") {
                                Text("ISO cycle ${state.pulseCycle} / 6", fontSize = 14.sp)
                                LinearProgressIndicator(
                                    progress = { state.pulseCycle / 6f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (state.timeRemainingSec > 0) {
                                Text(if (state.macroPhase.startsWith("Accumulator recovery")) "Pump recovery: ${state.timeRemainingSec} s" else "Time remaining: ${state.timeRemainingSec} s", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { showFullLog = !showFullLog }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showFullLog) "Hide full log" else "Show diagnostic log")
                }
            }
            if (showFullLog) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF080808))) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (state.terminalLog.isEmpty()) Text("No messages", color = Color.LightGray)
                            state.terminalLog.takeLast(80).forEach { entry ->
                                Text(
                                    "[${entry.timestamp}] ${entry.text}",
                                    fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFBDBDBD)
                                )
                            }
                        }
                    }
                }
            }
            if (!busy) {
                item {
                    TextButton(onClick = { showManual = !showManual }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showManual) "Hide manual tests" else "Advanced manual tests")
                    }
                }
                if (showManual) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Activation is limited to 1 second, with automatic shutdown. For controlled diagnostics only.",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Button(
                                    onClick = { manualRequest = "ISO" }, enabled = actuatorAvailable && !busy && !requiresReview,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Test ISO (1 s)") }
                                Button(
                                    onClick = { manualRequest = "DUMP" }, enabled = actuatorAvailable && !busy && !requiresReview,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Test DUMP (1 s)") }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (busy) {
            Button(
                onClick = { viewModel.emergencyStop() },
                enabled = state.executionState != BleedExecState.STOPPING,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF2626), contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.executionState == BleedExecState.STOPPING) "STOPPING" else "EMERGENCY STOP", fontWeight = FontWeight.Black)
            }
        } else {
            if (requiresReview) {
                Button(
                    onClick = { confirmAcknowledge = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White)
                ) { Text("INSPECT AND UNLOCK", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(6.dp))
            }
            Button(
                onClick = { confirmStart = true },
                enabled = actuatorAvailable && !requiresReview,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LotusYellow, contentColor = Color.Black)
            ) {
                Text("START ROUTINE $mode", fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BleedOutcome(state: BleedState) {
    val (headline, detail, warning) = when (state.executionState) {
        BleedExecState.COMPLETED -> Triple("Sequence completed", "Commands acknowledged. Verify brake bleeding and pedal feel before using the vehicle.", false)
        BleedExecState.COMPLETED_UNVERIFIED -> Triple("Sequence completed — missing confirmations", "One or more commands were only transmitted; the physical actuator state is not verified.", true)
        BleedExecState.STOPPED_CONFIRMED -> Triple("Stop commanded and acknowledged", "The ECU acknowledged shutdown; the physical state still requires inspection.", false)
        BleedExecState.STOP_UNCONFIRMED -> Triple("WARNING: stop not confirmed", "OFF commands were not fully acknowledged. Inspect the system before proceeding.", true)
        BleedExecState.ERROR -> Triple("Error or connection lost", "Actuator state not verified. Stop testing and inspect the system.", true)
        else -> return
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            .border(1.dp, if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(headline, fontWeight = FontWeight.Bold,
                color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
            Text(detail, fontSize = 12.sp)
        }
    }
}
