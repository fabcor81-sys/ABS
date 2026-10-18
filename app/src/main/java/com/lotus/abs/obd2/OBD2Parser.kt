package com.lotus.abs.obd2

import java.util.Locale

object OBD2Commands {
    // ELM327 Commands
    const val RESET = "AT Z"
    const val ECHO_OFF = "AT E0"
    const val LINE_FEEDS_OFF = "AT L0"
    const val TIMEOUT = "AT ST 0A"
    const val SET_PROTOCOL = "AT SP 6"  // KWP2000 (ISO 15765-4 CAN)
    const val SET_HEADER = "AT SH 700"  // Lotus ABS Header

    // OBD2 Standard Commands (PID Mode 01)
    const val ENGINE_RPM = "010C"
    const val VEHICLE_SPEED = "010D"
    const val COOLANT_TEMP = "0105"
    const val FUEL_PRESSURE = "010A"
    const val INTAKE_TEMP = "010F"
    const val MAF_AIRFLOW = "0110"
    const val THROTTLE_POSITION = "0111"
    const val OXYGEN_SENSOR1 = "0114"
    const val OXYGEN_SENSOR2 = "0115"

    // Diagnostic Commands
    const val READ_DTC = "18 02"   // KWP2000 Read DTC
    const val CLEAR_DTC = "14 FF FF 00" // Clear DTC
    const val READ_VIN = "1A 90"   // Read VIN
}

object OBD2Parser {
    fun parseRPM(response: String): Int {
        return try {
            val bytes = extractBytes(response)
            if (bytes.size >= 2) {
                ((bytes[0] * 256 + bytes[1]) / 4)
            } else 0
        } catch (e: Exception) {
            0
        }
    }

    fun parseSpeed(response: String): Int {
        return try {
            val bytes = extractBytes(response)
            if (bytes.isNotEmpty()) {
                bytes[0]
            } else 0
        } catch (e: Exception) {
            0
        }
    }

    fun parseTemperature(response: String): Float {
        return try {
            val bytes = extractBytes(response)
            if (bytes.isNotEmpty()) {
                bytes[0].toFloat() - 40
            } else 0f
        } catch (e: Exception) {
            0f
        }
    }

    fun parsePressure(response: String): Float {
        return try {
            val bytes = extractBytes(response)
            if (bytes.isNotEmpty()) {
                bytes[0].toFloat() * 3f
            } else 0f
        } catch (e: Exception) {
            0f
        }
    }

    /**
     * Converts 2 raw DTC hex bytes (e.g. 0x40, 0x35) into standard SAE J2012 DTC format (e.g. "C0035").
     */
    fun convertBytesToDTC(highByte: Int, lowByte: Int): String {
        val categoryChar = when ((highByte and 0xC0) shr 6) {
            0 -> 'P'
            1 -> 'C'
            2 -> 'B'
            3 -> 'U'
            else -> 'P'
        }

        val digit1 = (highByte and 0x30) shr 4
        val digit2 = String.format(Locale.US, "%X", highByte and 0x0F)
        val digit3And4 = String.format(Locale.US, "%02X", lowByte)

        return "$categoryChar$digit1$digit2$digit3And4"
    }

    fun parseDTC(response: String): List<String> {
        return try {
            val dtcList = mutableSetOf<String>()

            // 1. Direct Regex match for already formatted alphanumeric codes (e.g. C0035, P0100)
            val formattedMatcher = Regex("[PCBU][0-3][0-9A-F]{3}", RegexOption.IGNORE_CASE)
            formattedMatcher.findAll(response).forEach {
                dtcList.add(it.value.uppercase())
            }

            // 2. Extract raw hex bytes (excluding headers like 58 02, 59 02, BUS INIT, etc.)
            val bytes = extractBytes(response)
            if (bytes.size >= 2) {
                // Skip header bytes if present (e.g. 0x58, 0x02 or 0x59, 0x02)
                val startIndex = if (bytes.size >= 3 && (bytes[0] == 0x58 || bytes[0] == 0x59 || bytes[0] == 0x18 || bytes[0] == 0x19)) 2 else 0

                var i = startIndex
                while (i + 1 < bytes.size) {
                    val b1 = bytes[i]
                    val b2 = bytes[i + 1]

                    // Convert pair to DTC (e.g. 0x40, 0x35 -> C0035)
                    val dtcCode = convertBytesToDTC(b1, b2)

                    // Ignore zero codes (P0000) or invalid empty pairs
                    if (dtcCode != "P0000" && dtcCode.matches(Regex("[P|C|B|U][0-3][0-9A-F]{3}"))) {
                        dtcList.add(dtcCode)
                    }

                    // If 3-byte format is used (DTC High, DTC Low, Status Byte), advance by 3; otherwise advance by 2
                    if (i + 2 < bytes.size && (bytes[i + 2] in 0x00..0x0F || bytes[i + 2] == 0x20 || bytes[i + 2] == 0x28 || bytes[i + 2] == 0x01)) {
                        i += 3
                    } else {
                        i += 2
                    }
                }
            }

            dtcList.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractBytes(response: String): List<Int> {
        return try {
            response.trim()
                .replace(">", "")
                .replace("BUS INIT:", "")
                .replace("STOPPED", "")
                .replace("ERROR", "")
                .split(" ", "\n", "\r")
                .filter { it.trim().matches(Regex("[0-9A-F]{2}", RegexOption.IGNORE_CASE)) }
                .map { it.trim().toInt(16) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isValidResponse(response: String): Boolean {
        return response.isNotBlank() && !response.contains("ERROR") && !response.contains("UNABLE")
    }
}
