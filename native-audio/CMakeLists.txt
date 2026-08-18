cmake_minimum_required(VERSION 3.25)
project(bmsir_native_audio LANGUAGES C CXX)

if(NOT WIN32 OR NOT MSVC)
  message(FATAL_ERROR "The distributable native-audio build requires 64-bit Windows MSVC")
endif()
if(NOT CMAKE_SIZEOF_VOID_P EQUAL 8)
  message(FATAL_ERROR "Only the Windows x86-64 distribution is supported")
endif()

set(PORTAUDIO_SOURCE_DIR "" CACHE PATH "Pinned PortAudio source directory")
set(ASIOSDK_ROOT_DIR "" CACHE PATH "Pinned Steinberg ASIO SDK root")
set(PORTAUDIO_COMMIT "" CACHE STRING "Pinned PortAudio commit")
set(JAVA_HOME "" CACHE PATH "Windows x64 JDK root")
foreach(required PORTAUDIO_SOURCE_DIR ASIOSDK_ROOT_DIR PORTAUDIO_COMMIT JAVA_HOME)
  if(NOT ${required})
    message(FATAL_ERROR "${required} is required")
  endif()
endforeach()
if(NOT EXISTS "${PORTAUDIO_SOURCE_DIR}/include/portaudio.h")
  message(FATAL_ERROR "PORTAUDIO_SOURCE_DIR does not contain PortAudio")
endif()
if(NOT EXISTS "${ASIOSDK_ROOT_DIR}/common/asio.h")
  message(FATAL_ERROR "ASIOSDK_ROOT_DIR does not contain the ASIO SDK")
endif()
if(NOT EXISTS "${JAVA_HOME}/include/jni.h" OR NOT EXISTS "${JAVA_HOME}/include/win32/jni_md.h")
  message(FATAL_ERROR "JAVA_HOME does not contain Windows JNI headers")
endif()

set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
set(PA_BUILD_SHARED ON CACHE BOOL "" FORCE)
set(PA_BUILD_STATIC OFF CACHE BOOL "" FORCE)
set(PA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(PA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(PA_DISABLE_INSTALL ON CACHE BOOL "" FORCE)
set(PA_USE_ASIO ON CACHE BOOL "" FORCE)
set(PA_USE_WASAPI ON CACHE BOOL "" FORCE)
set(PA_USE_WMME ON CACHE BOOL "" FORCE)
set(PA_USE_DS ON CACHE BOOL "" FORCE)
set(PA_ENABLE_DEBUG_OUTPUT OFF CACHE BOOL "" FORCE)

add_subdirectory("${PORTAUDIO_SOURCE_DIR}" portaudio-build EXCLUDE_FROM_ALL)
if(NOT TARGET portaudio OR NOT PA_USE_ASIO OR NOT PA_USE_WASAPI)
  message(FATAL_ERROR "PortAudio must be built with both ASIO and WASAPI")
endif()
target_compile_definitions(portaudio PRIVATE "PA_GIT_REVISION=${PORTAUDIO_COMMIT}")

set(JPORTAUDIO_SOURCE_DIR "${PORTAUDIO_SOURCE_DIR}/bindings/java/c/src")
add_library(jportaudio SHARED
  "${JPORTAUDIO_SOURCE_DIR}/com_portaudio_BlockingStream.c"
  "${JPORTAUDIO_SOURCE_DIR}/com_portaudio_PortAudio.c"
  "${JPORTAUDIO_SOURCE_DIR}/jpa_tools.c"
)
target_include_directories(jportaudio PRIVATE
  "${PORTAUDIO_SOURCE_DIR}/include"
  "${JPORTAUDIO_SOURCE_DIR}"
  "${JAVA_HOME}/include"
  "${JAVA_HOME}/include/win32"
)
target_link_libraries(jportaudio PRIVATE portaudio)
set_target_properties(jportaudio PROPERTIES
  PREFIX ""
  OUTPUT_NAME "jportaudio_x64"
)

set(BMSIR_NATIVE_OUTPUT_DIR "${CMAKE_BINARY_DIR}/artifacts")
set_target_properties(portaudio jportaudio PROPERTIES
  RUNTIME_OUTPUT_DIRECTORY_RELEASE "${BMSIR_NATIVE_OUTPUT_DIR}"
  LIBRARY_OUTPUT_DIRECTORY_RELEASE "${BMSIR_NATIVE_OUTPUT_DIR}"
  ARCHIVE_OUTPUT_DIRECTORY_RELEASE "${BMSIR_NATIVE_OUTPUT_DIR}"
)

foreach(target portaudio jportaudio)
  target_compile_options(${target} PRIVATE
    /Brepro
    "/pathmap:${PORTAUDIO_SOURCE_DIR}=portaudio-19.7.0"
    "/pathmap:${ASIOSDK_ROOT_DIR}=ASIOSDK-2.3.4"
    "/pathmap:${CMAKE_CURRENT_SOURCE_DIR}=oraja-native-audio"
  )
  target_link_options(${target} PRIVATE /Brepro /INCREMENTAL:NO)
endforeach()
