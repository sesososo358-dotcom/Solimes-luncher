cmake_minimum_required(VERSION 3.22.1)
project(solimes_native LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# The library name here MUST match System.loadLibrary("solimes_native")
# in NativeBridge.kt (the "lib" prefix and ".so" suffix are added automatically).
add_library(solimes_native SHARED
        NativeBridge.cpp
        VersionProfile.cpp)

target_compile_options(solimes_native PRIVATE
        -Wall -Wextra -O2 -fvisibility=hidden -ffunction-sections -fdata-sections)

# 16 KB page-size support (required for newer Android versions) + dead code stripping
target_link_options(solimes_native PRIVATE
        -Wl,--gc-sections -Wl,-z,max-page-size=16384)

find_library(log-lib log)
target_link_libraries(solimes_native PRIVATE ${log-lib})
