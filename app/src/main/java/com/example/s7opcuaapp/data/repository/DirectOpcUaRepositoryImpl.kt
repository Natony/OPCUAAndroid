package com.example.s7opcuaapp.data.repository

import android.util.Log
import com.example.s7opcuaapp.data.PlcConfig
import com.example.s7opcuaapp.data.api.ConnectionStatusDto
import com.example.s7opcuaapp.data.buffer.PlcDataBuffer
import com.example.s7opcuaapp.data.model.DeviceEntity
import com.example.s7opcuaapp.data.model.DirectPlcConfig
import com.example.s7opcuaapp.data.model.PlcData
import com.example.s7opcuaapp.data.opcua.DirectOpcUaClient
import com.example.s7opcuaapp.util.LoadingTracker
import com.example.s7opcuaapp.util.PerformanceMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Direct OPC UA repository — backup connection mode.
 * Bypasses the WPF server and talks directly to the PLC via Eclipse Milo.
 * Mirrors [ApiRepositoryImpl]'s public surface so ControlViewModel needs no special-casing.
 */
class DirectOpcUaRepositoryImpl(
    private var device: DeviceEntity,
    private val config: DirectPlcConfig,
    private val dataBuffer: PlcDataBuffer,
    private val performanceMonitor: PerformanceMonitor
) : S7Repository {

    companion object {
        private const val TAG = "DirectOpcUaRepository"
        private const val NS = 4
    }

    // Same ns=4 numeric mapping the WPF server uses, kept in lockstep with ApiRepositoryImpl.
    private val boolNodeIds: List<NodeId> = PlcConfig.BOOL_NODE_RANGE.map { NodeId(NS, uint(it)) }
    private val intNodeIds: List<NodeId>  = PlcConfig.INT_NODE_RANGE.map { NodeId(NS, uint(it)) }
    private val allNodeIds: List<NodeId>  = boolNodeIds + intNodeIds

    private val totalNodes = allNodeIds.size
    private val loadingTracker = LoadingTracker<NodeId>(totalNodes)

    private val client = DirectOpcUaClient(config)

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectionMutex = Mutex()
    private val isStarted = AtomicBoolean(false)
    private val isConnectedFlag = AtomicBoolean(false)

    private var connectionJob: Job? = null
    private var stateObserverJob: Job? = null
    private var updateCollectorJob: Job? = null

    // Kept for parity with ApiRepositoryImpl — ControlViewModel collects this.
    private val _serverConnectionStatus = MutableStateFlow<ConnectionStatusDto?>(null)
    val serverConnectionStatus: StateFlow<ConnectionStatusDto?> = _serverConnectionStatus

    override fun observePlcData(): Flow<PlcData> = dataBuffer.dataFlow

    fun isConnected(): Boolean = isConnectedFlag.get()
    fun observeLoadingPercent(): StateFlow<Int> = loadingTracker.percent
    fun observeDirectState(): StateFlow<DirectOpcUaClient.DirectState> = client.connectionState

    /** Connect to PLC, prime the buffer with an initial read, and start a subscription for live updates. */
    suspend fun start() = connectionMutex.withLock {
        if (isStarted.get()) {
            Log.d(TAG, "Repository already started")
            return@withLock
        }
        Log.d(TAG, "Starting Direct OPC UA repository (endpoint=${config.endpointUrl})")
        isStarted.set(true)

        loadingTracker.reset()
        dataBuffer.clear()

        cancelJobs()

        startStateObserver()
        startUpdateCollector()

        connectionJob = repositoryScope.launch { startConnectionLoop() }
    }

    private suspend fun startConnectionLoop() {
        try {
            val ok = client.connect()
            if (!ok) {
                Log.w(TAG, "Initial connect failed")
                isConnectedFlag.set(false)
                // Let reconnectWithBackoff drive recovery.
                client.reconnectWithBackoff(allNodeIds)
                return
            }
            client.subscribeNodes(allNodeIds)
            // Prime buffer with an initial read so UI shows real values before the first delta arrives.
            primeBufferFromInitialRead()
            isConnectedFlag.set(true)
        } catch (t: Throwable) {
            Log.e(TAG, "Connection loop failed", t)
            isConnectedFlag.set(false)
            loadingTracker.setError()
        }
    }

    private suspend fun primeBufferFromInitialRead() {
        val initial = client.readAll(allNodeIds)
        initial.forEach { applyUpdate(it) }
    }

    private fun startUpdateCollector() {
        updateCollectorJob = repositoryScope.launch {
            client.itemUpdates.collect { update -> applyUpdate(update) }
        }
    }

    private fun applyUpdate(update: DirectOpcUaClient.NodeUpdate) {
        if (!update.statusGood) return
        val identifier = (update.nodeId.identifier as? org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger)
            ?.toInt() ?: return
        when {
            identifier in PlcConfig.BOOL_NODE_RANGE -> {
                val index = identifier - PlcConfig.BOOL_NODE_START
                val value = parseBoolean(update.value) ?: return
                dataBuffer.updateBool(index, value)
                loadingTracker.markLoaded(update.nodeId)
            }
            identifier in PlcConfig.INT_NODE_RANGE -> {
                val index = identifier - PlcConfig.INT_NODE_START
                val value = parseInt(update.value) ?: return
                dataBuffer.updateInt(index, value)
                loadingTracker.markLoaded(update.nodeId)
            }
        }
    }

    private fun startStateObserver() {
        stateObserverJob = repositoryScope.launch {
            client.connectionState.collect { state ->
                when (state) {
                    is DirectOpcUaClient.DirectState.Connected -> {
                        isConnectedFlag.set(true)
                    }
                    is DirectOpcUaClient.DirectState.Faulted,
                    is DirectOpcUaClient.DirectState.Disconnected -> {
                        isConnectedFlag.set(false)
                    }
                    is DirectOpcUaClient.DirectState.Connecting -> { /* keep current flag */ }
                }
            }
        }
    }

    override suspend fun writeBoolean(index: Int, value: Boolean) = withContext(Dispatchers.IO) {
        if (!isConnectedFlag.get()) throw Exception("Not connected to PLC (Direct mode)")
        if (index !in boolNodeIds.indices) return@withContext

        performanceMonitor.recordWriteCommand()
        val startTime = System.currentTimeMillis()
        try {
            val nodeId = boolNodeIds[index]
            val result = client.writeBoolean(nodeId, value)
            if (!result.success) {
                throw Exception(formatWriteError(result))
            }

            // Optimistic update — same pattern as ApiRepositoryImpl.
            dataBuffer.updateBool(index, value)

            val writeTime = System.currentTimeMillis() - startTime
            performanceMonitor.recordNetworkLatency(writeTime)
            Log.d(TAG, "WriteBoolean[$index]=$value in ${writeTime}ms (direct)")
        } catch (e: Exception) {
            Log.e(TAG, "WriteBoolean[$index] failed: ${e.message}")
            throw e
        }
    }

    override suspend fun writeInt(index: Int, value: Int) = withContext(Dispatchers.IO) {
        if (!isConnectedFlag.get()) throw Exception("Not connected to PLC (Direct mode)")
        if (index !in intNodeIds.indices) return@withContext

        performanceMonitor.recordWriteCommand()
        val startTime = System.currentTimeMillis()
        try {
            val nodeId = intNodeIds[index]
            val result = client.writeInt(nodeId, value)
            if (!result.success) {
                throw Exception(formatWriteError(result))
            }

            val writeTime = System.currentTimeMillis() - startTime
            performanceMonitor.recordNetworkLatency(writeTime)
            Log.d(TAG, "WriteInt[$index]=$value in ${writeTime}ms (direct)")
        } catch (e: Exception) {
            Log.e(TAG, "WriteInt[$index] failed: ${e.message}")
            throw e
        }
    }

    override fun stop() {
        Log.d(TAG, "Stop called")
        runBlocking {
            try {
                isStarted.set(false)
                isConnectedFlag.set(false)
                cancelJobs()
                client.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error during stop", e)
            }
        }
    }

    override fun updateDevice(device: DeviceEntity) {
        // No-op: Direct mode reads its endpoint/credentials from DirectPlcConfig (PrefsManager).
        this.device = device
    }

    private fun cancelJobs() {
        connectionJob?.cancel(); connectionJob = null
        updateCollectorJob?.cancel(); updateCollectorJob = null
        stateObserverJob?.cancel(); stateObserverJob = null
    }

    private fun formatWriteError(result: DirectOpcUaClient.WriteResult): String =
        if (result.statusCode != 0L) "PLC rejected write: ${result.message}"
        else "Write call failed: ${result.message}"

    private fun parseBoolean(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.equals("true", ignoreCase = true) || value == "1"
        else -> null
    }

    private fun parseInt(value: Any?): Int? = when (value) {
        is Number -> value.toInt()
        is Boolean -> if (value) 1 else 0
        is String -> value.toIntOrNull()
        else -> null
    }
}
