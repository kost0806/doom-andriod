/**
 * android_doom.c
 *
 * Android platform implementation for doomgeneric.
 * Implements the doomgeneric interface (DG_Init, DG_DrawFrame, etc.)
 * and provides JNI functions for the Java/Kotlin side to call.
 *
 * Architecture:
 *   - DOOM runs in a dedicated background thread
 *   - DG_DrawFrame() copies the 640x400 framebuffer to an ANativeWindow surface
 *   - Touch events are queued and consumed by DG_GetKey()
 */

#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <stdint.h>

#include "doomgeneric/doomgeneric.h"

#define TAG "DoomAndroid"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

/* ============================================================
 *  Surface / rendering
 * ============================================================ */

static ANativeWindow *s_window       = NULL;
static pthread_mutex_t s_window_mutex = PTHREAD_MUTEX_INITIALIZER;

/* ============================================================
 *  Key-event queue  (ring buffer, single-producer / single-consumer)
 * ============================================================ */

#define KEY_QUEUE_SIZE 64

typedef struct {
    int           pressed;
    unsigned char key;
} KeyEvent;

static KeyEvent    s_key_queue[KEY_QUEUE_SIZE];
static volatile int s_key_head = 0;   /* writer index */
static volatile int s_key_tail = 0;   /* reader index */
static pthread_mutex_t s_key_mutex = PTHREAD_MUTEX_INITIALIZER;

/* ============================================================
 *  DOOM thread state
 * ============================================================ */

static pthread_t     s_doom_thread;
static volatile int  s_doom_running = 0;
static char          s_wad_path[1024] = {0};

/* ============================================================
 *  doomgeneric interface implementation
 * ============================================================ */

void DG_Init(void)
{
    LOGI("DG_Init: DOOM initialised (%dx%d)", DOOMGENERIC_RESX, DOOMGENERIC_RESY);
}

void DG_DrawFrame(void)
{
    pthread_mutex_lock(&s_window_mutex);
    ANativeWindow *window = s_window;
    if (!window) {
        pthread_mutex_unlock(&s_window_mutex);
        return;
    }

    ANativeWindow_Buffer buf;
    if (ANativeWindow_lock(window, &buf, NULL) != 0) {
        pthread_mutex_unlock(&s_window_mutex);
        return;
    }

    const int srcW = DOOMGENERIC_RESX;
    const int srcH = DOOMGENERIC_RESY;
    const int dstW = buf.width;
    const int dstH = buf.height;

    uint32_t *src = (uint32_t *)DG_ScreenBuffer;
    uint32_t *dst = (uint32_t *)buf.bits;

    /*
     * Scale the 640x400 DOOM framebuffer up to whatever the surface size is.
     * doomgeneric uses 0xAARRGGBB; Android RGBX_8888 wants 0xXXBBGGRR in
     * little-endian memory, so we swap R↔B.
     */
    for (int y = 0; y < dstH; y++) {
        int srcY = y * srcH / dstH;
        for (int x = 0; x < dstW; x++) {
            int srcX = x * srcW / dstW;
            uint32_t p = src[srcY * srcW + srcX];
            uint32_t r = (p >> 16) & 0xFF;
            uint32_t g = (p >>  8) & 0xFF;
            uint32_t b =  p        & 0xFF;
            dst[y * buf.stride + x] = 0xFF000000u | (b << 16) | (g << 8) | r;
        }
    }

    ANativeWindow_unlockAndPost(window);
    pthread_mutex_unlock(&s_window_mutex);
}

void DG_SleepMs(uint32_t ms)
{
    struct timespec ts;
    ts.tv_sec  =  ms / 1000;
    ts.tv_nsec = (ms % 1000) * 1000000L;
    nanosleep(&ts, NULL);
}

uint32_t DG_GetTicksMs(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint32_t)(ts.tv_sec * 1000ULL + ts.tv_nsec / 1000000ULL);
}

int DG_GetKey(int *pressed, unsigned char *doomKey)
{
    pthread_mutex_lock(&s_key_mutex);
    if (s_key_head == s_key_tail) {
        pthread_mutex_unlock(&s_key_mutex);
        return 0;
    }
    KeyEvent evt   = s_key_queue[s_key_tail];
    s_key_tail     = (s_key_tail + 1) % KEY_QUEUE_SIZE;
    pthread_mutex_unlock(&s_key_mutex);

    *pressed = evt.pressed;
    *doomKey  = evt.key;
    return 1;
}

