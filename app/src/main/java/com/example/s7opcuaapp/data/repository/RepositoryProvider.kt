package com.example.s7opcuaapp.data.repository

import android.util.Log
import com.example.s7opcuaapp.data.api.ConnectionStatusDto
import com.example.s7opcuaapp.data.api.PlcApiClient
import com.example.s7opcuaapp.data.buffer.PlcDataBuffer
import com.example.s7opcuaapp.data.local.ConnectionMode
import com.example.s7opcuaapp.data.local.PrefsManager
import com.example.s7opcuaapp.data.model.DeviceEntity
import com.example.s7opcuaapp.util.PerformanceMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Holds the active [S7Repository] implementation and lets callers swap implementations
 * at runtime without re-injecting (used to flip between API and Direct OPC UA modes).
 *
 * ControlViewModel reads the active impl via [current] on each operation; ConfigViewModel
 * calls [switchTo] when the user toggles the connection mode in ConfigScreen.
 */
@Singleton
class RepositoryProvider @Inject constructor(
    private val prefsManager: PrefsManager,
    private val dataBuffer: PlcDataBuffer,
    private val performanceMonitor: PerformanceMonitor,
    private val apiClientProvider: Provider<PlcApiClient>
) {
    companion object {
        private const val TAG = "RepositoryProvider"
    }

    private val providerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _activeRepo = MutableStateFlow<S7Repository>(buildForCurrentMode())
    val activeRepo: StateFlow<S7Repository> = _activeRepo

    /**
     * Composite flows that follow whichever impl is currently active. Subscribers (e.g. ControlViewModel)
     * collect these once and automatically re-route to the new impl after a runtime mode switch.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val loadingPercent: StateFlow<Int> = _activeRepo
        .flatMapLatest { repo -> repoLoadingPercent(repo) }
        .stateIn(providerScope, SharingStarted.Eagerly, 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val serverConnectionStatus: StateFlow<ConnectionStatusDto?> = _activeRepo
        .flatMapLatest { repo -> repoServerConnectionStatus(repo) }
        .stateIn(providerScope, SharingStarted.Eagerly, null)

    fun current(): S7Repository = _activeRepo.value

    fun currentMode(): ConnectionMode = prefsManager.getConnectionMode()

    /** Synchronously stop the old impl, persist the new mode, and replace the active impl. */
    fun switchTo(mode: ConnectionMode) {
        if (prefsManager.getConnectionMode() == mode) return
        Log.d(TAG, "Switching connection mode -> $mode")
        runCatching { _activeRepo.value.stop() }
            .onFailure { Log.w(TAG, "Old repo stop threw: ${it.message}") }
        prefsManager.saveConnectionMode(mode)
        _activeRepo.value = buildForCurrentMode()
    }

    /** Rebuild the active impl after the user edits Direct config without changing mode. */
    fun rebuildActive() {
        Log.d(TAG, "Rebuilding active repo (mode=${prefsManager.getConnectionMode()})")
        runCatching { _activeRepo.value.stop() }
            .onFailure { Log.w(TAG, "Old repo stop threw: ${it.message}") }
        _activeRepo.value = buildForCurrentMode()
    }

    /** Convenience wrappers ControlViewModel needs (the API impl exposes more than the interface). */
    fun isConnected(): Boolean = when (val r = current()) {
        is ApiRepositoryImpl -> r.isConnected()
        is DirectOpcUaRepositoryImpl -> r.isConnected()
        else -> false
    }

    fun observeLoadingPercent(): StateFlow<Int> = loadingPercent

    fun observeServerConnectionStatus(): StateFlow<ConnectionStatusDto?> = serverConnectionStatus

    private fun repoLoadingPercent(repo: S7Repository): StateFlow<Int> = when (repo) {
        is ApiRepositoryImpl -> repo.observeLoadingPercent()
        is DirectOpcUaRepositoryImpl -> repo.observeLoadingPercent()
        else -> MutableStateFlow(0)
    }

    private fun repoServerConnectionStatus(repo: S7Repository): StateFlow<ConnectionStatusDto?> = when (repo) {
        is ApiRepositoryImpl -> repo.serverConnectionStatus
        is DirectOpcUaRepositoryImpl -> repo.serverConnectionStatus
        else -> MutableStateFlow(null)
    }

    suspend fun start() {
        when (val r = current()) {
            is ApiRepositoryImpl -> r.start()
            is DirectOpcUaRepositoryImpl -> r.start()
        }
    }

    fun stop() {
        runCatching { current().stop() }
    }

    private fun buildForCurrentMode(): S7Repository {
        return when (prefsManager.getConnectionMode()) {
            ConnectionMode.API -> {
                val device = prefsManager.getCurrentDevice() ?: defaultDevice()
                ApiRepositoryImpl(
                    device = device,
                    dataBuffer = dataBuffer,
                    performanceMonitor = performanceMonitor,
                    sharedApiClient = apiClientProvider.get()
                )
            }
            ConnectionMode.Direct -> {
                val device = prefsManager.getCurrentDevice() ?: defaultDevice()
                DirectOpcUaRepositoryImpl(
                    device = device,
                    config = prefsManager.getDirectPlcConfig(),
                    dataBuffer = dataBuffer,
                    performanceMonitor = performanceMonitor
                )
            }
        }
    }

    private fun defaultDevice() = DeviceEntity(
        id = "default",
        name = "Default Device",
        ipAddress = "192.168.1.100",
        port = 4840,
        opcUsername = "",
        opcPassword = "",
        useOpcUa = true
    )

}
