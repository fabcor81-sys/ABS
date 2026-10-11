# Lotus ABS Android App

Applicazione Android nativa per la diagnostica del sistema ABS (Anti-Lock Braking System) di veicoli Lotus tramite connessione Bluetooth a un dispositivo ELM327.

## Caratteristiche

### 🏠 Home Screen
- Visualizzazione dello stato di connessione Bluetooth
- Lista dei dispositivi Bluetooth accoppiati
- Informazioni sull'ECU connesso
- Voltaggio della batteria
- Gestione della connessione/disconnessione

### 📊 Dashboard
- **Monitoraggio Real-time:**
  - RPM (giri motore)
  - Velocità (km/h)
  - Temperatura motore (°C)
  - Pressione freni (bar)
- Refresh manuale dei dati
- Cronologia salvata nel database locale

### 🔧 Diagnostics
- Lettura dei Trouble Codes (DTC)
- Cancellazione degli errori
- Decodifica automatica dei codici
- Visualizzazione dello stato e descrizione errori
- Eliminazione singoli codici dalla cronologia

## Architettura

### Stack Tecnologico
- **Linguaggio:** Kotlin
- **Framework UI:** Jetpack Compose
- **Database:** Room Database
- **Comunicazione:** Bluetooth Classic + OBD2
- **Architettura:** MVVM + Flow

### Struttura del Progetto
```
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

## Requisiti

- Android 8.0+ (API 26)
- Dispositivo con Bluetooth
- ELM327 accoppiato via Bluetooth
- Permessi Android richiesti:
  - BLUETOOTH_CONNECT
  - BLUETOOTH_SCAN
  - ACCESS_FINE_LOCATION

## Build e Deploy

### Build APK
```bash
./gradlew build
```

### Build e installazione su device
```bash
./gradlew installDebug
```

### Generare release APK
```bash
./gradlew assembleRelease
```

## Comandi OBD2 Supportati

### Comandi ELM327
- `AT Z` - Reset
- `AT E0` - Disabilita echo
- `AT L0` - Disabilita line feeds
- `AT SP 6` - Imposta protocollo KWP2000

### Comandi Diagnostici
- `010C` - RPM (giri motore)
- `010D` - Velocità veicolo
- `0105` - Temperatura coolant
- `010A` - Pressione carburante
- `19 02` - Leggi DTC
- `14` - Cancella DTC

## Utilizzo

1. **Connessione:**
   - Accendi il Bluetooth sul dispositivo
   - Seleziona ELM327 dalla lista dei dispositivi
   - L'app si connetterà automaticamente

2. **Dashboard:**
   - Una volta connesso, accedi alla sezione Dashboard
   - Premi il bottone di refresh per aggiornare i dati
   - I valori vengono salvati automaticamente nel database

3. **Diagnostica:**
   - Accedi a Diagnostics per leggere i codici errore
   - Premi "Read DTC" per scansionare gli errori
   - Usa "Clear Errors" per cancellare i DTC (attenzione!)

## Permessi Richiesti

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

## Note Tecniche

### Timeout Bluetooth
- Timeout sessione: 3 secondi
- Timeout reply: 2 secondi
- Delay inter-messaggio: 25ms

### Database Room
- Tabella `dtc_records` - Cronologia codici errore
- Tabella `sensor_data` - Cronologia letture sensori
- Database auto-pulito per dati antichi

### Thread di Comunicazione
- Bluetooth: Coroutine IO Dispatcher
- UI: Main Dispatcher
- Database: Coroutine IO Dispatcher

## Troubleshooting

### Connessione non stabilizzata
1. Verifica che ELM327 sia alimentato
2. Riaccendi il Bluetooth sul dispositivo
3. Riavvia l'app

### DTC non leggibili
1. Verifica la connessione Bluetooth
2. Controlla che il veicolo sia acceso
3. Riprova la scansione

### Dati non aggiornano
1. Premi il bottone di refresh manualmente
2. Controlla la qualità della connessione Bluetooth
3. Riavvia la connessione

## Sviluppo Futuro

- [ ] Grafici real-time dei dati
- [ ] Esportazione dati in CSV
- [ ] Storico completo delle sessioni
- [ ] Supporto per più ECU
- [ ] Widget Android
- [ ] Sincronizzazione cloud

## Licenza

Questo progetto è per uso personale e diagnostica del sistema ABS Lotus.

## Contatti

Sviluppato per la diagnostica di sistemi ABS Lotus via ELM327 Bluetooth.
