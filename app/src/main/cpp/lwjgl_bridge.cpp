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
int               g_width   = 0;
int               g_height  = 0;
void*             g_lib     = nullptr;
Hooks             g_hooks;
std::deque<Event> g_pending;
uint32_t          g_dropped = 0;

void enqueueLocked(const Event& e) {
    if (g_pending.size() >= kMaxPending) {
        g_pending.pop_front();
        if ((++g_dropped % 100) == 1) {
            LOGW("Pending queue full, dropped old events (total=%u)", g_dropped);
        }
    }
    g_pending.push_back(e);
}

void dispatch(const Hooks& h, const Event& e) {
    switch (e.type) {
        case EventType::Key:
            if (h.key) h.key(e.a, e.b);
            break;
        case EventType::Pointer:
            if (h.pointer) h.pointer(e.x, e.y, e.a, e.b);
            break;
        case EventType::Size:
            if (h.size) h.size(e.a, e.b);
            break;
    }
}

bool hasHookFor(const Hooks& h, EventType t) {
    switch (t) {
        case EventType::Key:     return h.key != nullptr;
        case EventType::Pointer: return h.pointer != nullptr;
        case EventType::Size:    return h.size != nullptr;
    }
    return false;
}

void sendOrQueue(const Event& e) {
    Hooks h;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        h = g_hooks;
        if (!hasHookFor(h, e.type)) {
            enqueueLocked(e);
            return;
        }
    }
    dispatch(h, e);
}

template <typename Fn>
Fn resolveSymbol(void* lib, const char* name) {
    void* sym = lib ? dlsym(lib, name) : nullptr;
    if (!sym) sym = dlsym(RTLD_DEFAULT, name);
    return reinterpret_cast<Fn>(sym);
}

void flushPending() {
    Hooks h;
    std::deque<Event> toSend;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        h = g_hooks;
        std::deque<Event> keep;
        for (const Event& e : g_pending) {
            if (hasHookFor(h, e.type)) toSend.push_back(e);
            else keep.push_back(e);
        }
        g_pending.swap(keep);
    }
    for (const Event& e : toSend) dispatch(h, e);
}

}  // namespace

extern "C" {

SOLIMES_EXPORT ANativeWindow* solimes_get_native_window() {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_window;
}

SOLIMES_EXPORT void solimes_get_window_size(int* width, int* height) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (width)  *width  = g_width;
    if (height) *height = g_height;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    LOGI("JNI_OnLoad: lwjgl_bridge loaded");
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
Java_com_solimes_launcher_core_LwjglBridge_nativeSetSurface(JNIEnv* env, jclass, jobject surface) {
    if (surface == nullptr) return;
    ANativeWindow* win = ANativeWindow_fromSurface(env, surface);
    if (win == nullptr) return;

    const int w = ANativeWindow_getWidth(win);
    const int h = ANativeWindow_getHeight(win);

    ANativeWindow* old = nullptr;
    Hooks hooks;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        old      = g_window;
        g_window = win;
        g_width  = w;
        g_height = h;
        hooks    = g_hooks;
    }
    if (old) ANativeWindow_release(old);
    if (hooks.surface) hooks.surface(win, w, h);
}

JNIEXPORT void JNICALL
Java_com_solimes_launcher_core_LwjglBridge_nativeSurfaceDestroyed(JNIEnv*, jclass) {
    ANativeWindow* old = nullptr;
    Hooks hooks;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        old      = g_window;
        g_window = nullptr;
        hooks    = g_hooks;
    }
    if (hooks.surface) hooks.surface(nullptr, 0, 0);
    if (old) ANativeWindow_release(old);
}

JNIEXPORT void JNICALL
Java_com_solimes_launcher_core_LwjglBridge_nativeSetSize(JNIEnv*, jclass, jint width, jint height) {
    if (width <= 0 || height <= 0) return;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_width  = width;
        g_height = height;
    }
    Event e{EventType::Size, width, height, 0.0, 0.0};
    sendOrQueue(e);
}

JNIEXPORT void JNICALL
Java_com_solimes_launcher_core_LwjglBridge_nativeKeyEvent(JNIEnv*, jclass, jint keyCode, jint action) {
    if (action < 0 || action > 2) return;
    Event e{EventType::Key, keyCode, action, 0.0, 0.0};
    sendOrQueue(e);
}

JNIEXPORT void JNICALL
Java_com_solimes_launcher_core_LwjglBridge_nativePointerEvent(JNIEnv*, jclass,
                                                               jfloat x, jfloat y,
                                                               jint button, jint action) {
    if (action < 0 || action > 2) return;
    Event e{EventType::Pointer, button, action, static_cast<double>(x), static_cast<double>(y)};
    sendOrQueue(e);
}

JNIEXPORT jboolean JNICALL
Java_com_solimes_launcher_core_LwjglBridge_nativeBindLibrary(JNIEnv* env, jclass, jstring libPath) {
    void* lib = nullptr;

    if (libPath != nullptr) {
        const char* path = env->GetStringUTFChars(libPath, nullptr);
        if (path == nullptr) return JNI_FALSE;
        lib = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
        if (lib == nullptr) {
            LOGE("dlopen failed for %s: %s", path, dlerror());
            env->ReleaseStringUTFChars(libPath, path);
            return JNI_FALSE;
        }
        env->ReleaseStringUTFChars(libPath, path);
    }

    Hooks hooks;
    hooks.key     = resolveSymbol<KeyFn>(lib, "solimes_glfw_on_key");
    hooks.pointer = resolveSymbol<PointerFn>(lib, "solimes_glfw_on_pointer");
    hooks.size    = resolveSymbol<SizeFn>(lib, "solimes_glfw_on_size");
    hooks.surface = resolveSymbol<SurfaceFn>(lib, "solimes_glfw_on_surface");

    ANativeWindow* win = nullptr;
    int w = 0, h = 0;
    void* oldLib = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        oldLib  = g_lib;
        g_lib   = lib;
        g_hooks = hooks;
        win     = g_window;
        w       = g_width;
        h       = g_height;
    }
    if (oldLib && oldLib != lib) dlclose(oldLib);

    if (win && hooks.surface) hooks.surface(win, w, h);
    flushPending();

    return (hooks.key || hooks.pointer || hooks.size || hooks.surface) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_solimes_launcher_core_LwjglBridge_nativeUnbindLibrary(JNIEnv*, jclass) {
    void* lib = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        lib     = g_lib;
        g_lib   = nullptr;
        g_hooks = Hooks{};
    }
    if (lib) dlclose(lib);
}

}  // extern "C"
