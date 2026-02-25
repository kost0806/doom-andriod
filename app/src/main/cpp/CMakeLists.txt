cmake_minimum_required(VERSION 3.22.1)

project("doomandroid")

# Doom screen resolution (passed from Gradle)
if(NOT DEFINED DOOMGENERIC_RESX)
    set(DOOMGENERIC_RESX 640)
endif()
if(NOT DEFINED DOOMGENERIC_RESY)
    set(DOOMGENERIC_RESY 400)
endif()

# Collect all doomgeneric source files
file(GLOB DOOM_SOURCES
    "${CMAKE_CURRENT_SOURCE_DIR}/doomgeneric/doomgeneric/*.c"
)

# Exclude platform-specific implementations that conflict with our Android bridge
# - doomgeneric_*.c    : SDL, Win32, X11, Linux VT, Allegro, Emscripten examples
# - i_sdlsound.c       : requires SDL_mixer
# - i_sdlmusic.c       : requires SDL_mixer
# - i_allegromusic.c   : requires Allegro
# - i_allegrosound.c   : requires Allegro
list(FILTER DOOM_SOURCES EXCLUDE REGEX ".*doomgeneric_(sdl|win|xlib|linuxvt|allegro|emscripten|soso|sosox)\\.c$")
list(FILTER DOOM_SOURCES EXCLUDE REGEX ".*(i_sdlsound|i_sdlmusic|i_allegromusic|i_allegrosound)\\.c$")

# Our Android bridge
list(APPEND DOOM_SOURCES
    "${CMAKE_CURRENT_SOURCE_DIR}/android_doom.c"
)

add_library(
    doom-android
    SHARED
    ${DOOM_SOURCES}
)

target_include_directories(doom-android PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/doomgeneric/doomgeneric"
)

target_compile_definitions(doom-android PRIVATE
    DOOMGENERIC_RESX=${DOOMGENERIC_RESX}
    DOOMGENERIC_RESY=${DOOMGENERIC_RESY}
    ANDROID=1
)

target_compile_options(doom-android PRIVATE
    -O2
    -Wall
    -Wno-unused-function
    -Wno-unused-variable
    -Wno-pointer-sign
    -Wno-incompatible-pointer-types
    -Wno-implicit-function-declaration
    -Wno-int-conversion
    -fvisibility=hidden
)

# Link against Android system libraries
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(doom-android
    ${log-lib}
    ${android-lib}
)
