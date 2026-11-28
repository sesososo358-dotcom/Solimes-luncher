#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <dlfcn.h>

#include <cstdint>
#include <deque>
#include <mutex>

#define LOG_TAG "SolimesBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define SOLIMES_EXPORT __attribute__((visibility("default")))

namespace {

using KeyFn     = void (*)(int keyCode, int action);
using PointerFn = void (*)(double x, double y, int button, int action);
using SizeFn    = void (*)(int width, int height);
using SurfaceFn = void (*)(ANativeWindow* window, int width, int height);

struct Hooks {
    KeyFn     key     = nullptr;
    PointerFn pointer = nullptr;
    SizeFn    size    = nullptr;
    SurfaceFn surface = nullptr;
};

enum class EventType { Key, Pointer, Size };

struct Event {
    EventType type;
    int       a = 0;
    int       b = 0;
    double    x = 0.0;
    double    y = 0.0;
};

constexpr size_t kMaxPending = 1024;

std::mutex        g_mutex;
JavaVM*           g_vm      = nullptr;
ANativeWindow*    g_window  = nullptr;
int               g_width   = 0
