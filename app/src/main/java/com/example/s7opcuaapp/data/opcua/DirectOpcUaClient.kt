package com.example.s7opcuaapp.data.opcua

import android.util.Log
import com.example.s7opcuaapp.data.model.DirectPlcConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.future.await
import org.eclipse.milo.opcua.sdk.client.OpcUaClient
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription
import org.eclipse.milo.opcua.stack.client.DiscoveryClient
import org.eclipse.milo.opcua.stack.core.AttributeId
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * Direct OPC UA client wrapper around Eclipse Milo's OpcUaClient.
 * Used in Direct (backup) connection mode when the WPF server is unreachable.
 *
 * Lifecycle: connect() -> subscribeNodes() -> read updates from itemUpdates flow -> disconnect().
 * Reconnection is automatic with exponential backoff while the client is started.
 */
class DirectOpcUaClient(private val config: DirectPlcConfig) {

    companion object {
        private const val TAG = "DirectOpcUaClient"
        private const val APPLICATION_NAME = "OPCUAAndroid"
        private const val APPLICATION_URI = "urn:com.example.s7opcuaapp:client"
    }

    sealed class DirectState {
        object Disconnected : DirectState()
        object Connecting : DirectState()
        object Connected : DirectState()
        data class Faulted(val reason: String) : DirectState()
    }

    data class NodeUpdate(val nodeId: NodeId, val value: Any?, val statusGood: Boolean)

    /** Result of a write — exposes the raw StatusCode so callers can surface it to the user. */
    data class WriteResult(val success: Boolean, val statusCode: Long, val message: String) {
        companion object {
            val SkippedNoClient = WriteResult(false, 0L, "Not connected")
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var client: OpcUaClient? = null
    private var subscription: UaSubscription? = null
    private val isStarted = AtomicBoolean(false)

    // Cached data type per NodeId so writes can use the matching builtin type
    // (PLC int tags can be Int16/UInt16/Int32/UInt32 depending on the engineering tool).
    private val dataTypeCache = mutableMapOf<NodeId, NodeId>()

    private val _connectionState = MutableStateFlow<DirectState>(DirectState.Disconnected)
    val connectionState: StateFlow<DirectState> = _connectionState.asStateFlow()

    private val _itemUpdates = MutableSharedFlow<NodeUpdate>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val itemUpdates: SharedFlow<NodeUpdate> = _itemUpdates.asSharedFlow()

    /** Connect to PLC. Returns true on success. Marks the client as "started" so reconnection can resume after drops. */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        isStarted.set(true)
        connectInternal()
    }

    private suspend fun connectInternal(): Boolean {
        return try {
            _connectionState.value = DirectState.Connecting
            Log.d(TAG, "Discovering endpoints at ${config.endpointUrl}")

            val endpoints = DiscoveryClient.getEndpoints(config.endpointUrl).await()
            // Direct mode currently only supports SecurityPolicy.None (no PKI/cert provisioning on Android).
            val targetUri = SecurityPolicy.None.uri
            val endpoint = endpoints.firstOrNull { it.securityPolicyUri == targetUri }
                ?: error("PLC does not advertise a SecurityPolicy.None endpoint")

            val identityProvider = if (config.username.isBlank()) {
                AnonymousProvider()
            } else {
                UsernameProvider(config.username, config.password)
            }

            val cfg = OpcUaClientConfig.builder()
                .setEndpoint(endpoint)
                .setApplicationName(LocalizedText.english(APPLICATION_NAME))
                .setApplicationUri(APPLICATION_URI)
                .setIdentityProvider(identityProvider)
                .setKeepAliveInterval(uint(config.keepAliveSeconds * 1000L))
                .build()

            val newClient = OpcUaClient.create(cfg)
            newClient.connect().await()
            client = newClient

            _connectionState.value = DirectState.Connected
            Log.d(TAG, "Connected to ${config.endpointUrl}")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Connect failed: ${t.message}")
            _connectionState.value = DirectState.Faulted(mapError(t))
            false
        }
    }

    /**
     * Subscribe to a fixed list of nodes. Creates ONE subscription with all monitored items
     * batched in a single createMonitoredItems call. Item value-callbacks emit into [itemUpdates].
     */
    suspend fun subscribeNodes(nodeIds: List<NodeId>) = withContext(Dispatchers.IO) {
        val activeClient = client ?: error("Not connected")

        // Cache datatype for each node so write() can pick the right Variant constructor.
        cacheDataTypes(activeClient, nodeIds)

        val sub = activeClient.subscriptionManager
            .createSubscription(config.publishingIntervalMs)
            .await()
        subscription = sub

        val createRequests = nodeIds.mapIndexed { i, nodeId ->
            val readValueId = ReadValueId(
                nodeId,
                AttributeId.Value.uid(),
                null,
                QualifiedName.NULL_VALUE
            )
            val params = MonitoringParameters(
                uint(i),                     // clientHandle (used as index back into nodeIds)
                config.samplingIntervalMs,
                null,                        // no filter
                uint(10),                    // queueSize
                true                         // discardOldest
            )
            MonitoredItemCreateRequest(readValueId, MonitoringMode.Reporting, params)
        }

        sub.createMonitoredItems(
            TimestampsToReturn.Both,
            createRequests
        ) { item, idx ->
            item.setValueConsumer { _, dv ->
                val nodeId = nodeIds[idx]
                val value = dv.value?.value
                val good = dv.statusCode?.isGood == true
                // tryEmit is non-blocking thanks to DROP_OLDEST overflow strategy.
                _itemUpdates.tryEmit(NodeUpdate(nodeId, value, good))
            }
        }.await()

        Log.d(TAG, "Subscribed to ${nodeIds.size} nodes (publishing=${config.publishingIntervalMs}ms)")
    }

