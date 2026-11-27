#pragma once
// Thread-safe logger: writes to logcat AND keeps a bounded ring buffer
// that the Kotlin "Logs" screen can drain.
#include <android/log.h>
#include <chrono>
#include <cstdarg>
#include <cstdio>
#include <deque>
#include <mutex>
#include <string>
#include <vector>

namespace solimes {

class Logger {
public:
    static Logger& get() {
        static Logger instance;
        return instance;
    }

    void logf(int prio, const char* fmt, ...) __attribute__((format(printf, 3, 4))) {
        char buf[512];
        va_list ap;
        va_start(ap, fmt);
        vsnprintf(buf, sizeof(buf), fmt, ap);
        va_end(ap);
        add(prio, buf);
    }

    void add(int prio, const std::string& msg) {
        __android_log_write(prio, "SolimesNative", msg.c_str());
        auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::system_clock::now().time_since_epoch()).count();
        std::string line = std::to_string(ms) + " " + letter(prio) + " " + msg;
        std::lock_guard<std::mutex> lock(mutex_);
        if (lines_.size() >= kMaxLines) lines_.pop_front();
        lines_.push_back(std::move(line));
    }

    // Returns all buffered lines and clears the buffer.
    std::vector<std::string> drain() {
        std::lock_guard<std::mutex> lock(mutex_);
        std::vector<std::string> out(lines_.begin(), lines_.end());
        lines_.clear();
        return out;
    }

private:
    static constexpr size_t kMaxLines = 500;
    static const char* letter(int prio) {
        switch (prio) {
            case ANDROID_LOG_ERROR: return "E";
            case ANDROID_LOG_WARN:  return "W";
            case ANDROID_LOG_DEBUG: return "D";
            default:                return "I";
        }
    }
    std::mutex mutex_;
    std::deque<std::string> lines_;
};

}  // namespace solimes

#define SLOG_I(...) ::solimes::Logger::get().logf(ANDROID_LOG_INFO, __VA_ARGS__)
#define SLOG_W(...) ::solimes::Logger::get().logf(ANDROID_LOG_WARN, __VA_ARGS__)
#define SLOG_E(...) ::solimes::Logger::get().logf(ANDROID_LOG_ERROR, __VA_ARGS__)