void DG_SetWindowTitle(const char *title)
{
    LOGI("DG_SetWindowTitle: %s", title);
}

/* ============================================================
 *  DOOM thread
 * ============================================================ */

static void *doom_thread_func(void *arg)
{
    LOGI("DOOM thread started, WAD: %s", s_wad_path);

    /* Change working directory so DOOM can write save-games next to the WAD */
    char dir[1024];
    strncpy(dir, s_wad_path, sizeof(dir) - 1);
    dir[sizeof(dir) - 1] = '\0';
    char *slash = strrchr(dir, '/');
    if (slash) {
        *slash = '\0';
        if (chdir(dir) != 0)
            LOGE("chdir(%s) failed", dir);
    }

    /* Build argv for DOOM */
    char *argv[] = { "doom", "-iwad", s_wad_path, NULL };
    doomgeneric_Create(3, argv);

    while (s_doom_running) {
        doomgeneric_Tick();
    }

    LOGI("DOOM thread exiting");
    return NULL;
}

/* ============================================================
 *  JNI interface
 * ============================================================ */

JNIEXPORT void JNICALL
Java_com_doom_android_DoomEngine_nativeSetWadPath(JNIEnv *env, jobject obj,
                                                   jstring wadPath)
{
    const char *path = (*env)->GetStringUTFChars(env, wadPath, NULL);
    strncpy(s_wad_path, path, sizeof(s_wad_path) - 1);
    s_wad_path[sizeof(s_wad_path) - 1] = '\0';
    (*env)->ReleaseStringUTFChars(env, wadPath, path);
    LOGI("WAD path set to: %s", s_wad_path);
}

JNIEXPORT void JNICALL
Java_com_doom_android_DoomEngine_nativeStart(JNIEnv *env, jobject obj)
{
    if (s_doom_running) {
        LOGI("DOOM already running");
        return;
    }
    s_doom_running = 1;
    if (pthread_create(&s_doom_thread, NULL, doom_thread_func, NULL) != 0) {
        LOGE("Failed to create DOOM thread");
        s_doom_running = 0;
    }
}

JNIEXPORT void JNICALL
Java_com_doom_android_DoomEngine_nativeStop(JNIEnv *env, jobject obj)
{
    s_doom_running = 0;
    pthread_join(s_doom_thread, NULL);
    LOGI("DOOM thread joined");
}

JNIEXPORT void JNICALL
Java_com_doom_android_DoomEngine_nativeSetSurface(JNIEnv *env, jobject obj,
                                                   jobject surface)
{
    pthread_mutex_lock(&s_window_mutex);

    if (s_window) {
        ANativeWindow_release(s_window);
        s_window = NULL;
    }

    if (surface) {
        s_window = ANativeWindow_fromSurface(env, surface);
        if (s_window) {
            /* Request RGBX_8888 format (format id = 2) */
            ANativeWindow_setBuffersGeometry(s_window, 0, 0,
                                             WINDOW_FORMAT_RGBX_8888);
            LOGI("Surface set: %dx%d",
                 ANativeWindow_getWidth(s_window),
                 ANativeWindow_getHeight(s_window));
        } else {
            LOGE("ANativeWindow_fromSurface returned NULL");
        }
    } else {
        LOGI("Surface cleared (NULL)");
    }

    pthread_mutex_unlock(&s_window_mutex);
}

JNIEXPORT void JNICALL
Java_com_doom_android_DoomEngine_nativeSendKey(JNIEnv *env, jobject obj,
                                                jboolean pressed, jint doomKey)
{
    pthread_mutex_lock(&s_key_mutex);
    int next = (s_key_head + 1) % KEY_QUEUE_SIZE;
    if (next != s_key_tail) {
        s_key_queue[s_key_head].pressed = pressed ? 1 : 0;
        s_key_queue[s_key_head].key     = (unsigned char)doomKey;
        s_key_head = next;
    }
    pthread_mutex_unlock(&s_key_mutex);
}
