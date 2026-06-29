package com.tsplayer

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.SurfaceHolder
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var urlInput: TextView
    private lateinit var volumeBar: SeekBar
    private lateinit var progressBar: SeekBar
    private lateinit var tvCurrent: TextView
    private lateinit var tvTotal: TextView
    private lateinit var surfaceHolder: SurfaceHolder

    private var isDragging = false
    private var isPrepared = false
    private var progressUpdater: Thread? = null
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        volumeBar = findViewById(R.id.volumeBar)
        progressBar = findViewById(R.id.progressBar)
        tvCurrent = findViewById(R.id.tvCurrentTime)
        tvTotal = findViewById(R.id.tvTotalTime)
        surfaceHolder = findViewById(R.id.surfaceView).holder
        surfaceHolder.addCallback(this)

        findViewById<TextView>(R.id.btnPlay).setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Insira uma URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            playUrl(url)
        }

        findViewById<TextView>(R.id.btnStop).setOnClickListener {
            stopPlayback()
        }

        volumeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                mediaPlayer?.setVolume(p / 100f, p / 100f)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser && isPrepared) {
                    val dur = mediaPlayer?.duration ?: return
                    if (dur > 0) {
                        mediaPlayer?.seekTo((dur * p / 100).toInt())
                        tvCurrent.text = formatTime((dur * p / 100).toLong())
                    }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { isDragging = true }
            override fun onStopTrackingTouch(sb: SeekBar?) { isDragging = false }
        })
    }

    private fun playUrl(url: String) {
        stopPlayback()
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)
                setDataSource(url)
                setDisplay(surfaceHolder)
                setOnPreparedListener {
                    isPrepared = true
                    start()
                    startProgressUpdater()
                }
                setOnErrorListener { mp, what, extra ->
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Erro: $what/$extra", Toast.LENGTH_LONG).show()
                    }
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Falha: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPlayback() {
        running = false
        isPrepared = false
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        progressBar.progress = 0
        tvCurrent.text = "0:00"
        tvTotal.text = "0:00"
    }

    private fun startProgressUpdater() {
        running = true
        progressUpdater = Thread {
            while (running && isPrepared) {
                try {
                    val mp = mediaPlayer ?: break
                    if (mp.isPlaying && !isDragging) {
                        runOnUiThread { updateProgress(mp) }
                    }
                    Thread.sleep(250)
                } catch (_: Exception) {
                    break
                }
            }
        }.apply { isDaemon = true }
        progressUpdater?.start()
    }

    private fun updateProgress(mp: MediaPlayer) {
        val dur = mp.duration
        val pos = mp.currentPosition
        if (dur > 0) {
            progressBar.progress = ((pos.toFloat() / dur) * 100).toInt()
            tvCurrent.text = formatTime(pos.toLong())
            tvTotal.text = formatTime(dur.toLong())
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = ((totalSec % 3600) / 60).toInt()
        val s = (totalSec % 60).toInt()
        return if (h > 0) "$h:${m.pad()}:${s.pad()}" else "$m:${s.pad()}"
    }

    private fun Int.pad(): String = toString().padStart(2, '0')

    override fun surfaceCreated(holder: SurfaceHolder) {}
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }
}
