// jvm_launcher.cpp — تشغيل OpenJDK داخل العملية عبر JLI_Launch (بدون exec).
#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>

#define LOG_TAG "SolimesJvm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

using JLI_Launch_t = int (*)(int argc, char** argv,
                             int jargc, const char** jargv,
                             int appclassc, const char** appclassv,
                             const char* fullversion, const char* dotversion,
                             const char* pname, const char* lname,
                             jboolean javaargs, jboolean cpwildcard,
                             jboolean javaw, jint ergo);

std::string str(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

std::vector<std::string> vec(JNIEnv* env, jobjectArray arr) {
    std::vector<std::string> out;
    if (!arr) return out;
    const jsize n = env->GetArrayLength(arr);
    out.reserve(static_cast<size_t>(n));
    for (jsize i = 0; i < n; i++) {
        jstring s = static_cast<jstring>(env->GetObjectArrayElement(arr, i));
        out.push_back(str(env, s));
        env->DeleteLocalRef(s);
    }
    return out;
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_solimes_launcher_core_JvmLauncher_nativeRun(JNIEnv* env, jclass,
                                                     jstring jWork, jstring jLog, jstring jLibJli,
                                                     jobjectArray jPreload, jobjectArray jEnv,
                                                     jobjectArray jArgv) {
    const std::string work = str(env, jWork);
    const std::string log = str(env, jLog);
    const std::string libJli = str(env, jLibJli);
    const auto preload = vec(env, jPreload);
    const auto envs = vec(env, jEnv);
    const auto args = vec(env, jArgv);

    for (const auto& kv : envs) {
        const size_t eq = kv.find('=');
        if (eq == std::string::npos) continue;
        setenv(kv.substr(0, eq).c_str(), kv.substr(eq + 1).c_str(), 1);
    }

    if (chdir(work.c_str()) != 0) LOGW("chdir(%s) فشل", work.c_str());

    // توجيه stdout/stderr الخاصة بـ JVM واللعبة إلى ملف السجل مع تفعيل التسطير الفوري
    const int fd = open(log.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd >= 0) {
        dup2(fd, STDOUT_FILENO);
        dup2(fd, STDERR_FILENO);
        if (fd > STDERR_FILENO) close(fd);
        std::setvbuf(stdout, nullptr, _IOLBF, 0);
        std::setvbuf(stderr, nullptr, _IOLBF, 0);
    } else {
        LOGW("تعذّر فتح ملف السجل %s", log.c_str());
    }

    for (const auto& p : preload) {
        void* h = dlopen(p.c_str(), RTLD_NOW | RTLD_GLOBAL);
        if (h) LOGI("preload OK: %s", p.c_str());
        else   LOGE("preload فشل: %s -> %s", p.c_str(), dlerror());
    }

    void* jli = dlopen(libJli.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!jli) {
        LOGE("dlopen(libjli) فشل: %s", dlerror());
        return -2;
    }
    auto launch = reinterpret_cast<JLI_Launch_t>(dlsym(jli, "JLI_Launch"));
    if (!launch) {
        LOGE("JLI_Launch غير موجود: %s", dlerror());
        return -3;
    }

    std::vector<std::string> owned(args);  // نسخة قابلة للتعديل تبقى حية أثناء التشغيل
    std::vector<char*> argv;
    argv.reserve(owned.size() + 1);
    for (auto& s : owned) argv.push_back(s.data());
    argv.push_back(nullptr);

    LOGI("استدعاء JLI_Launch بـ %zu وسيط", owned.size());
    const int rc = launch(static_cast<int>(owned.size()), argv.data(),
                          0, nullptr, 0, nullptr,
                          "1.8.0-internal", "1.8", "java", "openjdk",
                          JNI_FALSE, JNI_TRUE, JNI_FALSE, JNI_TRUE);
    std::fflush(stdout);
    std::fflush(stderr);
    LOGI("JLI_Launch رجع: %d", rc);
    return rc;
}
