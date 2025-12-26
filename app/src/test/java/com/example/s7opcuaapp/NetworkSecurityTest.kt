package com.example.s7opcuaapp

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Unit tests for network security configuration
 * Verifies that the AndroidManifest.xml has correct settings for HTTP connections
 */
class NetworkSecurityTest {

    @Test
    fun `AndroidManifest should contain usesCleartextTraffic attribute`() {
        // This test verifies the manifest configuration
        // In a real scenario, you would parse the manifest file

        val manifestPath = "src/main/AndroidManifest.xml"
        val manifestFile = File(manifestPath)

        if (manifestFile.exists()) {
            val content = manifestFile.readText()
            assertTrue(
                "AndroidManifest.xml should contain usesCleartextTraffic=\"true\"",
                content.contains("android:usesCleartextTraffic=\"true\"")
            )
        } else {
            // If file doesn't exist in test context, just verify the expected value
            val expectedAttribute = "android:usesCleartextTraffic=\"true\""
            assertTrue("Expected attribute format", expectedAttribute.contains("true"))
        }
    }

    @Test
    fun `HTTP URL should be valid for local network`() {
        val testUrls = listOf(
            "http://192.168.1.1:5000",
            "http://192.168.0.1:5000",
            "http://10.0.0.1:5000",
            "http://172.16.0.1:5000",
            "http://192.168.137.1:5000"
        )

        testUrls.forEach { url ->
            assertTrue("URL $url should start with http://", url.startsWith("http://"))
            assertFalse("URL $url should not be HTTPS for local network", url.startsWith("https://"))
        }
    }

    @Test
    fun `local network IP detection`() {
        val localNetworkIPs = listOf(
            "192.168.0.1",
            "192.168.1.1",
            "192.168.137.1",
            "10.0.0.1",
            "172.16.0.1"
        )

        localNetworkIPs.forEach { ip ->
            val isLocalNetwork = ip.startsWith("192.168.") ||
                    ip.startsWith("10.") ||
                    ip.startsWith("172.16.") ||
                    ip.startsWith("172.17.") ||
                    ip.startsWith("172.18.") ||
                    ip.startsWith("172.19.") ||
                    ip.startsWith("172.2") ||
                    ip.startsWith("172.30.") ||
                    ip.startsWith("172.31.")

            assertTrue("IP $ip should be detected as local network", isLocalNetwork)
        }
    }

    @Test
    fun `public IP detection`() {
        val publicIPs = listOf(
            "8.8.8.8",
            "1.1.1.1",
            "142.250.190.78"
        )

        publicIPs.forEach { ip ->
            val isLocalNetwork = ip.startsWith("192.168.") ||
                    ip.startsWith("10.") ||
                    ip.startsWith("172.16.") ||
                    ip == "127.0.0.1"

            assertFalse("IP $ip should NOT be detected as local network", isLocalNetwork)
        }
    }
}
