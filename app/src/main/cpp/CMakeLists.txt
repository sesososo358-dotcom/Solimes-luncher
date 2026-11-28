cmake_minimum_required(VERSION 3.18.1)
project(solimes_native LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(lwjgl_bridge SHARED
    lwjgl_bridge.cpp
)

find_library(log-lib log)
find_library(android-lib android)

target_compile_options(lwjgl_bridge PRIVATE
    -Wall -Wextra -fvisibility=hidden
)

target_link_libraries(lwjgl_bridge
    ${log-lib}
    ${android-lib}
    dl
)
