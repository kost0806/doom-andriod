package com.doom.android

import android.view.Surface

/**
 * JNI bridge to the native DOOM engine.
 * All public methods are safe to call from any thread.
 */
object DoomEngine {

    init {
        System.loadLibrary("doom-android")
    }

    /** Key constants matching doomkeys.h */
    object Key {
        const val RIGHTARROW = 0xae
        const val LEFTARROW  = 0xac
        const val UPARROW    = 0xad
        const val DOWNARROW  = 0xaf
        const val STRAFE_L   = 0xa0
        const val STRAFE_R   = 0xa1
        const val USE        = 0xa2
        const val FIRE       = 0xa3
        const val ESCAPE     = 0x1b
        const val ENTER      = 0x0d
        const val TAB        = 0x09
        const val RSHIFT     = 0x80 + 0x36   // run
    }

    // ---- Native function declarations ----

    /** Set the absolute path to the WAD file before calling start(). */
    external fun nativeSetWadPath(wadPath: String)

    /** Start the DOOM game loop in a background thread. */
    external fun nativeStart()

    /** Signal the game loop to stop and wait for the thread to exit. */
    external fun nativeStop()

    /**
     * Provide (or remove) the ANativeWindow surface that DOOM renders into.
     * Pass null to release the current surface.
     */
    external fun nativeSetSurface(surface: Surface?)

    /** Inject a key press or release into DOOM's input queue. */
    external fun nativeSendKey(pressed: Boolean, doomKey: Int)
}
