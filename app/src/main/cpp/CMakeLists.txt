cmake_minimum_required(VERSION 3.22.1)
project("neontides_llm" LANGUAGES C CXX)

if(ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    add_compile_options(-O3 -march=armv8-a)
elseif(ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "x86_64")
    add_compile_options(-O3)
endif()

include(FetchContent)

set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://android.googlesource.com/platform/external/ggml-org/llama.cpp
    GIT_TAG refs/tags/studio-2026.1.2
    GIT_SHALLOW TRUE
)

FetchContent_MakeAvailable(llama_cpp)

add_library(neontides_llm SHARED native-lib.cpp)

target_compile_features(neontides_llm PRIVATE cxx_std_17)

target_include_directories(
    neontides_llm PRIVATE
    ${llama_cpp_SOURCE_DIR}/include
    ${llama_cpp_SOURCE_DIR}/ggml/include
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    neontides_llm
    PRIVATE
    llama
    ${log-lib}
    ${android-lib}
)
