package com.example.s7opcuaapp.data.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DeviceEntity data class
 */
class DeviceEntityTest {

    @Test
    fun `default port should be 4840`() {
        val device = DeviceEntity(
            id = "1",
            name = "TestPLC",
            ipAddress = "192.168.1.100"
        )
        assertEquals(4840, device.port)
    }

    @Test
    fun `default apiPort should be 5000`() {
        val device = DeviceEntity(
            id = "1",
            name = "TestPLC",
            ipAddress = "192.168.1.100"
        )
        assertEquals(5000, device.apiPort)
    }

    @Test
    fun `custom port values should be preserved`() {
        val device = DeviceEntity(
            id = "1",
            name = "TestPLC",
            ipAddress = "192.168.1.100",
            port = 4841,
            apiPort = 8080
        )
        assertEquals(4841, device.port)
        assertEquals(8080, device.apiPort)
    }

    @Test
    fun `copy should preserve apiPort`() {
        val original = DeviceEntity(
            id = "1",
            name = "TestPLC",
            ipAddress = "192.168.1.100",
            apiPort = 3000
        )
        val copied = original.copy(name = "NewName")
        assertEquals(3000, copied.apiPort)
        assertEquals("NewName", copied.name)
    }

    @Test
    fun `copy should allow changing apiPort`() {
        val original = DeviceEntity(
            id = "1",
            name = "TestPLC",
            ipAddress = "192.168.1.100",
            apiPort = 3000
        )
        val copied = original.copy(apiPort = 9000)
        assertEquals(9000, copied.apiPort)
    }

    @Test
    fun `device with all fields should be created correctly`() {
        val device = DeviceEntity(
            id = "device123",
            name = "Production PLC",
            ipAddress = "10.0.0.50",
            port = 4840,
            apiPort = 5000,
            opcUsername = "admin",
            opcPassword = "secret123",
            useOpcUa = true
        )

        assertEquals("device123", device.id)
        assertEquals("Production PLC", device.name)
        assertEquals("10.0.0.50", device.ipAddress)
        assertEquals(4840, device.port)
        assertEquals(5000, device.apiPort)
        assertEquals("admin", device.opcUsername)
        assertEquals("secret123", device.opcPassword)
        assertTrue(device.useOpcUa)
    }

    @Test
    fun `equality should consider apiPort`() {
        val device1 = DeviceEntity(
            id = "1",
            name = "PLC",
            ipAddress = "192.168.1.1",
            apiPort = 5000
        )
        val device2 = DeviceEntity(
            id = "1",
            name = "PLC",
            ipAddress = "192.168.1.1",
            apiPort = 5000
        )
        val device3 = DeviceEntity(
            id = "1",
            name = "PLC",
            ipAddress = "192.168.1.1",
            apiPort = 8080
        )

        assertEquals(device1, device2)
        assertNotEquals(device1, device3)
    }
}
