package com.example.my_player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.example.my_player.databinding.ActivityMainBinding


private const val CHANNEL_ID = "My channel id"

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var playerService: MyService.MusicBinder
    private var bound = false
    private var pauseBtnState = false
    private lateinit var mediaController: MediaControllerCompat

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
            playerService = service as MyService.MusicBinder
            mediaController =
                MediaControllerCompat(this@MainActivity, playerService.getSessionToken()!!)
            mediaController.registerCallback(object : MediaControllerCompat.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                    if (state == null) {
                        return
                    }
                    if ((state.state == PlaybackStateCompat.STATE_PLAYING) ||
                        (state.state == PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS) ||
                        (state.state == PlaybackStateCompat.STATE_SKIPPING_TO_NEXT)
                    ) {
                        binding.btnPlayPause.setImageResource(R.drawable.baseline_pause_24)
                        pauseBtnState = true
                    } else {
                        binding.btnPlayPause.setImageResource(R.drawable.baseline_play_arrow_24)
                        pauseBtnState = false
                    }
                }
            })
            bound = true
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        createNotificationChannel()
        bindService(Intent(this, MyService::class.java), connection, Context.BIND_AUTO_CREATE)

        binding.btnPlayPause.setOnClickListener {
            if (bound && !pauseBtnState) {
                mediaController.transportControls.play()
            } else if (bound) {
                mediaController.transportControls.pause()
            }
        }
        binding.btnNext.setOnClickListener {
            if (bound) {
                mediaController.transportControls.skipToNext()
            }
        }
        binding.btnPrevious.setOnClickListener {
            if (bound) {
                mediaController.transportControls.skipToPrevious()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.app_name)
            val descriptionText = getString(R.string.app_name)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}



