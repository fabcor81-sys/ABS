# Lotus ABS — UX/UI navigation and safety test matrix

This document describes source-reviewed expected behavior; **it does not claim real-hardware validation**.

## Navigation paths

| Path | Expected screen / behavior | Automated smoke coverage |
| --- | --- | --- |
| Home → Connection | USB/Bluetooth selection, device list, status | Manual |
| Connection → Home (top-bar Home button) | Home, adapter remains connected | Manual |
| Connection (connected) → Vai allo spurgo | Bleed setup (not directly starting a macro) | Manual |
| Home → Bleed | Select wheel and FAST/SLOW | NavigationFlowTest |
| Home → drawer → Bleed | Same single setup page, no duplicate FAST/SLOW routes | NavigationFlowTest |
| Bleed setup → LF/RF/LR/RR → Continue | Selected wheel detail, explicit start confirmation | NavigationFlowTest LF/RF |
| Wheel detail → internal Back | Returns to Bleed setup, preserves choice | NavigationFlowTest |
| Wheel detail → Android Back | Returns to Bleed setup | NavigationFlowTest |
| Bleed setup → Android Back | Returns to Home | NavigationFlowTest |
| Wheel detail → top-bar Home | Returns to Home while idle | Manual |
| Live Data → Home | Returns without active polling (DisposableEffect) | Manual |
| Diagnostics → Home | Returns to Home, retains DTC log | Manual |
| Repeated Home → Bleed | Starts at setup, not an old wheel detail | Manual |
| Connected menu switching | Preserves adapter connection unless explicit disconnect | Manual |

## Operational safeguards / negative-path checks

| Situation | Expected |
| --- | --- |
| No adapter connected | Bleed can be inspected but Start is disabled |
| USB KKL read-only | Actuator Start is disabled; explanatory message shown |
| Running macro | Home, drawer and Android Back cannot navigate away |
| Running macro | Emergency Stop accessible, not hidden under log |
| Emergency Stop | Requests immediate coordinated stop; skips normal 5s pump recovery |
| Missing OFF acknowledgement | STOP_UNCONFIRMED or ERROR persists; no success message |
| Unverified outcome | Actuator Start disabled until explicit inspection acknowledgment |
| Normal outcome | Completion displayed persistently with physical-check warning |
| Normal FAST/SLOW sequence | Six ISO pulses, DUMP/ISO return to rest, then 5s pump recovery, pump OFF |
| Manual ISO / DUMP test | Explicit confirmation, at most 1s ON and acknowledged OFF required |
| DTC clear | Confirmation before sending; local history is NOT cleared without ECU positive ACK |
| USB DTC clear | Disabled (USB KKL whitelist currently read-only) |
| DTC raw terminal | Restricted to read-command prefixes; no raw 30 actuator command |
| Live Data disconnected | Poll button disabled; data explicitly shown as stale |
| Live Data units | A single km/h / V suffix, last received timestamp shown |

## Manual validation still required

- Android emulator / real phone layout checks at small widths, landscape, dynamic font sizes.
- Granting/rejecting Bluetooth permissions, USB attach/detach, BLE/Classic switching.
- Connection and commands on EBC430 with reliable ECU acknowledgements, without actuating brakes during UI acceptance testing.
- The Lotus original service bleeding process is **not** reproduced by the per-wheel routine. Lotus Service Notes JJ.3 calls for all four nipples open during the Lotus Scan automatic stage.
- The five-second pump recovery is **experimental**. JJ.12 describes brief pump overrun during ordinary ABS brake release but does not specify a service-bleeding interval.

## Commands for automated tests

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin
# NavigationFlowTest must be executed separately on an Android emulator/device:
./gradlew :app:connectedDebugAndroidTest
```
