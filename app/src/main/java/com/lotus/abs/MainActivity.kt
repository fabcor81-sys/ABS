package com.lotus.abs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lotus.abs.bluetooth.BluetoothManager
import com.lotus.abs.data.database.LotusAbsDatabase
import com.lotus.abs.ui.screens.DashboardScreen
import com.lotus.abs.ui.screens.DiagnosticsScreen
import com.lotus.abs.ui.screens.HomeScreen
import com.lotus.abs.ui.theme.LotusABSAndroidTheme
import com.lotus.abs.ui.viewmodel.DashboardViewModel
import com.lotus.abs.ui.viewmodel.DiagnosticsViewModel
import com.lotus.abs.ui.viewmodel.HomeViewModel

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var database: LotusAbsDatabase
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var dashboardViewModel: DashboardViewModel
    private lateinit var diagnosticsViewModel: DiagnosticsViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            // Handle permission denial
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize managers and viewmodels
        bluetoothManager = BluetoothManager(this)
        database = LotusAbsDatabase.getInstance(this)

        homeViewModel = HomeViewModel(bluetoothManager)
        dashboardViewModel = DashboardViewModel(bluetoothManager, database)
        diagnosticsViewModel = DiagnosticsViewModel(bluetoothManager, database)

        // Request permissions
        requestBluetoothPermissions()

        setContent {
            LotusABSAndroidTheme {
                MainApp(
                    homeViewModel = homeViewModel,
                    dashboardViewModel = dashboardViewModel,
                    diagnosticsViewModel = diagnosticsViewModel
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

@Composable
fun MainApp(
    homeViewModel: HomeViewModel,
    dashboardViewModel: DashboardViewModel,
    diagnosticsViewModel: DiagnosticsViewModel
) {
    val navController = rememberNavController()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home"
                        )
                    },
                    label = { Text("Home") },
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        navController.navigate("home") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Dashboard"
                        )
                    },
                    label = { Text("Dashboard") },
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        navController.navigate("dashboard") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Diagnostics"
                        )
                    },
                    label = { Text("Diagnostics") },
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        navController.navigate("diagnostics") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(homeViewModel)
            }
            composable("dashboard") {
                DashboardScreen(dashboardViewModel)
            }
            composable("diagnostics") {
                DiagnosticsScreen(diagnosticsViewModel)
            }
        }
    }
}
