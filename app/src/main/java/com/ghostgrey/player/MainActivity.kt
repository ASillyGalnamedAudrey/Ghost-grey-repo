package com.ghostgrey.player

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var playBtn: Button
    private lateinit var pickBtn: Button
    private lateinit var spinner: ProgressBar

    private val picker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) patchFrom(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#121212"))
        }

        root.addView(TextView(this).apply {
            text = "Pokémon Ghost Grey"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Fan-made ROM hack player.\nThis app contains no game. You supply your own " +
                "Pokémon FireRed (v1.0, USA) ROM and the app applies the Ghost Grey patch on your device."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad)
        })

        status = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, pad / 2)
        }
        root.addView(status)

        spinner = ProgressBar(this).apply { visibility = android.view.View.GONE }
        root.addView(spinner)

        playBtn = Button(this).apply {
            text = "Play"
            setOnClickListener { startActivity(Intent(this@MainActivity, GameActivity::class.java)) }
        }
        root.addView(playBtn)

        pickBtn = Button(this).apply {
            setOnClickListener { picker.launch(arrayOf("*/*")) }
        }
        root.addView(pickBtn)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val ready = Patcher.romFile(this).exists()
        playBtn.visibility = if (ready) android.view.View.VISIBLE else android.view.View.GONE
        pickBtn.text = if (ready) "Re-patch with a different ROM" else "Choose your FireRed ROM"
        status.text = if (ready) "Game is ready." else "First, choose your FireRed ROM file."
    }

    private fun setBusy(busy: Boolean, msg: String? = null) {
        spinner.visibility = if (busy) android.view.View.VISIBLE else android.view.View.GONE
        pickBtn.isEnabled = !busy
        playBtn.isEnabled = !busy
        if (msg != null) status.text = msg
    }

    private fun patchFrom(uri: Uri) {
        setBusy(true, "Patching… this takes a few seconds.")
        Thread {
            val error: String? = try {
                val base = contentResolver.openInputStream(uri)!!.use { Patcher.readLimited(it) }
                Patcher.buildGame(this, base)
                null
            } catch (e: Patcher.PatchException) {
                e.message
            } catch (e: Throwable) {
                "Something went wrong: ${e.message}"
            }
            runOnUiThread {
                setBusy(false)
                refresh()
                if (error != null) {
                    AlertDialog.Builder(this)
                        .setTitle("Couldn't patch")
                        .setMessage(error)
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    status.text = "Patched and verified. Ready to play!"
                }
            }
        }.start()
    }
}
