package com.example.s7opcuaapp.viewmodel

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ConfigUiState validation logic
 */
class ConfigViewModelTest {

    @Test
    fun `ConfigUiState default values should be correct`() {
        val state = ConfigUiState()

        assertEquals("", state.newDeviceName)
        assertEquals("", state.newDeviceIp)
        assertEquals("4840", state.newDevicePort)
        assertEquals("5000", state.newDeviceApiPort)
        assertEquals("", state.newDeviceUsername)
        assertEquals("", state.newDevicePassword)
        assertNull(state.errorMessage)
        assertFalse(state.isEditMode)
        assertNull(state.editingDevice)
        assertTrue(state.deviceList.isEmpty())
        assertNull(state.currentDevice)
    }

    @Test
    fun `ConfigUiState copy should preserve apiPort`() {
        val original = ConfigUiState(
            newDeviceApiPort = "8080"
        )

        val copied = original.copy(newDeviceName = "TestDevice")

        assertEquals("8080", copied.newDeviceApiPort)
        assertEquals("TestDevice", copied.newDeviceName)
    }

    @Test
    fun `port validation - valid port`() {
        val validPorts = listOf("80", "443", "4840", "5000", "8080", "65535")

        validPorts.forEach { portStr ->
            val port = portStr.toIntOrNull()
            assertNotNull("Port $portStr should parse to int", port)
            assertTrue("Port $port should be positive", port!! > 0)
        }
    }

    @Test
    fun `port validation - invalid port`() {
        val invalidPorts = listOf("", "abc", "-1", "0", "65536", "99999")

        invalidPorts.forEach { portStr ->
            val port = portStr.toIntOrNull()
            val isValid = port != null && port > 0 && port <= 65535

            assertFalse("Port '$portStr' should be invalid", isValid)
        }
    }

    @Test
    fun `apiPort validation - valid apiPort`() {
        val validApiPorts = listOf("3000", "5000", "8000", "8080", "9000")

        validApiPorts.forEach { apiPortStr ->
            val apiPort = apiPortStr.toIntOrNull()
            assertNotNull("API Port $apiPortStr should parse to int", apiPort)
            assertTrue("API Port $apiPort should be positive", apiPort!! > 0)
        }
    }

    @Test
    fun `form validation - all fields required`() {
        val testCases = listOf(
            // name, ip, port, apiPort -> isValid
            Triple("", "192.168.1.1", "4840") to "5000" to false,         // Empty name
            Triple("PLC", "", "4840") to "5000" to false,                 // Empty IP
            Triple("PLC", "192.168.1.1", "") to "5000" to false,          // Empty port
            Triple("PLC", "192.168.1.1", "4840") to "" to false,          // Empty apiPort
            Triple("PLC", "192.168.1.1", "4840") to "5000" to true        // All valid
        )

        testCases.forEach { (inputs, expectedValid) ->
            val (triple, apiPort) = inputs
            val (name, ip, port) = triple

            val isValid = name.isNotEmpty() &&
                    ip.isNotEmpty() &&
                    port.isNotEmpty() &&
                    apiPort.isNotEmpty() &&
                    port.toIntOrNull() != null &&
                    port.toInt() > 0 &&
                    apiPort.toIntOrNull() != null &&
                    apiPort.toInt() > 0

            assertEquals(
                "Validation for name='$name', ip='$ip', port='$port', apiPort='$apiPort'",
                expectedValid,
                isValid
            )
        }
    }

    @Test
    fun `IP address format validation`() {
        val validIPs = listOf(
            "192.168.1.1",
            "10.0.0.1",
            "172.16.0.1",
            "127.0.0.1",
            "192.168.137.1"
        )

        val ipPattern = Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")

        validIPs.forEach { ip ->
            assertTrue("IP $ip should be valid", ipPattern.matches(ip))
        }
    }

    @Test
    fun `ConfigUiState with all fields set`() {
        val state = ConfigUiState(
            newDeviceName = "Production PLC",
            newDeviceIp = "192.168.1.100",
            newDevicePort = "4840",
            newDeviceApiPort = "5000",
            newDeviceUsername = "admin",
            newDevicePassword = "password123",
            isEditMode = false,
            errorMessage = null
        )

        assertEquals("Production PLC", state.newDeviceName)
        assertEquals("192.168.1.100", state.newDeviceIp)
        assertEquals("4840", state.newDevicePort)
        assertEquals("5000", state.newDeviceApiPort)
        assertEquals("admin", state.newDeviceUsername)
        assertEquals("password123", state.newDevicePassword)
    }
}
