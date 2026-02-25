package com.doom.android

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * SurfaceView that hands its Surface to the native DOOM renderer.
 */
class GameSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    init {
        holder.addCallback(this)
        // Keep the surface on top so DOOM renders correctly
        holder.setType(SurfaceHolder.SURFACE_TYPE_NORMAL)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        DoomEngine.nativeSetSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Re-attach so the native side picks up the new geometry
        DoomEngine.nativeSetSurface(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        DoomEngine.nativeSetSurface(null)
    }
}
