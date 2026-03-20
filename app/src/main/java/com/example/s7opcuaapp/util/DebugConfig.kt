package com.example.s7opcuaapp.util

import android.util.Log

/**
 * Configuration for debug logging.
 * Set VERBOSE_LOGGING to true when debugging connection issues.
 */
object DebugConfig {
    // Set to true to enable verbose logging
    const val VERBOSE_LOGGING = false

    // Individual log categories
    const val LOG_SIGNALR = false
    const val LOG_DATA_BUFFER = false
    const val LOG_CONNECTION_MONITOR = false
    const val LOG_LOCK_STATUS = false

    // Helper functions
    inline fun logVerbose(tag: String, message: () -> String) {
        if (VERBOSE_LOGGING) {
            Log.d(tag, message())
        }
    }

    inline fun logSignalR(tag: String, message: () -> String) {
        if (LOG_SIGNALR) {
            Log.d(tag, message())
        }
    }

    inline fun logDataBuffer(tag: String, message: () -> String) {
        if (LOG_DATA_BUFFER) {
            Log.v(tag, message())
        }
    }

    inline fun logConnectionMonitor(tag: String, message: () -> String) {
        if (LOG_CONNECTION_MONITOR) {
            Log.d(tag, message())
        }
    }

    inline fun logLockStatus(tag: String, message: () -> String) {
        if (LOG_LOCK_STATUS) {
            Log.d(tag, message())
        }
    }
}
