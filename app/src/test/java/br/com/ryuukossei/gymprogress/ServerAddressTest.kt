package br.com.ryuukossei.gymprogress

import br.com.ryuukossei.gymprogress.data.remote.GymApi
import org.junit.Assert.*
import org.junit.Test

class ServerAddressTest {
    @Test fun normalizesHostAndPreservesDeploymentPath() {
        assertEquals("https://example.com/gym", GymApi.normalizeBaseUrl("  HTTPS://Example.COM/gym/  "))
        assertEquals("https://example.com", GymApi.normalizeBaseUrl("example.com/"))
    }
    @Test fun rejectsPlaintextAndUnexpectedProtocols() {
        listOf("http://example.com", "ftp://example.com", "", "https://").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { GymApi.normalizeBaseUrl(url) }
        }
    }
    @Test fun rejectsCredentialsQueriesAndFragments() {
        listOf("https://user:password@example.com", "https://example.com?token=value", "https://example.com#fragment").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { GymApi.normalizeBaseUrl(url) }
        }
    }
}
