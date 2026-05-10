package com.example.s7opcuaapp.data.model

data class DirectPlcConfig(
    val endpointUrl: String = "opc.tcp://192.168.1.100:4840",
    val securityPolicy: DirectSecurityPolicy = DirectSecurityPolicy.None,
    val username: String = "",
    val password: String = "",
    val publishingIntervalMs: Double = 250.0,
    val samplingIntervalMs: Double = 250.0,
    val keepAliveSeconds: Int = 10
)

enum class DirectSecurityPolicy { None, Basic256Sha256 }
