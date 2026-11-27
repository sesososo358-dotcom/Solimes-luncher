#pragma once
#include <string>
#include <vector>

namespace solimes {

enum class Era { Legacy, Modern };

// Static description of one supported Minecraft version.
// NOTE: values marked "fromManifest" are only DEFAULTS. At launch time the
// Kotlin side must read the real version JSON (javaVersion, mainClass,
// assetIndex, libraries) from Mojang's manifest and override them.
struct VersionProfile {
    std::string id;                  // "26.3", "26.1", "1.8.8"
    Era era;                         // Legacy (1.8.8) or Modern (26.x)
    int javaMajor;                   // required Java major version (default)
    int lwjglMajor;                  // 2 for 1.8.8, 3 for modern
    std::string mainClass;           // client entry point (default)
    std::string assetIndex;          // "" = take from manifest
    std::string fabricLoader;        // "fabric" or "legacy-fabric"
    bool valuesFromManifest;         // true = verify against version JSON
    int defaultRenderDistance;       // low-end default (chunks)
    int defaultSimulationDistance;   // 0 = not applicable (legacy)
    int minHeapMb;
    int maxHeapMb;
};

const VersionProfile* findProfile(const std::string& id);
std::vector<std::string> supportedIds();
std::string toJson(const VersionProfile& p);
int clampHeapMb(const VersionProfile& p, int heapMb);
int recommendedHeapMb(const VersionProfile& p, long totalRamMb);
std::vector<std::string> buildJvmArgs(const VersionProfile& p, int heapMb, bool lowEnd);

}  // namespace solimes
