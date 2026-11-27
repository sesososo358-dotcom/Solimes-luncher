#include "VersionProfile.h"

#include <algorithm>
#include <array>

namespace solimes {
namespace {

// Field order: id, era, javaMajor, lwjglMajor, mainClass, assetIndex,
// fabricLoader, valuesFromManifest, renderDist, simDist, minHeap, maxHeap
const std::array<VersionProfile, 3> kProfiles = {{
    {"26.3", Era::Modern, 25, 3, "net.minecraft.client.main.Main", "",
     "fabric", true, 4, 5, 768, 1536},
    {"26.1", Era::Modern, 25, 3, "net.minecraft.client.main.Main", "",
     "fabric", true, 4, 5, 768, 1536},
    {"1.8.8", Era::Legacy, 8, 2, "net.minecraft.client.main.Main", "1.8",
     "legacy-fabric", false, 4, 0, 512, 1024},
}};

std::string jsonEscape(const std::string& s) {
    std::string out;
    out.reserve(s.size() + 8);
    for (char c : s) {
        switch (c) {
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n";  break;
            default:   out += c;
        }
    }
    return out;
}

}  // namespace

const VersionProfile* findProfile(const std::string& id) {
    for (const auto& p : kProfiles) {
        if (p.id == id) return &p;
    }
    return nullptr;
}

std::vector<std::string> supportedIds() {
    std::vector<std::string> ids;
    for (const auto& p : kProfiles) ids.push_back(p.id);
    return ids;
}

std::string toJson(const VersionProfile& p) {
    std::string j = "{";
    j += "\"id\":\"" + jsonEscape(p.id) + "\",";
    j += std::string("\"era\":\"") + (p.era == Era::Legacy ? "legacy" : "modern") + "\",";
    j += "\"javaMajor\":" + std::to_string(p.javaMajor) + ",";
    j += "\"lwjglMajor\":" + std::to_string(p.lwjglMajor) + ",";
    j += "\"mainClass\":\"" + jsonEscape(p.mainClass) + "\",";
    j += "\"assetIndex\":\"" + jsonEscape(p.assetIndex) + "\",";
    j += "\"fabricLoader\":\"" + jsonEscape(p.fabricLoader) + "\",";
    j += std::string("\"valuesFromManifest\":") + (p.valuesFromManifest ? "true" : "false") + ",";
    j += "\"defaultRenderDistance\":" + std::to_string(p.defaultRenderDistance) + ",";
    j += "\"defaultSimulationDistance\":" + std::to_string(p.defaultSimulationDistance) + ",";
    j += "\"minHeapMb\":" + std::to_string(p.minHeapMb) + ",";
    j += "\"maxHeapMb\":" + std::to_string(p.maxHeapMb);
    j += "}";
    return j;
}

int clampHeapMb(const VersionProfile& p, int heapMb) {
    return std::max(p.minHeapMb, std::min(heapMb, p.maxHeapMb));
}

int recommendedHeapMb(const VersionProfile& p, long totalRamMb) {
    if (totalRamMb <= 0) return p.minHeapMb;
    // ~35% of physical RAM leaves room for Android, the renderer and the game's native memory.
    return clampHeapMb(p, static_cast<int>(totalRamMb * 35 / 100));
}

std::vector<std::string> buildJvmArgs(const VersionProfile& p, int heapMb, bool lowEnd) {
    const int heap = clampHeapMb(p, heapMb);
    std::vector<std::string> a;
    a.push_back("-Xmx" + std::to_string(heap) + "M");
    a.push_back("-Xms" + std::to_string(heap / 2) + "M");
    if (lowEnd) {
        a.push_back("-XX:+UseSerialGC");  // fewest threads / lowest overhead on 8 weak cores
    } else {
        a.push_back("-XX:+UseG1GC");
        a.push_back("-XX:MaxGCPauseMillis=50");
    }
    a.push_back("-XX:+DisableExplicitGC");
    a.push_back("-Dfile.encoding=UTF-8");
    return a;
}

}  // namespace solimes
