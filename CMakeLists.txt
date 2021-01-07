cmake_minimum_required(VERSION 3.4)

include(ExternalProject)

project(olm VERSION 3.1.4 LANGUAGES CXX C)

option(OLM_TESTS "Build tests" ON)
option(BUILD_SHARED_LIBS "Build as a shared library" OFF)

add_definitions(-DOLMLIB_VERSION_MAJOR=${PROJECT_VERSION_MAJOR})
add_definitions(-DOLMLIB_VERSION_MINOR=${PROJECT_VERSION_MINOR})
add_definitions(-DOLMLIB_VERSION_PATCH=${PROJECT_VERSION_PATCH})

#set(CMAKE_VERBOSE_MAKEFILE ON)

set(CMAKE_MODULE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/cmake)
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_WINDOWS_EXPORT_ALL_SYMBOLS ON)

# iOS configuration
set(CMAKE_SYSTEM_NAME Darwin)
set(CMAKE_SYSTEM_VERSION 12.0)

set (UNIX True)
set (APPLE True)
set (IOS True)

set(CMAKE_SYSTEM_NAME iOS)
#set(CMAKE_SYSTEM_PROCESSOR arm)
set(PLATFORM_NAME iphonesimulator)
set(PLATFORM_FLAG_NAME ios)

set(CMAKE_OSX_DEPLOYMENT_TARGET "" CACHE STRING "Force unset of the deployment target for iOS" FORCE)
set (CMAKE_IOS_DEVELOPER_ROOT "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform//Developer")
set (CMAKE_IOS_SDK_ROOT "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneOS.platform/Developer/SDKs/iPhoneOS.sdk")
set (CMAKE_FIND_FRAMEWORK FIRST)
set (CMAKE_SYSTEM_FRAMEWORK_PATH
    ${CMAKE_IOS_SDK_ROOT}/System/Library/Frameworks
    ${CMAKE_IOS_SDK_ROOT}/System/Library/PrivateFrameworks
    ${CMAKE_IOS_SDK_ROOT}/Developer/Library/Frameworks
)

#set(CMAKE_OSX_ARCHITECTURES arm64)
set(CMAKE_OSX_SYSROOT "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator.sdk")

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -arch x86_64 -miphoneos-version-min=11.0")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -arch x86_64 -miphoneos-version-min=11.0")

INCLUDE_DIRECTORIES(/usr/local/include/ios64)
LINK_DIRECTORIES(/usr/local/lib/ios64)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

add_library(self_olm
    src/account.cpp
    src/base64.cpp
    src/cipher.cpp
    src/crypto.cpp
    src/memory.cpp
    src/message.cpp
    src/pickle.cpp
    src/ratchet.cpp
    src/session.cpp
    src/utility.cpp
    src/pk.cpp
    src/sas.c

    src/ed25519.c
    src/error.c
    src/inbound_group_session.c
    src/megolm.c
    src/olm.cpp
    src/outbound_group_session.c
    src/pickle_encoding.c

    lib/crypto-algorithms/aes.c
    lib/crypto-algorithms/sha256.c
    lib/curve25519-donna/curve25519-donna.c)
add_library(Olm::Olm ALIAS self_olm)


target_include_directories(self_olm
    PUBLIC
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
        $<INSTALL_INTERFACE:${CMAKE_INSTALL_INCLUDEDIR}>
    PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/lib)

set_target_properties(self_olm PROPERTIES
   SOVERSION ${PROJECT_VERSION_MAJOR}
   VERSION ${PROJECT_VERSION})

set_target_properties(self_olm PROPERTIES
    ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/${CMAKE_INSTALL_BINDIR}
    LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/${CMAKE_INSTALL_LIBDIR}
    RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/${CMAKE_INSTALL_LIBDIR})

# Link libsodium
find_library(sodium NAMES sodium REQUIRED PATHS /usr/local/include PATH_SUFFIXES ios64)
target_link_libraries(self_olm sodium)

#
# Installation
#
include(GNUInstallDirs)
set(INSTALL_CONFIGDIR ${CMAKE_INSTALL_LIBDIR}/cmake/Olm)
install(TARGETS self_olm
    EXPORT olm-targets
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
    ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR}
    INCLUDES DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}
)

# The exported target will be named Olm.
set_target_properties(self_olm PROPERTIES EXPORT_NAME Olm)
install(FILES
    ${CMAKE_SOURCE_DIR}/include/self_olm/olm.h
    ${CMAKE_SOURCE_DIR}/include/self_olm/outbound_group_session.h
    ${CMAKE_SOURCE_DIR}/include/self_olm/inbound_group_session.h
    ${CMAKE_SOURCE_DIR}/include/self_olm/pk.h
    ${CMAKE_SOURCE_DIR}/include/self_olm/sas.h
DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}/self_olm)

# Export the targets to a script.
install(EXPORT olm-targets
  FILE OlmTargets.cmake
  NAMESPACE Olm::
  DESTINATION ${INSTALL_CONFIGDIR})

# Create a ConfigVersion.cmake file.
include(CMakePackageConfigHelpers)
write_basic_package_version_file(
    ${CMAKE_CURRENT_BINARY_DIR}/OlmConfigVersion.cmake
    VERSION ${PROJECT_VERSION}
    COMPATIBILITY SameMajorVersion)

configure_package_config_file(
    ${CMAKE_CURRENT_LIST_DIR}/cmake/OlmConfig.cmake.in
    ${CMAKE_CURRENT_BINARY_DIR}/OlmConfig.cmake
    INSTALL_DESTINATION ${INSTALL_CONFIGDIR})

#Install the config & configversion.
install(FILES
    ${CMAKE_CURRENT_BINARY_DIR}/OlmConfig.cmake
    ${CMAKE_CURRENT_BINARY_DIR}/OlmConfigVersion.cmake
    DESTINATION ${INSTALL_CONFIGDIR})

# Register package in user's package registry
export(EXPORT olm-targets
    FILE ${CMAKE_CURRENT_BINARY_DIR}/OlmTargets.cmake
    NAMESPACE Olm::)
export(PACKAGE Olm)

if (OLM_TESTS)
   add_subdirectory(tests)
endif()
