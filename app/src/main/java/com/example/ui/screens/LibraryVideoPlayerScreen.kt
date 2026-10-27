package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.PlaybackRequest
import com.example.ui.theme.FoulBlackBg
import com.example.ui.theme.FoulCardDark
import com.example.ui.theme.FoulPurpleBright
import com.example.ui.theme.FoulPurpleDeep
import com.example.ui.theme.FoulPurpleGlow
import kotlinx.coroutines.delay
import java.util.Locale

private enum class PlayerAspectMode(val resizeMode: Int, val labelAr: String) {
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, "الأبعاد الأصلية"),
    ZOOM(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "ملء الشاشة من الأطراف"),
    FILL(AspectRatioFrameLayout.RESIZE_MODE_FILL, "ملء كامل الشاشة")
}

@OptIn(UnstableApi::class)
@Composable
fun LibraryVideoPlayerScreen(
    playbackRequest: PlaybackRequest,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // Immediately lock into Landscape mode & immersive fullscreen when playing
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity?.window
        val insetsController = if (window != null) {
            WindowCompat.getInsetsController(window, window.decorView)
        } else null
        insetsController?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            activity?.requestedOrientation = originalOrientation
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val candidateUrls = playbackRequest.candidateUrls
    var currentUrlIndex by remember(playbackRequest) { mutableIntStateOf(0) }
    var isBuffering by remember(playbackRequest) { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var allExtensionsFailed by remember(playbackRequest) { mutableStateOf(false) }

    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var aspectMode by remember { mutableStateOf(PlayerAspectMode.FIT) }
    var showAspectToast by remember { mutableStateOf(false) }

    // Double-tap seek feedback (-10s or +10s)
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var seekFeedbackIsForward by remember { mutableStateOf(true) }
    var seekFeedbackTrigger by remember { mutableIntStateOf(0) }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    // Load current candidate URL (mp4 -> mkv -> m3u8) silently with only spinner visible
    LaunchedEffect(currentUrlIndex, allExtensionsFailed, playbackRequest) {
        if (!allExtensionsFailed && currentUrlIndex in candidateUrls.indices) {
            isBuffering = true
            val urlToPlay = candidateUrls[currentUrlIndex]
            val mediaItem = MediaItem.fromUri(Uri.parse(urlToPlay))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else if (candidateUrls.isEmpty()) {
            isBuffering = false
            allExtensionsFailed = true
        }
    }

    // Attach listener for silent automatic fallback: mp4 -> mkv -> m3u8 -> error message
    DisposableEffect(exoPlayer, candidateUrls) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                        allExtensionsFailed = false
                        durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                        isPlaying = false
                    }
                    Player.STATE_IDLE -> Unit
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                // Silent & rapid fallback to next extension (mp4 -> mkv -> m3u8) without showing technical text
                if (!playbackRequest.isLiveChannel && currentUrlIndex + 1 < candidateUrls.size) {
                    isBuffering = true
                    currentUrlIndex += 1
                } else {
                    isBuffering = false
                    allExtensionsFailed = true
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Poll playback position for seekbar
    LaunchedEffect(exoPlayer, isPlaying) {
        while (true) {
            if (exoPlayer.isPlaying) {
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
            }
            delay(500L)
        }
    }

    // Auto-hide controls after 4 seconds of playback
    LaunchedEffect(isControlsVisible, isPlaying, isBuffering) {
        if (isControlsVisible && isPlaying && !isBuffering && !allExtensionsFailed) {
            delay(4000L)
            isControlsVisible = false
        }
    }

    // Hide seek feedback badge after 750ms
    LaunchedEffect(seekFeedbackTrigger) {
        if (seekFeedbackText != null) {
            delay(750L)
            seekFeedbackText = null
        }
    }

    // Hide aspect ratio toast after 1.2s
    LaunchedEffect(showAspectToast, aspectMode) {
        if (showAspectToast) {
            delay(1200L)
            showAspectToast = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(playbackRequest.isLiveChannel) {
                detectTapGestures(
                    onTap = {
                        isControlsVisible = !isControlsVisible
                    },
                    onDoubleTap = { offset ->
                        if (!playbackRequest.isLiveChannel && !allExtensionsFailed) {
                            val screenWidth = size.width
                            // Smart Arabic RTL double-tap:
                            // Left side of screen seeks forward +10s or backward -10s intuitively
                            // In standard video timelines (left-to-right progress or RTL):
                            // Tapping right half -> +10s (تقديم), tapping left half -> -10s (تأخير)
                            val isRightSide = offset.x > screenWidth / 2f
                            if (isRightSide) {
                                val nextPos = (exoPlayer.currentPosition + 10_000L)
                                    .coerceAtMost(exoPlayer.duration.coerceAtLeast(10_000L))
                                exoPlayer.seekTo(nextPos)
                                positionMs = nextPos
                                seekFeedbackIsForward = true
                                seekFeedbackText = "+10 ثواني تقديم"
                                seekFeedbackTrigger++
                            } else {
                                val prevPos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(prevPos)
                                positionMs = prevPos
                                seekFeedbackIsForward = false
                                seekFeedbackText = "-10 ثواني تأخير"
                                seekFeedbackTrigger++
                            }
                        }
                    }
                )
            }
    ) {
        // Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = aspectMode.resizeMode
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.resizeMode = aspectMode.resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // ONLY a clean loading spinner while checking/switching mp4 -> mkv -> m3u8 (no technical text!)
        if (isBuffering && !allExtensionsFailed) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 4.dp,
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("player_loading_spinner")
                )
            }
        }

        // Error state if all extensions (mp4, mkv, m3u8) failed
        if (allExtensionsFailed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FoulBlackBg.copy(alpha = 0.92f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "خطأ في التشغيل",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "لا يعمل هناك مشكلة",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                allExtensionsFailed = false
                                currentUrlIndex = 0
                                isBuffering = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = FoulPurpleBright),
                            modifier = Modifier.testTag("player_retry_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("إعادة المحاولة")
                        }
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = FoulCardDark),
                            modifier = Modifier.testTag("player_error_back_button")
                        ) {
                            Text("رجوع")
                        }
                    }
                }
            }
        }

        // Double-tap Seek Feedback Indicator (+10s / -10s)
        AnimatedVisibility(
            visible = seekFeedbackText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(
                if (seekFeedbackIsForward) Alignment.CenterEnd else Alignment.CenterStart
            )
        ) {
            Surface(
                color = FoulPurpleDeep.copy(alpha = 0.85f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.padding(horizontal = 48.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (seekFeedbackIsForward) Icons.Default.Forward10 else Icons.Default.Replay10,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = seekFeedbackText.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Aspect Ratio Mode Toast Badge
        AnimatedVisibility(
            visible = showAspectToast,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp)
        ) {
            Surface(
                color = FoulPurpleDeep.copy(alpha = 0.9f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = aspectMode.labelAr,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }

        // Custom Controls Overlay (Top bar, Center Play/Pause + Seek, Bottom Timeline + Aspect Ratio)
        AnimatedVisibility(
            visible = isControlsVisible && !allExtensionsFailed,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.78f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.82f)
                            )
                        )
                    )
            ) {
                // Top Bar: Back Button + Organized Title & Subtitle + Aspect Ratio Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(FoulCardDark.copy(alpha = 0.75f))
                                .testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playbackRequest.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (playbackRequest.subtitle.isNotBlank()) {
                                Text(
                                    text = playbackRequest.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Aspect Ratio / Screen Fit Toggle Button
                    Surface(
                        color = FoulPurpleDeep.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                aspectMode = when (aspectMode) {
                                    PlayerAspectMode.FIT -> PlayerAspectMode.ZOOM
                                    PlayerAspectMode.ZOOM -> PlayerAspectMode.FILL
                                    PlayerAspectMode.FILL -> PlayerAspectMode.FIT
                                }
                                showAspectToast = true
                            }
                            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .testTag("player_aspect_ratio_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = when (aspectMode) {
                                    PlayerAspectMode.FIT -> Icons.Default.AspectRatio
                                    PlayerAspectMode.ZOOM -> Icons.Default.Fullscreen
                                    PlayerAspectMode.FILL -> Icons.Default.FullscreenExit
                                },
                                contentDescription = "تغيير أبعاد الشاشة",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = aspectMode.labelAr,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                }

                // Center Controls: Seek -10s, Play/Pause in exact Center, Seek +10s
                if (!isBuffering) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(36.dp)
                    ) {
                        if (!playbackRequest.isLiveChannel) {
                            IconButton(
                                onClick = {
                                    val nextPos = (exoPlayer.currentPosition + 10_000L)
                                        .coerceAtMost(exoPlayer.duration.coerceAtLeast(10_000L))
                                    exoPlayer.seekTo(nextPos)
                                    positionMs = nextPos
                                },
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(FoulCardDark.copy(alpha = 0.7f))
                                    .testTag("player_forward_10_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "تقديم 10 ثواني",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Center Play / Pause Button
                        IconButton(
                            onClick = {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(FoulPurpleBright)
                                .border(2.dp, Color.White, CircleShape)
                                .testTag("player_center_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        if (!playbackRequest.isLiveChannel) {
                            IconButton(
                                onClick = {
                                    val prevPos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(prevPos)
                                    positionMs = prevPos
                                },
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(FoulCardDark.copy(alpha = 0.7f))
                                    .testTag("player_replay_10_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "تأخير 10 ثواني",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Bar: Seekbar + Elapsed/Total Time (or Live Server Selector for Live Channels)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    if (playbackRequest.isLiveChannel && playbackRequest.servers.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "الجودة / السيرفر:",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                            playbackRequest.servers.forEachIndexed { idx, srv ->
                                val selected = idx == currentUrlIndex
                                Surface(
                                    color = if (selected) FoulPurpleBright else FoulCardDark.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            allExtensionsFailed = false
                                            currentUrlIndex = idx
                                        }
                                ) {
                                    Text(
                                        text = srv.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    } else if (durationMs > 0L) {
                        var sliderPos by remember(positionMs) {
                            mutableFloatStateOf(positionMs.toFloat())
                        }
                        Slider(
                            value = sliderPos.coerceIn(0f, durationMs.toFloat()),
                            onValueChange = { sliderPos = it },
                            onValueChangeFinished = {
                                exoPlayer.seekTo(sliderPos.toLong())
                                positionMs = sliderPos.toLong()
                            },
                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = FoulPurpleBright,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDurationMs(positionMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                            Text(
                                text = "الضغط المزدوج يميناً أو يساراً للتقديم والتأخير 10 ثواني",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                            Text(
                                text = formatDurationMs(durationMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
