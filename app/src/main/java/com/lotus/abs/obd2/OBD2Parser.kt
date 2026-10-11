package com.lotus.abs.obd2

object OBD2Commands {
    // ELM327 Commands
    const val RESET = "AT Z"
    const val ECHO_OFF = "AT E0"
    const val LINE_FEEDS_OFF = "AT L0"
    const val TIMEOUT = "AT ST 0A"
    const val SET_PROTOCOL = "AT SP 6"  // KWP2000 (ISO 14230-4)

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
    const val READ_DTC = "19 02"  // Read DTC by status
    const val CLEAR_DTC = "14"     // Clear DTC
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

    fun parseDTC(response: String): List<String> {
        return try {
            val dtcList = mutableListOf<String>()
            val lines = response.split("\n")
            for (line in lines) {
                if (line.length >= 4 && !line.startsWith(">")) {
                    val code = line.substring(0, 4)
                    if (code.matches(Regex("[0-9A-F]{4}"))) {
                        dtcList.add(code)
                    }
                }
            }
            dtcList
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractBytes(response: String): List<Int> {
        return try {
            response.trim()
                .split(" ", "\n")
                .filter { it.matches(Regex("[0-9A-F]{2}")) }
                .map { it.toInt(16) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isValidResponse(response: String): Boolean {
        return response.isNotBlank() && !response.contains("ERROR") && !response.contains("UNABLE")
    }
}
