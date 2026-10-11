package com.lotus.abs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    val menuItems = listOf(
        Triple("home", "Home", Icons.Default.Home),
        Triple("connection", "Device Connection", Icons.Default.Bluetooth),
        Triple("live_data", "Live Data", Icons.Default.Speed),
        Triple("dtc", "DTC Errors", Icons.Default.Warning),
        Triple("bleed", "Bleed System", Icons.Default.Build)
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = "LOTUS ABS",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(24.dp)
                )
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                menuItems.forEach { (route, label, icon) ->
                    NavigationDrawerItem(
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        selected = currentRoute == route,
                        onClick = {
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedContainerColor = MaterialTheme.colorScheme.surface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(menuItems.find { it.first == currentRoute }?.second ?: "Lotus ABS") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                        navigationIconContentColor = MaterialTheme.colorScheme.primary
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
                    HomeScreen(navController)
                }
                composable("connection") {
                    ConnectionScreen(connectionViewModel)
                }
                composable("live_data") {
                    LiveDataScreen(liveDataViewModel)
                }
                composable("dtc") {
                    DiagnosticsScreen(diagnosticsViewModel)
                }
                composable("bleed") {
                    BleedScreen(bleedViewModel)
                }
            }
        }
    }
}
