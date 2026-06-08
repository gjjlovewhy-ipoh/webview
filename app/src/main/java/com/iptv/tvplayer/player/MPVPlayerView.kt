package com.iptv.tvplayer.player

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.iptv.tvplayer.data.SettingsManager

@Composable
fun MPVPlayerView(
    url: String,
    modifier: Modifier = Modifier
) {
    var isInitialized by remember { mutableStateOf(false) }
    
    // Safely load the URL only when MPV is fully initialized
    LaunchedEffect(url, isInitialized) {
        if (isInitialized && url.isNotEmpty()) {
            try {
                // Clear any old proxy settings
                MPVLib.setOptionString("http-proxy", "")
                
                // Apply Aspect Ratio
                if (SettingsManager.aspectRatioState.value == "填充") {
                    MPVLib.setOptionString("panscan", "1.0")
                } else {
                    MPVLib.setOptionString("panscan", "0.0")
                    MPVLib.setOptionString("video-aspect-override", "no")
                }
                
                // Apply Headers & UA
                if (SettingsManager.userAgent.isNotBlank()) {
                    MPVLib.setOptionString("user-agent", SettingsManager.userAgent)
                }
                val customHeaders = SettingsManager.headers
                if (customHeaders.isNotEmpty()) {
                    MPVLib.setOptionString("http-header-fields", customHeaders)
                }

                MPVLib.command(arrayOf("loadfile", url))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Timeout Monitor & Reconnect
    val timeoutSeconds = SettingsManager.timeoutSecondsState.value
    val autoReconnect = SettingsManager.autoReconnectState.value
    var lastPlaybackTime by remember { mutableStateOf(-1) }
    var stuckSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(url, isInitialized) {
        if (isInitialized && url.isNotEmpty()) {
            while (true) {
                delay(1000)
                try {
                    val ptime = MPVLib.getPropertyInt("playback-time") ?: -1
                    val isPaused = MPVLib.getPropertyBoolean("core-idle") ?: false
                    if (ptime == lastPlaybackTime) {
                        if (!isPaused) stuckSeconds++
                    } else {
                        stuckSeconds = 0
                        lastPlaybackTime = ptime
                    }
                    
                    val w = MPVLib.getPropertyInt("width") ?: 0
                    val h = MPVLib.getPropertyInt("height") ?: 0
                    if (w > 0 && h > 0) {
                        PlayerStateManager.videoResolution.value = "${w}x${h}"
                    }
                    
                    if (stuckSeconds >= timeoutSeconds) {
                        stuckSeconds = 0
                        if (autoReconnect) {
                            MPVLib.command(arrayOf("loadfile", url))
                        }
                    }
                } catch (e: Exception) {
                    stuckSeconds++
                    if (stuckSeconds >= timeoutSeconds && autoReconnect) {
                        stuckSeconds = 0
                        try { MPVLib.command(arrayOf("loadfile", url)) } catch (e2: Exception) {}
                    }
                }
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val surfaceView = android.view.SurfaceView(context).apply {
                holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
                
                holder.addCallback(object : android.view.SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                        MPVLib.create(context)
                        MPVLib.setOptionString("sub-font-provider", "none")
                        MPVLib.setOptionString("hwdec", "auto")
                        MPVLib.setOptionString("vo", "gpu")
                        MPVLib.init()
                        MPVLib.attachSurface(holder.surface)
                        
                        MPVLib.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
                        MPVLib.addObserver(object : MPVLib.EventObserver {
                            override fun eventProperty(property: String) {}
                            override fun eventProperty(property: String, value: Long) {}
                            override fun eventProperty(property: String, value: Boolean) {
                                if (property == "eof-reached" && value && SettingsManager.autoReconnect) {
                                    MPVLib.command(arrayOf("loadfile", url))
                                }
                            }
                            override fun eventProperty(property: String, value: String) {}
                            override fun eventProperty(property: String, value: Double) {}
                            override fun event(eventId: Int) {}
                        })
                        isInitialized = true
                    }

                    override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {
                        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
                    }

                    override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                        isInitialized = false
                        MPVLib.setPropertyString("vo", "null")
                        MPVLib.detachSurface()
                        MPVLib.destroy()
                    }
                })
            }
            
            surfaceView
        },
        update = { view ->
        }
    )
}
