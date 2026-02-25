package com.doom.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.doom.android.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val PERM_REQUEST = 100

    // Candidate WAD file names (shareware + full)
    private val WAD_NAMES = listOf("doom1.wad", "doom.wad", "doom2.wad",
                                   "DOOM1.WAD", "DOOM.WAD", "DOOM2.WAD")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hideSystemUI()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestStoragePermIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        DoomEngine.nativeStop()
    }

    // ---- Permissions ----------------------------------------------------

    private fun requestStoragePermIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ : app-specific external dir needs no permission
            startDoom()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startDoom()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    PERM_REQUEST
                )
            }
        } else {
            startDoom()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST) {
            startDoom()
        }
    }

    // ---- WAD search & launch --------------------------------------------

    private fun startDoom() {
        val wad = findWad()
        if (wad == null) {
            showWadMissingDialog()
            return
        }

        DoomEngine.nativeSetWadPath(wad.absolutePath)
        DoomEngine.nativeStart()
    }

    /**
     * Search for a WAD file in the app-private external directory
     * and on the public external storage root.
     *
     * Priority: app-specific dir → public /sdcard → public /sdcard/DOOM
     */
    private fun findWad(): File? {
        val searchDirs = mutableListOf<File>()

        // App-private external storage  →  /sdcard/Android/data/com.doom.android/files/
        getExternalFilesDir(null)?.let { searchDirs += it }

        // Public external storage root and sub-directory
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED ||
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED_READ_ONLY
        ) {
            searchDirs += Environment.getExternalStorageDirectory()
            searchDirs += File(Environment.getExternalStorageDirectory(), "DOOM")
            searchDirs += File(Environment.getExternalStorageDirectory(), "doom")
        }

        for (dir in searchDirs) {
            if (!dir.exists()) continue
            for (name in WAD_NAMES) {
                val candidate = File(dir, name)
                if (candidate.exists() && candidate.length() > 0) {
                    return candidate
                }
            }
        }
        return null
    }

    private fun showWadMissingDialog() {
        val appDir = getExternalFilesDir(null)?.absolutePath ?: "app storage"

        AlertDialog.Builder(this)
            .setTitle("WAD file not found")
            .setMessage(
                "DOOM requires a WAD file to run.\n\n" +
                "• Free shareware WAD (doom1.wad) can be downloaded from:\n" +
                "  https://www.doomworld.com/classicdoom/info/shareware.php\n\n" +
                "• Copy doom1.wad (or doom.wad / doom2.wad) to:\n" +
                "  $appDir\n\n" +
                "Then restart the app."
            )
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    // ---- Immersive / full-screen UI -------------------------------------

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }
}
