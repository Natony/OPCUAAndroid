package com.example.s7opcuaapp.data.repository

import com.example.s7opcuaapp.data.model.DeviceEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for API URL building logic
 * Tests that the correct URL is constructed using apiPort (not OPC port)
 */
class ApiUrlBuilderTest {

    /**
     * Helper function that mimics the URL building logic in ApiRepositoryImpl
     */
    private fun buildServerUrl(device: DeviceEntity): String {
        return "http://${device.ipAddress}:${device.apiPort}"
    }

    @Test
    fun `buildServerUrl should use apiPort not port`() {
        val device = DeviceEntity(
            id = "1",
            name = "TestPLC",
            ipAddress = "192.168.1.100",
            port = 4840,      // OPC UA port
            apiPort = 5000    // REST API port
        )

        val url = buildServerUrl(device)

        assertEquals("http://192.168.1.100:5000", url)
        assertFalse(url.contains("4840"))
    }

    @Test
    fun `buildServerUrl with custom apiPort`() {
        val device = DeviceEntity(
            id = "1",
            name = "TestPLC",
            ipAddress = "10.0.0.50",
            port = 4840,
            apiPort = 8080
        )

        val url = buildServerUrl(device)

        assertEquals("http://10.0.0.50:8080", url)
    }

    @Test
    fun `buildServerUrl with default ports`() {
        val device = DeviceEntity(
            id = "1",
            name = "TestPLC",
            ipAddress = "192.168.137.1"
        )

        val url = buildServerUrl(device)

        // Should use default apiPort (5000), not default port (4840)
        assertEquals("http://192.168.137.1:5000", url)
    }

    @Test
    fun `buildServerUrl with localhost`() {
        val device = DeviceEntity(
            id = "1",
            name = "LocalPLC",
            ipAddress = "127.0.0.1",
            apiPort = 5000
        )

        val url = buildServerUrl(device)

        assertEquals("http://127.0.0.1:5000", url)
    }

    @Test
    fun `buildServerUrl with different IP formats`() {
        // Test with various IP address formats
        val testCases = listOf(
            Pair("192.168.0.1", 5000) to "http://192.168.0.1:5000",
            Pair("10.10.10.10", 3000) to "http://10.10.10.10:3000",
            Pair("172.16.0.1", 8080) to "http://172.16.0.1:8080"
        )

        testCases.forEach { (input, expected) ->
            val (ip, apiPort) = input
            val device = DeviceEntity(
                id = "1",
                name = "TestPLC",
                ipAddress = ip,
                apiPort = apiPort
            )

            val url = buildServerUrl(device)
            assertEquals(expected, url)
        }
    }

    @Test
    fun `port and apiPort should be independent`() {
        val device = DeviceEntity(
            id = "1",
            name = "TestPLC",
            ipAddress = "192.168.1.1",
            port = 4841,      // Different OPC port
            apiPort = 9999    // Different API port
        )

        val url = buildServerUrl(device)

        // URL should only contain apiPort
        assertTrue(url.contains("9999"))
        assertFalse(url.contains("4841"))
    }
}
