package com.renotube.tv.crash

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

object CrashHandler {
    private const val TAG = "CrashHandler"
    private const val PREFS_NAME = "renotube_crash_prefs"
    private const val KEY_LAST_CRASH = "last_crash_log"

    fun install(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                handleCrash(context, thread, throwable)
            } catch (e: Throwable) {
                Log.e(TAG, "Error inside CrashHandler: ${e.message}", e)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
        Log.i(TAG, "RenoTube CrashHandler installed successfully")
    }

    private fun handleCrash(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val timeString = dateFormat.format(Date())

        val report = buildString {
            appendLine("═══════════════════════════════════════════════════")
            appendLine("           RENOTUBE TV FATAL CRASH LOG            ")
            appendLine("═══════════════════════════════════════════════════")
            appendLine("Timestamp      : $timeString")
            appendLine("App Version    : 1.4.0-leanback")
            appendLine("Device Model   : ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android OS     : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Supported ABIs : ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("Crashing Thread: ${thread.name} (id: ${thread.id})")
            appendLine("Exception Type : ${throwable.javaClass.name}")
            appendLine("Error Message  : ${throwable.message ?: "No error message"}")
            appendLine("───────────────── STACK TRACE ─────────────────────")
            appendLine(stackTrace)
            appendLine("═══════════════════════════════════════════════════")
        }

        Log.e(TAG, report)

        // 1. Write crash log to internal filesDir
        try {
            val internalFile = File(context.filesDir, "crash_log.txt")
            internalFile.writeText(report)
        } catch (_: Exception) {}

        // 2. Write crash log to externalFilesDir (easy to pull/read)
        try {
            val externalDir = context.getExternalFilesDir(null)
            if (externalDir != null) {
                val externalFile = File(externalDir, "crash_log.txt")
                externalFile.writeText(report)
            }
        } catch (_: Exception) {}

        // 3. Save to SharedPreferences
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_LAST_CRASH, report).commit()
        } catch (_: Exception) {}

        // 4. Launch CrashActivity on TV screen
        try {
            val intent = Intent(context, CrashActivity::class.java).apply {
                putExtra(CrashActivity.EXTRA_CRASH_REPORT, report)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start CrashActivity: ${e.message}")
        }

        // 5. Terminate the crashing process cleanly
        Process.killProcess(Process.myPid())
        exitProcess(10)
    }

    fun getLastCrashReport(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LAST_CRASH, null)
        if (!saved.isNullOrBlank()) return saved

        val internalFile = File(context.filesDir, "crash_log.txt")
        if (internalFile.exists()) {
            return internalFile.readText()
        }
        return null
    }

    fun clearCrashLog(context: Context) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(KEY_LAST_CRASH).apply()
            File(context.filesDir, "crash_log.txt").delete()
        } catch (_: Exception) {}
    }
}
