package com.iptv.tvplayer.player

import android.content.Context
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.iptv.tvplayer.R
import com.iptv.tvplayer.data.SettingsManager
import com.iptv.tvplayer.proxy.ProxyManager
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.*

object NativePlayerManager {
    private var exoPlayer: ExoPlayer? = null
    private var currentView: View? = null
    private var mpvJob: Job? = null
    private var timeoutJob: Job? = null
    var onAutoSwitchRequested: (() -> Unit)? = null
    
    private fun startTimeoutJob() {
        timeoutJob?.cancel()
        timeoutJob = CoroutineScope(Dispatchers.Main).launch {
            delay(SettingsManager.timeoutSeconds * 1000L)
            onAutoSwitchRequested?.invoke()
        }
    }

    private fun cancelTimeoutJob() {
        timeoutJob?.cancel()
        timeoutJob = null
    }
    
    var onPlayerReady: (() -> Unit)? = null
    var onPlayerBuffering: (() -> Unit)? = null
    
    fun getCurrentView(): View? = currentView

    fun createExoPlayer(context: Context, url: String): View {
        if (exoPlayer != null && currentView is PlayerView) {
            exoPlayer?.setMediaItem(MediaItem.fromUri(url))
            exoPlayer?.prepare()
            exoPlayer?.playWhenReady = true
            startTimeoutJob()
            onPlayerBuffering?.invoke()
            return currentView!!
        }
        release()
        val playerView = LayoutInflater.from(context).inflate(R.layout.custom_exo_player, null) as PlayerView
        playerView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        playerView.isFocusable = false
        playerView.isFocusableInTouchMode = false
        playerView.setKeepContentOnPlayerReset(true)
        playerView.resizeMode = if (SettingsManager.aspectRatio == "全屏") {
            AspectRatioFrameLayout.RESIZE_MODE_FILL
        } else {
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        }

        val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
            if (SettingsManager.userAgent.isNotBlank()) setUserAgent(SettingsManager.userAgent)
            if (SettingsManager.headers.isNotBlank()) {
                val headersMap = mutableMapOf<String, String>()
                SettingsManager.headers.split(",").forEach { part ->
                    val kv = part.split(":", limit = 2)
                    if (kv.size == 2) headersMap[kv[0].trim()] = kv[1].trim()
                }
                setDefaultRequestProperties(headersMap)
            }
            setConnectTimeoutMs(10000)
            setReadTimeoutMs(10000)
        }

        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(32000, 64000, 2000, 5000)
            .build()

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE) {
                    startTimeoutJob()
                    onPlayerBuffering?.invoke()
                } else if (playbackState == Player.STATE_READY) {
                    cancelTimeoutJob()
                    onPlayerReady?.invoke()
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                onAutoSwitchRequested?.invoke()
            }
        })

        playerView.player = exoPlayer
        exoPlayer?.setMediaItem(MediaItem.fromUri(url))
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true
        
        startTimeoutJob()
        onPlayerBuffering?.invoke()
        currentView = playerView
        return playerView
    }

    private var mpvInitialized = false
    private val mpvLock = Any()
    private var pendingMpvUrl: String? = null

    fun createMPVPlayer(context: Context, url: String): View {
        if (currentView is SurfaceView) {
            startTimeoutJob()
            onPlayerBuffering?.invoke()
            synchronized(mpvLock) {
                if (mpvInitialized) {
                    MPVLib.command(arrayOf("loadfile", url))
                } else {
                    pendingMpvUrl = url
                }
            }
            return currentView!!
        }
        release()
        val surfaceView = SurfaceView(context)
        surfaceView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                synchronized(mpvLock) {
                    if (mpvInitialized) return
                    MPVLib.create(context)
                    mpvInitialized = true
                    MPVLib.setOptionString("sub-font-provider", "none")
                    
                    // Enhance IPTV compatibility
                    MPVLib.setOptionString("hwdec", "mediacodec,auto")
                    MPVLib.setOptionString("hwdec-codecs", "all")
                    MPVLib.setOptionString("vo", "gpu")
                    MPVLib.setOptionString("deinterlace", "auto") // Auto deinterlace for 1080i streams
                    MPVLib.setOptionString("framedrop", "vo") // Drop video frames if audio gets out of sync
                    MPVLib.setOptionString("vd-lavc-threads", "4") // Multithreaded software decode fallback
                    
                    if (SettingsManager.aspectRatio == "全屏") {
                        MPVLib.setOptionString("keepaspect", "no")
                    } else {
                        MPVLib.setOptionString("keepaspect", "yes")
                    }
                    
                    MPVLib.setOptionString("profile", "fast")
                    MPVLib.setOptionString("tls-verify", "no")
                    MPVLib.setOptionString("cache", "yes")
                    MPVLib.setOptionString("demuxer-max-bytes", "64M")
                    MPVLib.setOptionString("demuxer-max-back-bytes", "32M")
                    MPVLib.setOptionString("demuxer-readahead-secs", "10")
                    MPVLib.setOptionString("network-timeout", SettingsManager.timeoutSeconds.toString())
                    
                    if (SettingsManager.userAgent.isNotBlank()) {
                        MPVLib.setOptionString("user-agent", SettingsManager.userAgent)
                    }
                    if (SettingsManager.headers.isNotBlank()) {
                        MPVLib.setOptionString("http-header-fields", SettingsManager.headers.replace(",", "\r\n"))
                    }
                    
                    MPVLib.init()
                    MPVLib.attachSurface(holder.surface)
                    MPVLib.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
                    MPVLib.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
                    MPVLib.observeProperty("playback-time", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
                    
                    MPVLib.addObserver(object : MPVLib.EventObserver {
                        override fun eventProperty(property: String) {}
                        override fun eventProperty(property: String, value: Long) {}
                        override fun eventProperty(property: String, value: Boolean) {
                            if (!mpvInitialized) return
                            if (property == "eof-reached" && value) {
                                if (SettingsManager.autoReconnect) {
                                    MPVLib.command(arrayOf("loadfile", pendingMpvUrl ?: url))
                                } else {
                                    onAutoSwitchRequested?.invoke()
                                }
                            } else if (property == "paused-for-cache") {
                                if (value) {
                                    startTimeoutJob()
                                    onPlayerBuffering?.invoke()
                                } else {
                                    cancelTimeoutJob()
                                    onPlayerReady?.invoke()
                                }
                            }
                        }
                        override fun eventProperty(property: String, value: String) {}
                        override fun eventProperty(property: String, value: Double) {
                            if (!mpvInitialized) return
                            if (property == "playback-time" && value > 0) {
                                cancelTimeoutJob()
                                onPlayerReady?.invoke()
                            }
                        }
                        override fun event(eventId: Int) {
                            if (!mpvInitialized) return
                        }
                    })
                    startTimeoutJob()
                    onPlayerBuffering?.invoke()
                    MPVLib.command(arrayOf("loadfile", pendingMpvUrl ?: url))
                    pendingMpvUrl = null
                }
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                synchronized(mpvLock) {
                    if (mpvInitialized) {
                        try {
                            MPVLib.setPropertyString("android-surface-size", "${width}x$height")
                        } catch (e: Exception) {}
                    }
                }
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                synchronized(mpvLock) {
                    if (mpvInitialized) {
                        try {
                            MPVLib.setPropertyString("vo", "null")
                            MPVLib.detachSurface()
                            MPVLib.destroy()
                        } catch (e: Exception) {}
                        mpvInitialized = false
                    }
                }
            }
        })
        currentView = surfaceView
        return surfaceView
    }

    fun release() {
        cancelTimeoutJob()
        exoPlayer?.release()
        exoPlayer = null
        synchronized(mpvLock) {
            if (mpvInitialized) {
                try {
                    MPVLib.setPropertyString("vo", "null")
                    MPVLib.detachSurface()
                    MPVLib.destroy()
                } catch (e: Exception) {}
                mpvInitialized = false
            }
        }
        currentView = null
    }
}
