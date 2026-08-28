package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.model.VideoItem
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerSurface(
    video: VideoItem?,
    isPlaying: Boolean,
    playTrigger: Long = 0L,
    seekRequestMs: Long? = null,
    onSeekHandled: () -> Unit = {},
    onPlaybackStateChanged: (isPlaying: Boolean, isBuffering: Boolean) -> Unit,
    onProgressUpdate: (currentPos: Long, totalDuration: Long) -> Unit,
    onVideoFinished: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val exoPlayer = remember {
        val userAgent = androidx.media3.common.util.Util.getUserAgent(context, "KidsTube")
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // Ultra-low latency LoadControl for instant 0ms playback like MX Player
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 500,
                /* maxBufferMs */ 2000,
                /* bufferForPlaybackMs */ 100, // Starts immediately after 100ms buffer
                /* bufferForPlaybackAfterRebufferMs */ 250
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Hardware-accelerated rendering with fallback
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekForwardIncrementMs(10000)
            .setSeekBackIncrementMs(10000)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                val isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING
                onPlaybackStateChanged(playWhenReady, isBuffering)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                val isPlayActive = exoPlayer.playWhenReady && playbackState != Player.STATE_ENDED
                onPlaybackStateChanged(isPlayActive, isBuffering)

                if (playbackState == Player.STATE_ENDED) {
                    onVideoFinished()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                onError(error.message ?: "Playback error")
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Handle video source changes and instant touch play
    LaunchedEffect(video?.uriString, playTrigger) {
        if (video != null) {
            try {
                val mediaItem = MediaItem.fromUri(Uri.parse(video.uriString))
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                exoPlayer.play()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to load media")
            }
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    // Handle isPlaying state changes
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // Handle seeking
    LaunchedEffect(seekRequestMs) {
        if (seekRequestMs != null) {
            exoPlayer.seekTo(seekRequestMs)
            onSeekHandled()
        }
    }

    // Periodic progress updates
    LaunchedEffect(video, isPlaying) {
        while (true) {
            if (exoPlayer.playbackState == Player.STATE_READY || exoPlayer.isPlaying) {
                val current = exoPlayer.currentPosition.coerceAtLeast(0L)
                val duration = exoPlayer.duration.coerceAtLeast(0L)
                if (duration > 0) {
                    onProgressUpdate(current, duration)
                }
            }
            delay(500)
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
