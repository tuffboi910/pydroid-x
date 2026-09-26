package com.pydroidx.app

import org.junit.Assert.*
import org.junit.Test

class LocalAiConfigTest {
    @Test fun cpuNeverOffloadsLayers() {
        assertEquals(0, LocalAiConfig(backend="CPU",gpuLayers=99).effectiveGpuLayers())
    }

    @Test fun gpuKeepsAtLeastOneLayer() {
        assertEquals(1, LocalAiConfig(backend="GPU",gpuLayers=0).effectiveGpuLayers())
        assertEquals(42, LocalAiConfig(backend="GPU",gpuLayers=42).effectiveGpuLayers())
    }

    @Test fun settingsAreClampedBeforeNativeLoad() {
        val config=LocalAiConfig(
            gpuLayers=-3, contextTokens=10, cpuThreads=99, maxTokens=2,
            temperature=99f, topP=2f, minP=-1f, topK=-5
        ).normalized()
        assertEquals(0,config.gpuLayers)
        assertEquals(512,config.contextTokens)
        assertEquals(32,config.cpuThreads)
        assertEquals(16,config.maxTokens)
        assertEquals(5f,config.temperature)
        assertEquals(1f,config.topP)
        assertEquals(0f,config.minP)
        assertEquals(0,config.topK)
    }
}
