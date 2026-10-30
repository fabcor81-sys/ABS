package com.lotus.abs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lotus.abs.bluetooth.BluetoothManager
import com.lotus.abs.data.database.LotusAbsDatabase
import com.lotus.abs.ui.screens.BleedScreen
import com.lotus.abs.ui.screens.ConnectionScreen
import com.lotus.abs.ui.screens.DiagnosticsScreen
import com.lotus.abs.ui.screens.HomeScreen
import com.lotus.abs.ui.screens.LiveDataScreen
import com.lotus.abs.ui.theme.LotusABSAndroidTheme
import com.lotus.abs.ui.viewmodel.BleedViewModel
import com.lotus.abs.ui.viewmodel.ConnectionViewModel
import com.lotus.abs.ui.viewmodel.DiagnosticsViewModel
import com.lotus.abs.ui.viewmodel.LiveDataViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var database: LotusAbsDatabase
    private lateinit var connectionViewModel: ConnectionViewModel
    private lateinit var diagnosticsViewModel: DiagnosticsViewModel
    private lateinit var liveDataViewModel: LiveDataViewModel
    private lateinit var bleedViewModel: BleedViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            connectionViewModel.refreshDeviceList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize file logger
        com.lotus.abs.util.FileLogger.init(this)
        com.lotus.abs.util.FileLogger.i("MainActivity", "Lotus ABS Application Started")

        // Initialize managers and viewmodels
        bluetoothManager = BluetoothManager(this)
        database = LotusAbsDatabase.getInstance(this)

        connectionViewModel = ConnectionViewModel(bluetoothManager)
        diagnosticsViewModel = DiagnosticsViewModel(bluetoothManager, database)
        liveDataViewModel = LiveDataViewModel(bluetoothManager)
        bleedViewModel = BleedViewModel(bluetoothManager)

        // Request permissions
        requestBluetoothPermissions()

        setContent {
            LotusABSAndroidTheme {
                MainApp(
                    connectionViewModel = connectionViewModel,
                    diagnosticsViewModel = diagnosticsViewModel,
                    liveDataViewModel = liveDataViewModel,
                    bleedViewModel = bleedViewModel
                )
            }
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val needToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(needToRequest.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    connectionViewModel: ConnectionViewModel,
    diagnosticsViewModel: DiagnosticsViewModel,
    liveDataViewModel: LiveDataViewModel,
    bleedViewModel: BleedViewModel
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val navEntry by navController.currentBackStackEntryAsState()
    val route = navEntry?.destination?.route ?: "home"

    val bleedState by bleedViewModel.state.collectAsState()
    val connectionState by connectionViewModel.state.collectAsState()
    val operationActive = bleedState.isRunningSequence ||
        bleedState.executionState == com.lotus.abs.ui.viewmodel.BleedExecState.STOPPING

    val menuItems = listOf(
        Triple("home", "Home", Icons.Default.Home),
        Triple("connection", "Connessione", Icons.Default.Bluetooth),
        Triple("live_data", "Dati live", Icons.Default.Speed),
        Triple("dtc", "Diagnostica DTC", Icons.Default.Warning),
        Triple("bleed", "Spurgo ABS", Icons.Default.Build)
    )

    val navigateTo: (String) -> Unit = { destination ->
        if (operationActive && destination != route) {
            scope.launch { snackbar.showSnackbar("Macro attiva: usa STOP prima di cambiare schermata") }
        } else if (destination == route) {
            if (destination == "bleed" && !operationActive) bleedViewModel.selectWheel(null)
        } else {
            if (destination == "bleed") bleedViewModel.selectWheel(null)
            if (destination == "home") {
                if (!navController.popBackStack("home", inclusive = false)) {
                    navController.navigate("home") { launchSingleTop = true }
                }
            } else {
                navController.navigate(destination) {
                    popUpTo("home") { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
        scope.launch { drawerState.close() }
    }

    // Guard system Back while any actuator routine is active. The nested
    // wheel-detail BackHandler handles its own disabled Back path as well.
    BackHandler(enabled = operationActive) {
        scope.launch { snackbar.showSnackbar("Operazione ABS attiva: premi STOP EMERGENZA prima di uscire") }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !operationActive,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.lotus_cars_logo),
                        contentDescription = "Lotus Logo",
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text("LOTUS ", fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("ABS", fontSize = 20.sp, fontWeight = FontWeight.Black, color = com.lotus.abs.ui.theme.LotusYellow)
                }
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                menuItems.forEach { (target, label, icon) ->
                    NavigationDrawerItem(
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label, fontWeight = FontWeight.SemiBold) },
                        selected = route == target,
                        onClick = { navigateTo(target) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        if (route == "home") {
                            Text("LOTUS ABS", fontWeight = FontWeight.Black,
                                color = com.lotus.abs.ui.theme.LotusYellow)
                        } else {
                            Text(menuItems.firstOrNull { it.first == route }?.second ?: "Lotus ABS",
                                fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        if (route == "home") {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Apri menu")
                            }
                        } else {
                            IconButton(
                                onClick = { navigateTo("home") },
                                enabled = !operationActive
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Torna alla Home")
                            }
                        }
                    },
                    actions = {
                        if (route != "home") {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                enabled = !operationActive
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Apri menu")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                        navigationIconContentColor = MaterialTheme.colorScheme.primary,
                        actionIconContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("home") {
                    HomeScreen(
                        onNavigate = navigateTo,
                        isConnected = connectionState.connectionStatus == BluetoothManager.ConnectionState.CONNECTED,
                        connectionType = if (connectionState.selectedTransport == com.lotus.abs.ui.viewmodel.TransportType.USB)
                            "USB KKL 409.1" else "Bluetooth ELM327"
                    )
                }
                composable("connection") { ConnectionScreen(connectionViewModel, onBleed = { navigateTo("bleed") }) }
                composable("live_data") { LiveDataScreen(liveDataViewModel) }
                composable("dtc") { DiagnosticsScreen(diagnosticsViewModel) }
                composable("bleed") {
                    BleedScreen(
                        viewModel = bleedViewModel,
                        onOpenConnection = { navigateTo("connection") }
                    )
                }
            }
        }
    }
}
