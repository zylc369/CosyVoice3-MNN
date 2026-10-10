package com.cosyvoice.app

import android.os.Build

internal data class CosyVoiceDeviceCapabilities(
    val manufacturer: String,
    val socManufacturer: String,
    val socModel: String,
    val hardware: String,
    val model: String,
    val cpuCores: Int,
    val openClAvailable: Boolean,
    val hexagonAvailable: Boolean,
    /** 运行期读到的 Hexagon DSP 架构版本（get_hex_arch_ver）；读不到为 null。 */
    val hexagonArch: Int? = null
)

data class CosyVoiceHardwarePlan(
    val flowBackend: String,
    val flowGpuMode: Int,
    val llmBackend: String,
    val hiftCoreBackend: String,
    val hiftGpuMode: Int,
    val cpuThreads: Int,
    val decision: String,
    val npuStatus: String
) {
    fun summary(): String {
        val llm = if (llmBackend == "hexagon") {
            "LLM NPU q_proj + CPU 解码"
        } else {
            "LLM CPU"
        }
        val flow = if (flowBackend == "opencl") "Flow GPU/OpenCL" else "Flow CPU"
        return "$llm · $flow · HiFT CPU/$cpuThreads 线程 · $decision"
    }

    fun cpuFallback(reason: String): CosyVoiceHardwarePlan = copy(
        llmBackend = "cpu",
        decision = reason,
        npuStatus = reason
    )
}

internal object CosyVoiceHardwarePolicy {

    // 【2026-10-10】动态启用策略：
    //   高通 + Hexagon 运行库可加载 → 直接尝试 NPU（q_proj 单算子），
    //   不再做机型/架构白名单。正确性由运行期质量门 + 失败整句回退 CPU 兜底。
    //   注：hexagonArch（get_hex_arch_ver 的数值）目前仅用于展示/记录。
    //
    //   历史方案是写死 setOf("SM8850")；因无法在未验证机型上拿到参考值，
    //   按需求改为“动态检查通过即放开”。代价：DSP/skel 不匹配的机型也会尝试 NPU。

    fun current(
        openClAvailable: Boolean,
        hexagonAvailable: Boolean,
        hexagonArch: Int? = null
    ): CosyVoiceDeviceCapabilities {
        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MANUFACTURER.orEmpty()
        } else {
            ""
        }
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.orEmpty()
        } else {
            ""
        }
        return CosyVoiceDeviceCapabilities(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            socManufacturer = socManufacturer,
            socModel = socModel,
            hardware = Build.HARDWARE.orEmpty(),
            model = Build.MODEL.orEmpty(),
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            openClAvailable = openClAvailable,
            hexagonAvailable = hexagonAvailable,
            hexagonArch = hexagonArch
        )
    }

    fun recommend(capabilities: CosyVoiceDeviceCapabilities): CosyVoiceHardwarePlan {
        val identity = listOf(
            capabilities.manufacturer,
            capabilities.socManufacturer,
            capabilities.socModel,
            capabilities.hardware,
            capabilities.model
        ).joinToString(" ").lowercase()
        val isQualcomm = listOf("qualcomm", "qcom", "snapdragon").any(identity::contains) ||
            Regex("\\bsm\\d{4}\\b", RegexOption.IGNORE_CASE).containsMatchIn(identity)
        val cpuThreads = when {
            capabilities.cpuCores >= 8 -> 6
            capabilities.cpuCores >= 6 -> 4
            capabilities.cpuCores >= 4 -> 3
            else -> 2
        }
        val llmBackend =
            if (isQualcomm && capabilities.hexagonAvailable) "hexagon" else "cpu"
        val flowBackend = if (capabilities.openClAvailable) "opencl" else "cpu"
        val decision = when {
            llmBackend == "hexagon" -> "高通 Hexagon 动态启用"
            capabilities.hexagonAvailable -> "Hexagon 运行库可用"
            isQualcomm -> "高通兼容模式，Hexagon 不可用"
            capabilities.openClAvailable -> "跨厂商 GPU 兼容模式"
            else -> "CPU 兼容模式"
        }
        val npuStatus = when {
            llmBackend == "hexagon" ->
                "Hexagon 仅执行第 0 层 q_proj；输出异常整句回退 CPU" +
                    (capabilities.hexagonArch?.let { "（arch=$it）" } ?: "")
            capabilities.hexagonAvailable -> "Hexagon 运行库可用，但未启用"
            isQualcomm -> "Hexagon Stub/Skeleton 初始化失败"
            else -> "非高通设备不启用 Hexagon"
        }
        return CosyVoiceHardwarePlan(
            flowBackend = flowBackend,
            flowGpuMode = if (flowBackend == "opencl" && isQualcomm) 68 else 4,
            llmBackend = llmBackend,
            hiftCoreBackend = "cpu",
            hiftGpuMode = 4,
            cpuThreads = cpuThreads,
            decision = decision,
            npuStatus = npuStatus
        )
    }
}
