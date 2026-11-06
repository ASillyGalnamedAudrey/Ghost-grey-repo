package com.ghostgrey.player

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

class GameActivity : AppCompatActivity() {

    companion object {
        // LibretroDroid swaps A/B (it maps by physical position). If A and B feel
        // reversed in-game, swap these two lines.
        const val KEY_GBA_A = KeyEvent.KEYCODE_BUTTON_B
        const val KEY_GBA_B = KeyEvent.KEYCODE_BUTTON_A
    }

    private lateinit var retroView: GLRetroView
    private lateinit var ffButton: PadButton
    private var fastForward = false

    private val sramFile by lazy { File(filesDir, "save.srm") }
    private val stateFile by lazy { File(filesDir, "state.bin") }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val data = GLRetroViewData(this).apply {
            coreFilePath = "libcore.so" // mGBA libretro core, placed in jniLibs by the build
            gameFilePath = Patcher.romFile(this@GameActivity).absolutePath
            systemDirectory = filesDir.absolutePath
            savesDirectory = filesDir.absolutePath
            saveRAMState = if (sramFile.exists()) sramFile.readBytes() else null
        }
        retroView = GLRetroView(this, data)
        lifecycle.addObserver(retroView)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111111"))
        }
        val screenH = resources.displayMetrics.widthPixels * 2 / 3 // GBA is 3:2
        root.addView(retroView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, screenH))

        val pad = FrameLayout(this)
        fun add(v: View, w: Int, h: Int, gravity: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) {
            val lp = FrameLayout.LayoutParams(dp(w), dp(h), gravity)
            lp.setMargins(dp(l), dp(t), dp(r), dp(b))
            pad.addView(v, lp)
        }

        add(PadButton(this, "L") { key(KeyEvent.KEYCODE_BUTTON_L1, it) }, 96, 46, Gravity.TOP or Gravity.START, l = 16, t = 10)
        add(PadButton(this, "R") { key(KeyEvent.KEYCODE_BUTTON_R1, it) }, 96, 46, Gravity.TOP or Gravity.END, r = 16, t = 10)

        add(DPad(this) { u, d, l, r ->
            key(KeyEvent.KEYCODE_DPAD_UP, u)
            key(KeyEvent.KEYCODE_DPAD_DOWN, d)
            key(KeyEvent.KEYCODE_DPAD_LEFT, l)
            key(KeyEvent.KEYCODE_DPAD_RIGHT, r)
        }, 160, 160, Gravity.TOP or Gravity.START, l = 16, t = 70)

        add(PadButton(this, "A") { key(KEY_GBA_A, it) }, 72, 72, Gravity.TOP or Gravity.END, r = 20, t = 90)
        add(PadButton(this, "B") { key(KEY_GBA_B, it) }, 72, 72, Gravity.TOP or Gravity.END, r = 104, t = 130)

        val mid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun small(label: String, cb: (Boolean) -> Unit): PadButton {
            val b = PadButton(this, label, cb)
            mid.addView(b, LinearLayout.LayoutParams(dp(84), dp(38)).apply { setMargins(dp(8), 0, dp(8), 0) })
            return b
        }
        small("SELECT") { key(KeyEvent.KEYCODE_BUTTON_SELECT, it) }
        small("START") { key(KeyEvent.KEYCODE_BUTTON_START, it) }
        add(mid, 190, 38, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, b = 24)

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(PadButton(this, "MENU") { if (it) showMenu() }, LinearLayout.LayoutParams(dp(84), dp(38)).apply { setMargins(dp(6), 0, dp(6), 0) })
        ffButton = PadButton(this, "FF") { if (it) toggleFastForward() }
        top.addView(ffButton, LinearLayout.LayoutParams(dp(60), dp(38)).apply { setMargins(dp(6), 0, dp(6), 0) })
        add(top, 170, 38, Gravity.TOP or Gravity.CENTER_HORIZONTAL, t = 14)

        root.addView(pad, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        // Periodic autosave of in-game saves (the battery save), in case the app is killed.
        lifecycleScope.launch {
            while (true) {
                delay(60_000)
                saveSram()
            }
        }
    }

    private fun key(code: Int, down: Boolean) {
        retroView.sendKeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code)
    }

    private fun toggleFastForward() {
        fastForward = !fastForward
        retroView.frameSpeed = if (fastForward) 2 else 1
        ffButton.active = fastForward
    }

    private fun showMenu() {
        AlertDialog.Builder(this)
            .setTitle("Menu")
            .setItems(arrayOf("Save state", "Load state", "Quit game")) { _, which ->
                when (which) {
                    0 -> saveState()
                    1 -> loadState()
                    2 -> { saveSram(); finish() }
                }
            }
            .setNegativeButton("Resume", null)
            .show()
    }

    private fun saveState() {
        lifecycleScope.launch {
            try {
                val bytes = retroView.serializeState()
                val tmp = File(filesDir, "state.bin.tmp")
                tmp.writeBytes(bytes)
                tmp.renameTo(stateFile)
                Toast.makeText(this@GameActivity, "State saved", Toast.LENGTH_SHORT).show()
            } catch (e: Throwable) {
                Toast.makeText(this@GameActivity, "Couldn't save state", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadState() {
        if (!stateFile.exists()) {
            Toast.makeText(this, "No saved state yet", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            retroView.unserializeState(stateFile.readBytes())
            Toast.makeText(this, "State loaded", Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            Toast.makeText(this, "Couldn't load state", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSram() {
        try {
            val bytes = runBlocking { retroView.serializeSRAM() }
            if (bytes.isNotEmpty()) {
                val tmp = File(filesDir, "save.srm.tmp")
                tmp.writeBytes(bytes)
                tmp.renameTo(sramFile)
            }
        } catch (_: Throwable) {
        }
    }

    override fun onPause() {
        saveSram()
        super.onPause()
    }

    // Physical controllers / keyboards with gamepad keys
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val src = event.source
        val isPad = (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (src and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
        if (isPad && event.keyCode != KeyEvent.KEYCODE_BACK) {
            retroView.sendKeyEvent(event.action, event.keyCode)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showMenu()
    }
}
