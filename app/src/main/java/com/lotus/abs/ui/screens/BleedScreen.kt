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
        Text("Spurgo ABS assistito", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Scegli una ruota e la modalità di comunicazione. La macro esegue sei cicli ISO e 5 secondi sperimentali di recupero pompa.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )

        BleedOutcome(state)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (connected) "Adattatore collegato" else "Adattatore non collegato",
                    fontWeight = FontWeight.Bold,
                    color = if (connected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                Text(
                    when {
                        !connected -> "Collega un adattatore prima di eseguire comandi ABS."
                        !actuatorAvailable -> "Il controllo attuatori non è abilitato su questo trasporto. Puoi consultare la procedura, ma non avviarla."
                        else -> "Controllo attuatori disponibile. La risposta ECU verrà verificata durante la macro."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                if (!connected) {
                    TextButton(onClick = onOpenConnection) { Text("Vai alla connessione") }
                }
            }
        }

        Text("1. Seleziona la ruota", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Image(
                painter = painterResource(R.drawable.exige_s2_top),
                contentDescription = "Lotus Exige S2 vista dall'alto, anteriore in alto",
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

        Text("2. Inizializzazione adattatore", fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
            "FAST INIT: pause di inizializzazione più brevi. SLOW INIT: pause di inizializzazione più lunghe. Entrambe eseguono gli stessi sei cicli ISO e lo stesso recupero pompa.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
        )
        Button(
            onClick = onContinue,
            enabled = state.setupWheel != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LotusYellow, contentColor = Color.Black)
        ) {
            Text("CONTINUA E VERIFICA", fontWeight = FontWeight.Black)
        }
        Text(
            "Nota: la procedura diagnostica Lotus Scan originale prevede tutti e quattro gli spurghi aperti. Questa variante a singola ruota non è validata come equivalente.",
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
                    WheelLocation.FRONT_LEFT -> "Anteriore SX"
                    WheelLocation.FRONT_RIGHT -> "Anteriore DX"
                    WheelLocation.REAR_LEFT -> "Posteriore SX"
                    WheelLocation.REAR_RIGHT -> "Posteriore DX"
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
    var showFullLog by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var manualRequest by remember { mutableStateOf<String?>(null) }
    var confirmAcknowledge by remember { mutableStateOf(false) }
    val requiresReview = state.executionState in setOf(BleedExecState.ERROR, BleedExecState.STOP_UNCONFIRMED, BleedExecState.COMPLETED_UNVERIFIED)
    val mode = if (state.slowMode) "SLOW INIT" else "FAST INIT"

    val attemptBack = {
        if (busy) Toast.makeText(context, "Arresta prima la macro per tornare indietro", Toast.LENGTH_SHORT).show()
        else onBack()
    }
    BackHandler(enabled = true) { attemptBack() }

    if (manualRequest != null) {
        val requested = manualRequest!!
        AlertDialog(
            onDismissRequest = { manualRequest = null },
            title = { Text("Conferma test manuale $requested") },
            text = { Text("Azionare la valvola $requested della ruota ${wheel.shortName} per 1 secondo? Lo spegnimento verrà richiesto e verificato.") },
            confirmButton = {
                Button(onClick = {
                    manualRequest = null
                    if (requested == "ISO") {
                        viewModel.runBleedSequence("ISO ${wheel.shortName}", wheel.isoOnCommand, wheel.isoOffCommand, 1)
                    } else {
                        viewModel.runBleedSequence("DUMP ${wheel.shortName}", wheel.dumpOnCommand, wheel.dumpOffCommand, 1)
                    }
                }, enabled = actuatorAvailable && !busy && !requiresReview) {
                    Text("CONFERMA TEST")
                }
            },
            dismissButton = { TextButton(onClick = { manualRequest = null }) { Text("Annulla") } }
        )
    }

    if (confirmAcknowledge) {
        AlertDialog(
            onDismissRequest = { confirmAcknowledge = false },
            title = { Text("Esito ABS non verificato") },
            text = { Text("Confermi di aver verificato fisicamente l'impianto frenante e di voler sbloccare nuovi comandi? La conferma non certifica la sicurezza del circuito.") },
            confirmButton = {
                Button(onClick = { confirmAcknowledge = false; viewModel.acknowledgeOutcome() }) {
                    Text("HO ESEGUITO LA VERIFICA")
                }
            },
            dismissButton = { TextButton(onClick = { confirmAcknowledge = false }) { Text("Annulla") } }
        )
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("Conferma procedura ABS") },
            text = {
                Text(
                    "Ruota: ${wheel.shortName} • Inizializzazione: $mode\n" +
                    "6 cicli ISO da 300/300 ms; poi riposo valvole e pompa ON per 5 s.\n\n" +
                    "Verifica livello liquido, spurgo e condizioni di lavoro. " +
                    "I 5 secondi sono sperimentali, non prescritti dal manuale Lotus."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmStart = false
                        if (state.slowMode) viewModel.runWheelBleedPulseTestMacroSlow(wheel)
                        else viewModel.runWheelBleedPulseTestMacro(wheel)
                    },
                    enabled = actuatorAvailable && !busy && !requiresReview
                ) { Text("CONFERMA AVVIO") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }) { Text("Annulla") }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = attemptBack, enabled = !busy, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Torna alla selezione")
            }
            Column(Modifier.weight(1f)) {
                Text("Spurgo ${wheel.shortName} • $mode", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Macro pulsazione idraulica", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        BleedOutcome(state)

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sequenza selezionata", fontWeight = FontWeight.Bold, color = LotusYellow)
                        Text("DUMP ON → Pompa ON → 6 cicli ISO (300/300 ms) → DUMP OFF → ISO OFF → recupero pompa 5 s → Pompa OFF.",
                            fontSize = 13.sp)
                        Text(
                            if (isConnected) {
                                if (actuatorAvailable) "Adattatore collegato • controllo attuatori disponibile"
                                else "Controllo attuatori non disponibile: avvio bloccato"
                            } else "Nessuna connessione: avvio bloccato",
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
                            Text(if (state.executionState == BleedExecState.STOPPING) "ARRESTO IN CORSO" else "OPERAZIONE IN CORSO",
                                fontWeight = FontWeight.Bold, color = LotusYellow)
                            Text(state.macroPhase, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            if (state.pulseCycle > 0 && state.macroPhase == "Pulsazione ISO") {
                                Text("Ciclo ISO ${state.pulseCycle} / 6", fontSize = 14.sp)
                                LinearProgressIndicator(
                                    progress = { state.pulseCycle / 6f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (state.timeRemainingSec > 0) {
                                Text(if (state.macroPhase.startsWith("Recupero")) "Recupero pompa: ${state.timeRemainingSec} s" else "Tempo rimanente: ${state.timeRemainingSec} s", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { showFullLog = !showFullLog }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showFullLog) "Nascondi log esteso" else "Mostra log diagnostico")
                }
            }
            if (showFullLog) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF080808))) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (state.terminalLog.isEmpty()) Text("Nessun messaggio", color = Color.LightGray)
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
                        Text(if (showManual) "Chiudi test manuali" else "Test manuali avanzati")
                    }
                }
                if (showManual) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Attivazione limitata a 1 secondo con spegnimento automatico. Solo per diagnostica controllata.",
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
                Text(if (state.executionState == BleedExecState.STOPPING) "ARRESTO IN CORSO" else "STOP EMERGENZA", fontWeight = FontWeight.Black)
            }
        } else {
            if (requiresReview) {
                Button(
                    onClick = { confirmAcknowledge = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White)
                ) { Text("VERIFICA E SBLOCCA", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(6.dp))
            }
            Button(
                onClick = { confirmStart = true },
                enabled = actuatorAvailable && !requiresReview,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LotusYellow, contentColor = Color.Black)
            ) {
                Text("AVVIA MACRO $mode", fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BleedOutcome(state: BleedState) {
    val (headline, detail, warning) = when (state.executionState) {
        BleedExecState.COMPLETED -> Triple("Sequenza terminata", "Comandi confermati. Verificare comunque il corretto spurgo e il pedale prima di utilizzare il veicolo.", false)
        BleedExecState.COMPLETED_UNVERIFIED -> Triple("Sequenza terminata — risposte incomplete", "Uno o più comandi risultano solo trasmessi: lo stato fisico non è verificato.", true)
        BleedExecState.STOPPED_CONFIRMED -> Triple("Arresto comandato e confermato", "Lo spegnimento è stato confermato dalla ECU; lo stato fisico richiede verifica.", false)
        BleedExecState.STOP_UNCONFIRMED -> Triple("ATTENZIONE: arresto non confermato", "Non è stata ricevuta conferma completa dei comandi OFF. Verificare l'impianto prima di procedere.", true)
        BleedExecState.ERROR -> Triple("Errore o connessione persa", "Stato attuatori non verificato. Interrompere i test e controllare il sistema.", true)
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
