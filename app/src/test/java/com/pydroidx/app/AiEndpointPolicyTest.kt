package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AiEndpointPolicyTest {
    @Test fun customEndpointNeverFallsBackToOpenAi() {
        assertEquals("", AiEndpointPolicy.normalizeForStorage("Custom", ""))
        assertEquals("http://local.invalid", AiEndpointPolicy.normalizeForStorage("Custom", "http://local.invalid"))
    }

    @Test fun builtInProvidersUseSafeDefaultForInvalidEndpoint() {
        assertEquals(
            AiEndpointPolicy.DEFAULT_OPENAI_ENDPOINT,
            AiEndpointPolicy.normalizeForStorage("OpenAI", "not-a-url")
        )
    }

    @Test fun customRequestsRequireHttps() {
        assertEquals("https://example.test/v1/chat", AiEndpointPolicy.requireSafeCustom(" https://example.test/v1/chat "))
        assertThrows(IllegalArgumentException::class.java) {
            AiEndpointPolicy.requireSafeCustom("http://example.test")
        }
    }
}