    /** Read all values once (used to populate buffer immediately on connect, before subscription deltas arrive). */
    suspend fun readAll(nodeIds: List<NodeId>): List<NodeUpdate> = withContext(Dispatchers.IO) {
        val activeClient = client ?: return@withContext emptyList()
        val readValues = nodeIds.map { id ->
            ReadValueId(id, AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE)
        }
        val response = activeClient.read(0.0, TimestampsToReturn.Both, readValues).await()
        val results = response.results ?: return@withContext emptyList()
        results.mapIndexed { i, dv ->
            NodeUpdate(nodeIds[i], dv.value?.value, dv.statusCode?.isGood == true)
        }
    }

    suspend fun writeBoolean(nodeId: NodeId, value: Boolean): WriteResult = withContext(Dispatchers.IO) {
        writeValue(nodeId, Variant(value))
    }

    /**
     * Write an integer. Tries Int32 (S7 DInt) first. If the server rejects with
     * Bad_TypeMismatch, retries as Int16 (S7 Int).
     */
    suspend fun writeInt(nodeId: NodeId, value: Int): WriteResult = withContext(Dispatchers.IO) {
        val r1 = writeValue(nodeId, Variant(value))
        if (r1.success) return@withContext r1
        if (isTypeMismatch(r1.statusCode)) {
            Log.w(TAG, "writeInt: Int32 rejected (${r1.message}), retrying as Int16")
            return@withContext writeValue(nodeId, Variant(value.toShort()))
        }
        r1
    }

    private fun isTypeMismatch(statusCode: Long): Boolean {
        // Bad_TypeMismatch = 0x80740000
        return (statusCode and 0xFFFFFFFFL) == 0x80740000L
    }

    private suspend fun writeValue(nodeId: NodeId, variant: Variant): WriteResult {
        val activeClient = client ?: run {
            Log.w(TAG, "writeValue skipped, client is null")
            return WriteResult.SkippedNoClient
        }
        return try {
            // DataValue(variant) sets StatusCode.GOOD and no timestamps — the form most
            // OPC UA servers expect for incoming writes.
            val statusList = activeClient.writeValues(
                listOf(nodeId),
                listOf(DataValue(variant))
            ).await()
            val status = statusList?.firstOrNull()
            val rawCode = status?.value ?: 0L
            val good = status?.isGood == true
            val message = status?.toString() ?: "no status returned"
            if (!good) {
                Log.w(TAG, "Write to $nodeId failed (variant=${variant.value}): $message")
            } else {
                Log.d(TAG, "Write to $nodeId OK")
            }
            WriteResult(good, rawCode, message)
        } catch (t: Throwable) {
            Log.w(TAG, "Write to $nodeId threw: ${t.message}")
            WriteResult(false, 0L, t.message ?: "exception during write")
        }
    }

    private suspend fun cacheDataTypes(activeClient: OpcUaClient, nodeIds: List<NodeId>) {
        try {
            val readValues = nodeIds.map { id ->
                ReadValueId(id, AttributeId.DataType.uid(), null, QualifiedName.NULL_VALUE)
            }
            val response = activeClient.read(0.0, TimestampsToReturn.Neither, readValues).await()
            response.results?.forEachIndexed { i, dv ->
                (dv.value?.value as? NodeId)?.let { dataTypeCache[nodeIds[i]] = it }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Datatype cache failed (continuing without it): ${t.message}")
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        isStarted.set(false)
        runCatching { subscription?.let { client?.subscriptionManager?.deleteSubscription(it.subscriptionId)?.await() } }
        runCatching { client?.disconnect()?.await() }
        subscription = null
        client = null
        dataTypeCache.clear()
        _connectionState.value = DirectState.Disconnected
        Log.d(TAG, "Disconnected")
    }

    /**
     * Best-effort reconnect loop. Caller (repository) invokes this in a background coroutine
     * after observing a transition to Faulted. Backoff caps at 30s.
     */
    suspend fun reconnectWithBackoff(nodeIdsToResubscribe: List<NodeId>) {
        var attempt = 0
        while (isStarted.get()) {
            val delayMs = min(30_000L, 1_000L * (1L shl min(attempt, 5)))
            Log.d(TAG, "Reconnect attempt ${attempt + 1} in ${delayMs}ms")
            delay(delayMs)
            if (!isStarted.get()) return
            val ok = connectInternal()
            if (ok) {
                runCatching { subscribeNodes(nodeIdsToResubscribe) }
                    .onFailure {
                        Log.w(TAG, "Resubscribe after reconnect failed: ${it.message}")
                        _connectionState.value = DirectState.Faulted(it.message ?: "resubscribe failed")
                    }
                if (_connectionState.value is DirectState.Connected) return
            }
            attempt++
        }
    }

    private fun mapError(t: Throwable): String {
        val msg = t.message ?: "unknown"
        return when {
            msg.contains("Bad_NotConnected", ignoreCase = true) ||
                msg.contains("ConnectionRejected", ignoreCase = true) ->
                "Cannot reach PLC at ${config.endpointUrl}"
            msg.contains("UserAccessDenied", ignoreCase = true) ->
                "Invalid OPC UA credentials"
            msg.contains("SecurityChecksFailed", ignoreCase = true) ->
                "Security policy mismatch"
            msg.contains("TooManySessions", ignoreCase = true) ->
                "PLC has too many active sessions"
            else -> msg
        }
    }
}
