package com.example.my_player

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.session.MediaButtonReceiver

private const val CHANNEL_ID = "My channel id"

class MyService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private val listSongs = listOf(R.raw.track, R.raw.track2)
    private var currentPosition = 0
    private var mediaSession: MediaSessionCompat? = null
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private lateinit var audioFocusRequest: AudioFocusRequest
    private lateinit var afChangeListener: AudioManager.OnAudioFocusChangeListener

    private val mediaCallback = object : MediaSessionCompat.Callback() {
        var curentState = PlaybackStateCompat.STATE_STOPPED

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onPlay() {
            Log.d("MyLog", "onPlay")
            if (mediaPlayer == null) {
                initPlayer()
            }
            afChangeListener = AudioManager.OnAudioFocusChangeListener { }
            val am = this@MyService.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setOnAudioFocusChangeListener(afChangeListener)
                setAudioAttributes(AudioAttributes.Builder().run {
                    setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    build()
                })
                build()
            }
            val result = am.requestAudioFocus(audioFocusRequest)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                startService(Intent(this@MyService, MyService::class.java))
                mediaSession?.isActive = true
                mediaSession?.setPlaybackState(
                    stateBuilder.setState(
                        PlaybackStateCompat.STATE_PLAYING,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f
                    ).build()
                )
                mediaPlayer?.start()
                curentState = PlaybackStateCompat.STATE_PLAYING
                updateNotification(curentState)
            }
        }

        override fun onPause() {
            Log.d("MyLog", "onPause")
            mediaPlayer?.pause()
            mediaSession?.setPlaybackState(
                stateBuilder.setState(
                    PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f
                ).build()
            )
            curentState = PlaybackStateCompat.STATE_PAUSED
            updateNotification(curentState)
        }

        override fun onStop() {
            Log.d("MyLog", "onStop")
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            mediaSession?.isActive = false
            mediaSession?.setPlaybackState(
                stateBuilder.setState(
                    PlaybackStateCompat.STATE_STOPPED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f
                ).build()
            )
            curentState = PlaybackStateCompat.STATE_STOPPED
            stopSelf()
        }

        override fun onSkipToNext() {
            Log.d("MyLog", "Next")
            if (mediaSession?.isActive == true) {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
                if (currentPosition < 3) {
                    currentPosition++
                } else {
                    currentPosition = 0
                }
                initPlayer()
                mediaPlayer?.start()
                mediaSession?.setPlaybackState(
                    stateBuilder.setState(
                        PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f
                    ).build()
                )
            }
        }

        override fun onSkipToPrevious() {
            Log.d("MyLog", "Previous")
            if (mediaSession?.isActive == true) {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null

                if (currentPosition > 0) {
                    currentPosition--
                } else {
                    currentPosition = 3
                }
                initPlayer()
                mediaPlayer?.start()
                mediaSession?.setPlaybackState(
                    stateBuilder.setState(
                        PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
                        PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f
                    ).build()
                )
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return MusicBinder()
    }

    override fun onCreate() {
        super.onCreate()
        val appContext = this.applicationContext
        mediaSession = MediaSessionCompat(baseContext, "InitMediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                        or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_STOP or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
            setPlaybackState(stateBuilder.build())
            setCallback(mediaCallback)
        }
        val activityIntent = Intent(appContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent, PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession?.setSessionActivity(pendingIntent)
        val mediaButtonIntent = Intent(
            Intent.ACTION_MEDIA_BUTTON,
            null, appContext,
            MediaButtonReceiver::class.java
        )
        mediaSession?.setMediaButtonReceiver(
            PendingIntent.getBroadcast(
                appContext,
                0,
                mediaButtonIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
    }

    private fun initPlayer() {
        mediaPlayer = MediaPlayer.create(this, listSongs[currentPosition])
    }

    private fun getNotification(state: Int) = NotificationCompat.Builder(this, CHANNEL_ID)
        .apply {
            setSmallIcon(R.drawable.baseline_volume_mute_24)
            setContentTitle("Player")
            setContentText("AstonPlayer")
            setContentIntent(mediaSession?.controller?.sessionActivity)
            setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    applicationContext, PlaybackStateCompat.ACTION_STOP
                )
            )
            addAction(
                R.drawable.baseline_skip_previous_24,
                "previous",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    applicationContext,
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
            )
            if (state == PlaybackStateCompat.STATE_PLAYING
            ) {
                addAction(
                    R.drawable.baseline_pause_24, "pause",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        applicationContext,
                        PlaybackStateCompat.ACTION_PAUSE
                    )
                )
            } else {
                addAction(
                    R.drawable.baseline_play_arrow_24, "pause",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        applicationContext,
                        PlaybackStateCompat.ACTION_PLAY
                    )
                )
            }
            addAction(
                R.drawable.baseline_skip_next_24, "next",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    applicationContext,
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
            )
            setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            applicationContext,
                            PlaybackStateCompat.ACTION_STOP
                        )
                    )
            )
            setOnlyAlertOnce(true)
        }
        .build()

    private fun updateNotification(state: Int) {
        when (state) {
            PlaybackStateCompat.STATE_PLAYING -> {
                startForeground(FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, getNotification(state))
            }

            PlaybackStateCompat.STATE_PAUSED -> {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                NotificationManagerCompat.from(this).notify(
                    FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                    getNotification(state)
                )
            }
        }
    }

    inner class MusicBinder : Binder() {
        fun getSessionToken() = mediaSession?.sessionToken
    }
}