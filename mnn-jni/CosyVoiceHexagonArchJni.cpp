// Hexagon DSP 架构版本查询（JNI）。
//
// 目的：把「是否允许走 NPU」从写死 SoC 型号，改为按运行期读到的 Hexagon DSP
// 架构版本判断（dex 对应我们打包的 V81 skel）。
//
// 为什么单独一个 .so：它只做 dlopen + dlsym，不依赖 MNN，因此可以用 NDK 直接编，
// 无需 WSL/MNN 构建环境。
//
// 依赖的符号来自 App 已打包的 libMNN_htpops.so：
//   int get_hex_arch_ver(int domain, int* archVer);   // 成功返回 0，archVer 写出 arch
// 实测（SM8750）：domain=3（cDSP）返回 0；其他 domain 返回 0x72。
//
// 任何一步失败都返回 -1，由 Kotlin 侧回退到 CPU，绝不抛异常。

#include <jni.h>
#include <dlfcn.h>

namespace {

typedef int (*GetHexArchVerFn)(int domain, int* archVer);

// cDSP 的 domain id；其余 domain 在实测机型上不可用。
constexpr int kCdspDomain = 3;

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_io_legado_app_cosy_CosyVoiceHexagonNative_arch(JNIEnv*, jobject) {
    void* handle = dlopen("libMNN_htpops.so", RTLD_NOW | RTLD_GLOBAL);
    if (handle == nullptr) {
        return -1;
    }
    auto fn = reinterpret_cast<GetHexArchVerFn>(dlsym(handle, "get_hex_arch_ver"));
    if (fn == nullptr) {
        return -1;
    }
    int arch = 0;
    const int rc = fn(kCdspDomain, &arch);
    return rc == 0 ? arch : -1;
}
