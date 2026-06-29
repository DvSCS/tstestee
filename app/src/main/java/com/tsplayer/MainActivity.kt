package com.tsplayer

import android.net.Uri
import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var urlInput: TextView
    private lateinit var btnPlay: TextView
    private lateinit var btnStop: TextView
    private lateinit var volumeBar: SeekBar
    private lateinit var progressBar: SeekBar
    private lateinit var tvCurrent: TextView
    private lateinit var tvTotal: TextView

    private var isDragging = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        btnPlay = findViewById(R.id.btnPlay)
        btnStop = findViewById(R.id.btnStop)
        volumeBar = findViewById(R.id.volumeBar)
        progressBar = findViewById(R.id.progressBar)
        tvCurrent = findViewById(R.id.tvCurrentTime)
        tvTotal = findViewById(R.id.tvTotalTime)

        player = ExoPlayer.Builder(this).build()
        findViewById<com.google.android.exoplayer2.ui.PlayerView>(R.id.playerView).player = player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && !isDragging) {
                    updateProgress()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Erro: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startProgressUpdater()
                }
            }
        })

        btnPlay.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Insira uma URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            playUrl(url)
        }

        btnStop.setOnClickListener {
            player.stop()
            player.clearMediaItems()
            progressBar.progress = 0
            tvCurrent.text = "0:00"
            tvTotal.text = "0:00"
        }

        volumeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                player.volume = p / 100f
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    val dur = player.duration
                    if (dur > 0) {
                        player.seekTo((dur * p / 100).toLong())
                        tvCurrent.text = formatTime((dur * p / 100).toLong())
                    }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { isDragging = true }
            override fun onStopTrackingTouch(sb: SeekBar?) { isDragging = false }
        })
    }

    private fun playUrl(url: String) {
        try {
            player.stop()
            player.clearMediaItems()

            val dataSourceFactory = DefaultHttpDataSource.Factory()
            val source = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(Uri.parse(url)))

            player.setMediaSource(source)
            player.prepare()
            player.play()
        } catch (e: Exception) {
            Toast.makeText(this, "Falha: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startProgressUpdater() {
        Thread {
            while (player.isPlaying) {
                if (!isDragging) {
                    runOnUiThread { updateProgress() }
                }
                Thread.sleep(250)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun updateProgress() {
        val dur = player.duration
        val pos = player.currentPosition
        if (dur > 0) {
            progressBar.progress = ((pos.toFloat() / dur) * 100).toInt()
            tvCurrent.text = formatTime(pos)
            tvTotal.text = formatTime(dur)
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

    override fun onStop() {
        super.onStop()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
