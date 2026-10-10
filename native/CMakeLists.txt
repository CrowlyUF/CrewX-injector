cmake_minimum_required(VERSION 3.21)
project(CrewXNative VERSION 1.0 LANGUAGES C RC)

if(NOT WIN32)
    message(FATAL_ERROR "CrewXNative is a Windows-only injection DLL")
endif()
if(NOT MSVC)
    message(FATAL_ERROR "CrewXNative requires the MSVC x64 toolchain")
endif()
if(NOT CMAKE_SIZEOF_VOID_P EQUAL 8)
    message(FATAL_ERROR "Only x64 Minecraft/JVM targets are supported")
endif()

set(CREWX_JAVA_HOME "$ENV{JAVA_HOME}" CACHE PATH "JDK containing JNI/JVMTI headers")
if(NOT CREWX_JAVA_HOME)
    foreach(candidate
            "C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"
            "C:/Program Files/Java/jdk-21.0.12")
        if(EXISTS "${candidate}/include/jni.h")
            set(CREWX_JAVA_HOME "${candidate}")
            break()
        endif()
    endforeach()
endif()

find_path(CREWX_JNI_INCLUDE jni.h
        HINTS "${CREWX_JAVA_HOME}/include" REQUIRED)
find_path(CREWX_JNI_MD_INCLUDE jni_md.h
        HINTS "${CREWX_JAVA_HOME}/include/win32" REQUIRED)
find_path(CREWX_JVMTI_INCLUDE jvmti.h
        HINTS "${CREWX_JAVA_HOME}/include" REQUIRED)

set(CREWX_PRODUCT_JAR "" CACHE FILEPATH
        "CrewX payload JAR embedded into CrewXNative.dll")
set(CREWX_PRODUCT_JAR_NAMED "" CACHE FILEPATH
        "CrewX named payload JAR for Lunar and other MCP-named clients")
set(CREWX_PRODUCT_JAR_NOTCH "" CACHE FILEPATH
        "CrewX Notch-mapped payload JAR for Badlion/vanilla clients")
if(NOT CREWX_PRODUCT_JAR OR NOT EXISTS "${CREWX_PRODUCT_JAR}")
    message(FATAL_ERROR
            "CREWX_PRODUCT_JAR must name an existing payload JAR")
endif()
get_filename_component(CREWX_PRODUCT_JAR
        "${CREWX_PRODUCT_JAR}" ABSOLUTE)
if(NOT CREWX_PRODUCT_JAR_NAMED OR NOT EXISTS "${CREWX_PRODUCT_JAR_NAMED}")
    message(FATAL_ERROR "CREWX_PRODUCT_JAR_NAMED must name an existing payload JAR")
endif()
get_filename_component(CREWX_PRODUCT_JAR_NAMED
        "${CREWX_PRODUCT_JAR_NAMED}" ABSOLUTE)
if(NOT CREWX_PRODUCT_JAR_NOTCH OR NOT EXISTS "${CREWX_PRODUCT_JAR_NOTCH}")
    message(FATAL_ERROR "CREWX_PRODUCT_JAR_NOTCH must name an existing payload JAR")
endif()
get_filename_component(CREWX_PRODUCT_JAR_NOTCH
        "${CREWX_PRODUCT_JAR_NOTCH}" ABSOLUTE)
file(TO_CMAKE_PATH "${CREWX_PRODUCT_JAR}" CREWX_PRODUCT_JAR_RC_PATH)
file(TO_CMAKE_PATH "${CREWX_PRODUCT_JAR_NAMED}" CREWX_PRODUCT_JAR_NAMED_RC_PATH)
file(TO_CMAKE_PATH "${CREWX_PRODUCT_JAR_NOTCH}" CREWX_PRODUCT_JAR_NOTCH_RC_PATH)
configure_file(payload.rc.in generated/payload.rc @ONLY)
configure_file(payload_named.rc.in generated/payload_named.rc @ONLY)
configure_file(payload_notch.rc.in generated/payload_notch.rc @ONLY)
set_property(SOURCE "${CMAKE_CURRENT_BINARY_DIR}/generated/payload.rc"
        APPEND PROPERTY OBJECT_DEPENDS "${CREWX_PRODUCT_JAR}")
set_property(SOURCE "${CMAKE_CURRENT_BINARY_DIR}/generated/payload_named.rc"
        APPEND PROPERTY OBJECT_DEPENDS "${CREWX_PRODUCT_JAR_NAMED}")
set_property(SOURCE "${CMAKE_CURRENT_BINARY_DIR}/generated/payload_notch.rc"
        APPEND PROPERTY OBJECT_DEPENDS "${CREWX_PRODUCT_JAR_NOTCH}")

add_library(CrewXNative SHARED
        dllmain.c
        "${CMAKE_CURRENT_BINARY_DIR}/generated/payload.rc"
        "${CMAKE_CURRENT_BINARY_DIR}/generated/payload_named.rc"
        "${CMAKE_CURRENT_BINARY_DIR}/generated/payload_notch.rc")
target_include_directories(CrewXNative PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}"
        "${CREWX_JNI_INCLUDE}"
        "${CREWX_JNI_MD_INCLUDE}"
        "${CREWX_JVMTI_INCLUDE}")
target_compile_definitions(CrewXNative PRIVATE
        WIN32_LEAN_AND_MEAN
        _CRT_SECURE_NO_WARNINGS)
target_compile_options(CrewXNative PRIVATE /W4)
target_link_libraries(CrewXNative PRIVATE user32)

set(CREWX_EMBEDDED_DLL "${CMAKE_BINARY_DIR}/dist/CrewXNative.dll")
file(TO_CMAKE_PATH "${CREWX_EMBEDDED_DLL}" CREWX_EMBEDDED_DLL_RC_PATH)
configure_file(loader_payload.rc.in generated/loader_payload.rc @ONLY)
set_property(SOURCE "${CMAKE_CURRENT_BINARY_DIR}/generated/loader_payload.rc"
        APPEND PROPERTY OBJECT_DEPENDS "${CREWX_EMBEDDED_DLL}")

add_executable(CrewXInjector WIN32 injector.c
        "${CMAKE_CURRENT_BINARY_DIR}/generated/loader_payload.rc")
add_dependencies(CrewXInjector CrewXNative)
target_compile_definitions(CrewXInjector PRIVATE
        WIN32_LEAN_AND_MEAN
        _CRT_SECURE_NO_WARNINGS)
target_compile_options(CrewXInjector PRIVATE /W4)
target_link_libraries(CrewXInjector PRIVATE advapi32 user32 gdi32)
target_link_options(CrewXInjector PRIVATE "/ENTRY:wmainCRTStartup")

set_property(TARGET CrewXNative CrewXInjector PROPERTY
        MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>DLL")

set_target_properties(CrewXNative CrewXInjector PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/dist")
foreach(configuration Debug Release RelWithDebInfo MinSizeRel)
    string(TOUPPER "${configuration}" configuration_upper)
    set_target_properties(CrewXNative CrewXInjector PROPERTIES
            RUNTIME_OUTPUT_DIRECTORY_${configuration_upper}
            "${CMAKE_BINARY_DIR}/dist")
endforeach()
