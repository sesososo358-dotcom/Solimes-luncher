#include <jni.h>
#include <unistd.h>

#include <atomic>
#include <string>
#include <vector>

#include "Logger.h"
#include "VersionProfile.h"

// Kotlin counterpart: com.solimes.launcher.nativebridge.NativeBridge
// JNI names below MUST match that package + object name exactly.
#define JNI_FN(ret, name) \
    extern "C" JNIEXPORT ret JNICALL Java_com_solimes_launcher_nativebridge_NativeBridge_##name

namespace {

std::atomic<bool> gInitialized{false};

std::string toStd(JNIEnv* env, jstring s) {
    if (s == nullptr) return {};
    const char* chars = env->GetStringUTFChars(s, nullptr);
    if (chars == nullptr) return {};  // OOM: a Java exception is already pending
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

jstring toJava(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());  // all our strings are ASCII
}

std::string join(const std::vector<std::string>& v, char sep) {
    std::string out;
    for (size_t i = 0; i < v.size(); ++i) {
        if (i) out += sep;
        out += v[i];
    }
    return out;
}

long totalRamMb() {
    long pages = sysconf(_SC_PHYS_PAGES);
    long pageSize = sysconf(_SC_PAGESIZE);
    if (pages <= 0 || pageSize <= 0) return 0;
    return static_cast<long>((static_cast<long long>(pages) * pageSize) / (1024 * 1024));
}

const char* abiName() {
#if defined(__aarch64__)
    return "arm64-v8a";
#elif defined(__arm__)
    return "armeabi-v7a";
#elif defined(__x86_64__)
    return "x86_64";
#else
    return "unknown";
#endif
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) {
    SLOG_I("libsolimes_native loaded (abi=%s)", abiName());
    return JNI_VERSION_1_6;
}

JNI_FN(jboolean, nativeInit)(JNIEnv*, jobject) {
    bool expected = false;
    if (gInitialized.compare_exchange_strong(expected, true)) {
        SLOG_I("native init: %ld MB RAM, %ld cores",
               totalRamMb(), sysconf(_SC_NPROCESSORS_ONLN));
    }
    return JNI_TRUE;
}

JNI_FN(jstring, nativeSupportedVersions)(JNIEnv* env, jobject) {
    return toJava(env, join(solimes::supportedIds(), ','));
}

JNI_FN(jstring, nativeVersionProfileJson)(JNIEnv* env, jobject, jstring versionId) {
    const std::string id = toStd(env, versionId);
    const solimes::VersionProfile* p = solimes::findProfile(id);
    if (p == nullptr) {
        SLOG_W("unknown version requested: %s", id.c_str());
        return nullptr;  // Kotlin side maps null -> unsupported version
    }
    return toJava(env, solimes::toJson(*p));
}

JNI_FN(jstring, nativeBuildJvmArgs)(JNIEnv* env, jobject, jstring versionId, jint heapMb, jboolean lowEnd) {
    const std::string id = toStd(env, versionId);
    const solimes::VersionProfile* p = solimes::findProfile(id);
    if (p == nullptr) return nullptr;
    return toJava(env, join(solimes::buildJvmArgs(*p, heapMb, lowEnd == JNI_TRUE), '\n'));
}

JNI_FN(jint, nativeRecommendedHeapMb)(JNIEnv* env, jobject, jstring versionId) {
    const std::string id = toStd(env, versionId);
    const solimes::VersionProfile* p = solimes::findProfile(id);
    if (p == nullptr) return 0;
    return solimes::recommendedHeapMb(*p, totalRamMb());
}

JNI_FN(jstring, nativeDeviceInfoJson)(JNIEnv* env, jobject) {
    std::string j = "{";
    j += std::string("\"abi\":\"") + abiName() + "\",";
    j += "\"totalRamMb\":" + std::to_string(totalRamMb()) + ",";
    j += "\"cores\":" + std::to_string(sysconf(_SC_NPROCESSORS_ONLN));
    j += "}";
    return toJava(env, j);
}

JNI_FN(void, nativeLog)(JNIEnv* env, jobject, jint level, jstring message) {
    const std::string msg = toStd(env, message);
    int prio = ANDROID_LOG_INFO;
    if (level >= 2) prio = ANDROID_LOG_ERROR;
    else if (level == 1) prio = ANDROID_LOG_WARN;
    solimes::Logger::get().add(prio, msg);
}

JNI_FN(jstring, nativeDrainLogs)(JNIEnv* env, jobject) {
    return toJava(env, join(solimes::Logger::get().drain(), '\n'));
}
