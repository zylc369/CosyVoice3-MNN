package com.cosyvoice.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CosyVoiceHardwarePolicyTest {

    @Test
    fun qualcommWithHexagonUsesHexagon() {
        val plan = CosyVoiceHardwarePolicy.recommend(capabilities("SM8850", hexagon = true))

        assertEquals("hexagon", plan.llmBackend)
        assertEquals("opencl", plan.flowBackend)
        assertEquals("cpu", plan.hiftCoreBackend)
        assertTrue(plan.npuStatus.contains("q_proj"))
    }

    @Test
    fun hexagonIsOpenedForAnyQualcommWhenLibraryLoads() {
        val plan = CosyVoiceHardwarePolicy.recommend(capabilities("SM8750", hexagon = true))

        assertEquals("hexagon", plan.llmBackend)
    }

    @Test
    fun qualcommWithoutHexagonLibraryStaysOnCpu() {
        val plan = CosyVoiceHardwarePolicy.recommend(capabilities("SM8750", hexagon = false))

        assertEquals("cpu", plan.llmBackend)
    }

    @Test
    fun nonQualcommNeverUsesHexagon() {
        val plan = CosyVoiceHardwarePolicy.recommend(capabilities("MT6991", hexagon = true))

        assertEquals("cpu", plan.llmBackend)
    }

    @Test
    fun missingOpenClFallsBackToCpuFlow() {
        val plan = CosyVoiceHardwarePolicy.recommend(
            capabilities("MT6991", hexagon = false, openCl = false)
        )

        assertEquals("cpu", plan.llmBackend)
        assertEquals("cpu", plan.flowBackend)
        assertEquals("cpu", plan.hiftCoreBackend)
    }

    private fun capabilities(
        soc: String,
        hexagon: Boolean,
        openCl: Boolean = true
    ) = CosyVoiceDeviceCapabilities(
        manufacturer = if (soc.startsWith("SM")) "Qualcomm" else "MediaTek",
        socManufacturer = if (soc.startsWith("SM")) "Qualcomm" else "MediaTek",
        socModel = soc,
        hardware = soc.lowercase(),
        model = "test",
        cpuCores = 8,
        openClAvailable = openCl,
        hexagonAvailable = hexagon
    )
}
