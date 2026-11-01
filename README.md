# Lotus ABS Android App

Native Android application for diagnosing the ABS (Anti-lock Braking System) on Lotus vehicles through a Bluetooth connection to an ELM327 device.

## Features

### 🏠 Home Screen
- Bluetooth connection status
- List of paired Bluetooth devices
- Connected ECU information
- Battery voltage
- Connect/disconnect controls

### 📊 Dashboard
- **Real-time monitoring:**
  - Engine speed (RPM)
  - Vehicle speed (km/h)
  - Engine temperature (°C)
  - Brake pressure (bar)
- Manual data refresh
- History stored in a local database

### 🔧 Diagnostics
- Read diagnostic trouble codes (DTCs)
- Clear faults
- Automatic code decoding
- Fault status and descriptions
- Delete individual codes from the history

## Architecture

### Technology Stack
- **Language:** Kotlin
- **UI framework:** Jetpack Compose
- **Database:** Room Database
- **Communication:** Bluetooth Classic + OBD2
- **Architecture:** MVVM + Flow

### Project Structure
```text
app/src/main/
├── java/com/lotus/abs/
│   ├── bluetooth/
│   │   └── BluetoothManager.kt
│   ├── obd2/
│   │   └── OBD2Parser.kt
│   ├── data/
│   │   ├── entity/
│   │   │   └── DTCRecord.kt
│   │   ├── dao/
│   │   │   ├── DTCDao.kt
│   │   │   └── SensorDao.kt
│   │   └── database/
│   │       └── LotusAbsDatabase.kt
│   ├── ui/
│   │   ├── viewmodel/
│   │   │   ├── HomeViewModel.kt
│   │   │   ├── DashboardViewModel.kt
│   │   │   └── DiagnosticsViewModel.kt
│   │   ├── screens/
│   │   │   ├── HomeScreen.kt
│   │   │   ├── DashboardScreen.kt
│   │   │   └── DiagnosticsScreen.kt
│   │   └── theme/
│   │       └── Theme.kt
│   └── MainActivity.kt
└── res/
    └── values/
        ├── strings.xml
        ├── colors.xml
        └── themes.xml
```

## Requirements

- Android 8.0+ (API 26)
- Bluetooth-capable device
- ELM327 paired over Bluetooth
- Required Android permissions:
  - BLUETOOTH_CONNECT
  - BLUETOOTH_SCAN
  - ACCESS_FINE_LOCATION

## Build and Deployment

### Build the APK
```bash
./gradlew build
```

### Build and Install on a Device
```bash
./gradlew installDebug
```

### Generate a Release APK
```bash
./gradlew assembleRelease
```

## Supported OBD2 Commands

### ELM327 Commands
- `AT Z` - Reset
- `AT E0` - Disable echo
- `AT L0` - Disable line feeds
- `AT SP 6` - Set the protocol (the original documentation labels this as KWP2000; this protocol mapping needs verification)

### Diagnostic Commands
- `010C` - Engine speed (RPM)
- `010D` - Vehicle speed
- `0105` - Coolant temperature
- `010A` - Fuel pressure
- `19 02` - Read DTCs
- `14` - Clear DTCs

## Usage

1. **Connection:**
   - Enable Bluetooth on your device.
   - Select the ELM327 from the device list.
   - The app will connect automatically.

2. **Dashboard:**
   - Once connected, open the Dashboard section.
   - Press the refresh button to update the readings.
   - Values are automatically saved to the database.

3. **Diagnostics:**
   - Open Diagnostics to read fault codes.
   - Press "Read DTC" to scan for faults.
   - Use "Clear Errors" to clear DTCs (use with care).

## Required Permissions

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

## Technical Notes

### Bluetooth Timeouts
- Session timeout: 3 seconds
- Reply timeout: 2 seconds
- Inter-message delay: 25 ms

### Room Database
- `dtc_records` table - Fault code history
- `sensor_data` table - Sensor reading history
- Automatic cleanup of old data

### Communication Threads
- Bluetooth: Coroutine IO Dispatcher
- UI: Main Dispatcher
- Database: Coroutine IO Dispatcher

## Troubleshooting

### Connection Cannot Be Established
1. Check that the ELM327 is powered on.
2. Turn Bluetooth off and back on.
3. Restart the app.

### DTCs Cannot Be Read
1. Check the Bluetooth connection.
2. Check that the vehicle ignition is on.
3. Retry the scan.

### Readings Do Not Update
1. Press the manual refresh button.
2. Check the Bluetooth connection quality.
3. Disconnect and reconnect.

## Future Development

- [ ] Real-time data charts
- [ ] CSV data export
- [ ] Complete session history
- [ ] Support for multiple ECUs
- [ ] Android widgets
- [ ] Cloud synchronization

## License

This project is intended for personal use and Lotus ABS diagnostics.

## Contact

Developed for Lotus ABS diagnostics over ELM327 Bluetooth.
