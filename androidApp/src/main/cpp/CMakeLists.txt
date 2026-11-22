cmake_minimum_required(VERSION 3.22.1)
project(nuvioseekthumb LANGUAGES C CXX)

find_library(log-lib log)

# Seek thumbnails: keyframe reader over the FFmpeg MPV already ships (opened with dlopen, nothing linked).
# seekthumb/ffmpeg holds only the matching public headers (LGPL 2.1), see seekthumb/ffmpeg/README.md.
add_library(nuvioseekthumb SHARED seekthumb/seekthumb.cpp)
target_include_directories(nuvioseekthumb PRIVATE "${CMAKE_CURRENT_LIST_DIR}/seekthumb/ffmpeg")
target_compile_features(nuvioseekthumb PRIVATE cxx_std_17)
target_compile_options(nuvioseekthumb PRIVATE -O2 -Wall -Wextra -Wno-unused-parameter -Wno-deprecated-declarations)
target_link_options(nuvioseekthumb PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(nuvioseekthumb ${log-lib} dl m)
