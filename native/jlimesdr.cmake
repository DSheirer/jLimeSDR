# Injected into the LimeSuiteNG build with CMAKE_PROJECT_limesuiteng_INCLUDE by
# build-unix.sh and build-windows.ps1; not meant to be used by hand.
#
# LimeSuiteNG is configured with BUILD_SHARED_LIBS=OFF and BUILD_LEGACYAPI=OFF,
# so its core library is static. Once the top-level CMakeLists.txt has defined
# every target, this adds limesuiteng-legacyapi as the one shared library that
# jLimeSDR loads: the legacy LMS_* wrapper with the core linked into it.
#
# One library keeps LimeSuiteNG's C++ interfaces inside it. On Windows that lets
# the C runtime be linked statically: two DLLs, each with its own runtime,
# would pass C++ objects between two heaps. Only the LMS_* functions are
# exported, and on Linux libstdc++ is linked statically and hidden too.

include_guard(GLOBAL)

function(jlimesdr_add_library)
    set(SRC "${CMAKE_SOURCE_DIR}/src")
    add_library(limesuiteng-legacyapi SHARED "${SRC}/legacyapi/LMS_APIWrapper.cpp")
    target_include_directories(limesuiteng-legacyapi PRIVATE "${SRC}" "${SRC}/legacyapi/include")
    target_link_libraries(limesuiteng-legacyapi PRIVATE limesuiteng)
    set_target_properties(limesuiteng-legacyapi PROPERTIES
        CXX_VISIBILITY_PRESET hidden
        VISIBILITY_INLINES_HIDDEN ON)
    if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
        # libstdc++ declares namespace std with default visibility, so template
        # instances would be exported despite -fvisibility=hidden.
        set(VERSION_SCRIPT "${CMAKE_BINARY_DIR}/jlimesdr-exports.map")
        file(WRITE "${VERSION_SCRIPT}" "{\n  global: LMS_*;\n  local: *;\n};\n")
        target_link_options(limesuiteng-legacyapi PRIVATE
            -static-libstdc++ -static-libgcc -Wl,--exclude-libs,ALL -Wl,--no-undefined
            "-Wl,--version-script=${VERSION_SCRIPT}")
        set_property(TARGET limesuiteng-legacyapi APPEND PROPERTY LINK_DEPENDS "${VERSION_SCRIPT}")
    elseif(APPLE)
        target_link_options(limesuiteng-legacyapi PRIVATE "LINKER:-exported_symbol,_LMS_*")
    elseif(MSVC)
        target_link_options(limesuiteng-legacyapi PRIVATE /INCREMENTAL:NO)
    endif()
endfunction()

cmake_language(DEFER DIRECTORY "${CMAKE_SOURCE_DIR}" CALL jlimesdr_add_library)
