# Bluetooth Support - Classic & BLE

Questo progetto supporta **sia Bluetooth Classic che Bluetooth Low Energy (BLE)** per massima compatibilità con diversi dispositivi ELM327.

## 🔌 Tipologie di Dispositivi Supportati

### Bluetooth Classic
- **ELM327 Tradizionali** (versioni meno recenti)
- Connessione: RFCOMM Socket
- UUID: `00001101-0000-1000-8000-00805F9B34FB`
- Consumo batteria: Medio-Alto
- Latenza: Bassa

### Bluetooth Low Energy (BLE)
- **ELM327 BLE** (versioni moderne)
- Connessione: GATT (Generic Attribute Profile)
- Service UUID: `0000ffe0-0000-1000-8000-00805f9b34fb`
- Characteristic UUID: `0000ffe1-0000-1000-8000-00805f9b34fb`
- Consumo batteria: Molto Basso ⚡
- Latenza: Media

## 🤖 Come Funziona l'Auto-Detection

1. **Rilevamento Tipo Dispositivo:**
   ```
   detectDeviceType(device) → TYPE_LE vs TYPE_CLASSIC
   ```

2. **Connessione Automatica:**
   - Se TYPE_LE → Usa BLE (GattCallback)
   - Se TYPE_CLASSIC → Usa RFCOMM Socket
   - Se UNKNOWN → Prova Classic prima, poi BLE

3. **Interfaccia Unificata:**
   - `sendCommand()` → Works for both
   - `receivedData` → Same flow for both
   - `connectionState` → Unified state machine

## 📊 Flusso di Connessione

```
User selects device
         ↓
detectDeviceType()
         ↓
    ┌────┴────┐
    ↓         ↓
  CLASSIC    BLE
    ↓         ↓
connectClassic  connectBLE
    ↓         ↓
  RFCOMM    GattCallback
    ↓         ↓
startListeningClassic  (automatic in callback)
    ↓         ↓
CONNECTED ←───┘
```

## 🔧 Permessi Richiesti

### Android 11 e precedenti
```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

### Android 12+
```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

## 📝 Implementazione Tecnica

### BluetoothManager - Dual Mode
```kotlin
class BluetoothManager {
    // Classic
    private var bluetoothSocket: BluetoothSocket?
    private var inputStream: InputStream?
    private var outputStream: OutputStream?
    
    // BLE
    private var bluetoothGatt: BluetoothGatt?
    private var writeCharacteristic: BluetoothGattCharacteristic?
    
    private var deviceType: DeviceType
}
```

### Supporto API Level
- **API 18+**: Supporto BLE base
- **API 21+**: Supporto completo GATT
- **API 31+**: Nuovi permission model
- **API 33+**: Nuovi permission model migliorato

## 🧪 Testare la Compatibilità

### Test Bluetooth Classic
1. Usa un ELM327 Bluetooth Classic
2. Accoppia via Bluetooth Settings
3. Seleziona da app → Dovrebbe connettersi immediatamente

### Test BLE
1. Usa un ELM327 BLE (es. vLinker BLE)
2. L'app dovrebbe rilevarlo automaticamente
3. Seleziona da app → Dovrebbe connettersi via GATT

### Test Auto-Fallback
1. Se il dispositivo è UNKNOWN
2. L'app tenta Classic prima
3. Se fallisce, prova BLE
4. Se entrambi falliscono → Errore

## 🐛 Debugging

### Log filtrati
```bash
adb logcat | grep "BluetoothManager"
```

### Informazioni utili
- "Device type detected: CLASSIC/BLE/UNKNOWN"
- "Connected to [device name] (Classic/BLE)"
- "Services discovered" (BLE only)
- "Write characteristic found" (BLE only)

## ⚠️ Limitazioni Conosciute

1. **BLE Timeout**: Se il dispositivo BLE non risponde in 5 secondi, timeout
2. **Fallback**: Se device è UNKNOWN e Classic fallisce, il fallback a BLE avviene dopo l'errore
3. **Characteristic UUID**: I UUID sono fixed per ELM327 standard

## 🚀 Miglioramenti Futuri

- [ ] UI per scegliere manualmente Classic vs BLE
- [ ] Supporto per ELM327 custom UUIDs
- [ ] Reconnect automatico con backoff
- [ ] Better BLE characteristic discovery
- [ ] Multiple writes buffering per BLE

## 📚 Riferimenti

- [Android Bluetooth Classic](https://developer.android.com/guide/topics/connectivity/bluetooth/connect-bluetooth-classic)
- [Android Bluetooth LE](https://developer.android.com/guide/topics/connectivity/bluetooth/ble-overview)
- [ELM327 Documentation](https://www.elm327.org/)
