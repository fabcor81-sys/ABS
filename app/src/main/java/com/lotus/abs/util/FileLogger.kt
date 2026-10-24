package com.lotus.abs.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {
    private const val TAG = "LotusABSLogger"
    private const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024L // 2 MB max per log file
    private const val MAX_BACKUP_FILES = 5                   // Retain last 5 rotated log files

    private var logDir: File? = null
    private var currentLogFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun init(context: Context) {
        synchronized(this) {
            val dir = File(context.filesDir, "logs")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            logDir = dir
            currentLogFile = File(dir, "lotus_abs_current.log")
            i(TAG, "FileLogger initialized. Log path: ${currentLogFile?.absolutePath}")
        }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        writeLog("DEBUG", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        writeLog("INFO", tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        writeLog("WARN", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val fullMsg = if (throwable != null) "$message\n${Log.getStackTraceString(throwable)}" else message
        writeLog("ERROR", tag, fullMsg)
    }

    fun logTerminal(direction: String, commandOrResponse: String) {
        val tag = "TERMINAL"
        val formatted = "$direction $commandOrResponse"
        Log.d(tag, formatted)
        writeLog("TERM", tag, formatted)
    }

    private fun writeLog(level: String, tag: String, message: String) {
        synchronized(this) {
            val file = currentLogFile ?: return
            try {
                rotateLogsIfNeeded(file)
                val timestamp = dateFormat.format(Date())
                val logLine = "[$timestamp] [$level] [$tag]: $message\n"

                FileWriter(file, true).use { writer ->
                    writer.write(logLine)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to log file: ${e.message}")
            }
        }
    }

    private fun rotateLogsIfNeeded(file: File) {
        if (!file.exists() || file.length() < MAX_FILE_SIZE_BYTES) {
            return
        }

        val dir = logDir ?: return
        Log.i(TAG, "Log file size limit reached (${file.length()} bytes). Rotating logs...")

        // Delete oldest backup file if limit reached
        val oldestFile = File(dir, "lotus_abs_$MAX_BACKUP_FILES.log")
        if (oldestFile.exists()) {
            oldestFile.delete()
        }

        // Shift remaining backup files down
        for (i in MAX_BACKUP_FILES - 1 downTo 1) {
            val source = File(dir, "lotus_abs_$i.log")
            val target = File(dir, "lotus_abs_${i + 1}.log")
            if (source.exists()) {
                source.renameTo(target)
            }
        }

        // Rename current log file to lotus_abs_1.log
        val backup1 = File(dir, "lotus_abs_1.log")
        file.renameTo(backup1)

        // Create new empty lotus_abs_current.log
        currentLogFile = File(dir, "lotus_abs_current.log")
    }

    fun getLogFiles(): List<File> {
        val dir = logDir ?: return emptyList()
        return dir.listFiles { _, name -> name.endsWith(".log") }?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun getLogContent(): String {
        return try {
            currentLogFile?.takeIf { it.exists() }?.readText() ?: "No log file found."
        } catch (e: Exception) {
            "Error reading log file: ${e.message}"
        }
    }

    fun clearLogs() {
        synchronized(this) {
            val dir = logDir ?: return
            dir.listFiles()?.forEach { it.delete() }
            currentLogFile = File(dir, "lotus_abs_current.log")
            i(TAG, "Logs cleared.")
        }
    }
}
