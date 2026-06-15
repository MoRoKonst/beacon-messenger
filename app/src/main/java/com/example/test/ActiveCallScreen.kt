package com.bcon.messenger

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import com.bcon.messenger.ui.theme.LocalBeaconColors
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun ActiveCallScreen(
    peerId: String,            // для 1-to-1: userId; для группы: пустая строка
    isVideo: Boolean,
    isGroup: Boolean,
    onHangUp: () -> Unit
) {
    val c = LocalBeaconColors.current
    val context = LocalContext.current
    val s = LocalStrings.current

    // Таймер длительности
    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); seconds++ }
    }

    var isMuted       by remember { mutableStateOf(false) }
    var isCamOff      by remember { mutableStateOf(false) }
    var isSpeaker     by remember { mutableStateOf(CallManager.isVideoCall) }
    var isFrontCamera by remember { mutableStateOf(true) }
    // Speaker view: userId участника в главном окне (null = первый подключившийся)
    var mainPeerId    by remember { mutableStateOf<String?>(null) }
    // Локальный видеотрек как Compose-state: обновляется через callback когда трек готов.
    // Нельзя читать CallManager.localVideoTrack напрямую — это обычный var,
    // изменения которого не триггерят рекомпоновку.
    var localVideoTrack by remember { mutableStateOf(CallManager.localVideoTrack) }

    // Proximity sensor: гасить экран когда телефон у уха (только аудиозвонок)
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    val proximityLock = remember {
        powerManager.newWakeLock(
            android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "beacon:ProximityLock"
        )
    }
    DisposableEffect(isVideo) {
        if (!isVideo && !proximityLock.isHeld) proximityLock.acquire()
        onDispose {
            if (proximityLock.isHeld) proximityLock.release()
        }
    }

    // Удалённые видеопотоки (для группы — несколько)
    val remoteVideoTracks = remember { mutableStateMapOf<String, VideoTrack>() }

    // Участники группового звонка
    val groupPeers = remember { mutableStateListOf<String>().also {
        if (isGroup) it.addAll(CallManager.peerConnections.keys)
        else if (peerId.isNotEmpty()) it.add(peerId)
    }}

    // Колбэки регистрируются в DisposableEffect (синхронно, не в корутине),
    // чтобы избежать гонки: IncomingCallScreen.onDispose обнуляет onCallEnded,
    // и если call_end придёт до того, как LaunchedEffect выполнится (~1 фрейм),
    // onHangUp() не был бы вызван и экран завис бы навсегда.
    DisposableEffect(Unit) {
        CallManager.onCallEnded = { _ ->
            context.startService(Intent(context, CallService::class.java).apply {
                action = CallService.ACTION_END
            })
            onHangUp()
        }
        CallManager.onRemoteVideoTrack = { pid, track ->
            remoteVideoTracks[pid] = track
        }
        CallManager.onPeerJoined = { pid ->
            if (!groupPeers.contains(pid)) groupPeers.add(pid)
        }
        CallManager.onLocalVideoTrackReady = { track ->
            localVideoTrack = track
        }
        // Захватываем трек если он уже был установлен до регистрации callback-а
        if (localVideoTrack == null) localVideoTrack = CallManager.localVideoTrack

        onDispose {
            CallManager.onRemoteVideoTrack = null
            CallManager.onPeerJoined = null
            CallManager.onCallEnded = null
            CallManager.onLocalVideoTrackReady = null
            // Если экран закрылся без нажатия кнопки завершения (системная кнопка «Назад»,
            // или Activity уничтожена) — отправляем call_end собеседнику И освобождаем ресурсы.
            if (CallManager.callId.isNotEmpty()) {
                context.startService(Intent(context, CallService::class.java).apply {
                    action = CallService.ACTION_END
                })
                CallManager.hangUp()
            }
        }
    }

    // Запустить уведомление активного звонка (можно в корутине — не критично по времени)
    LaunchedEffect(Unit) {
        context.startService(Intent(context, CallService::class.java).apply {
            action = CallService.ACTION_ACTIVE
            val name = if (isGroup) s.activeGroupLabel else ChatStorage.getContactName(context, peerId).ifBlank { peerId }
            putExtra(CallService.EXTRA_PEER_NAME, name)
            putExtra(CallService.EXTRA_IS_VIDEO, isVideo)
        })
    }

    val peerName = remember(peerId) {
        if (isGroup) s.activeGroupCallLabel
        else ChatStorage.getContactName(context, peerId).ifBlank { peerId }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.callBg)
    ) {
        // ── Видео зоны ────────────────────────────────────────────────────────
        if (isVideo) {
            if (isGroup) {
                if (remoteVideoTracks.isEmpty()) {
                    // Ожидание подключения
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📞", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                s.activeWaitingPeers,
                                color = c.textPrimary.copy(alpha = 0.6f),
                                fontFamily = JetBrainsMono,
                                fontSize = 16.sp
                            )
                        }
                    }
                } else {
                    // Speaker view: один участник на большом экране, остальные — стрип миниатюр
                    val effectiveMainId =
                        if (mainPeerId != null && remoteVideoTracks.containsKey(mainPeerId))
                            mainPeerId!! else remoteVideoTracks.keys.first()
                    val mainTrack      = remoteVideoTracks[effectiveMainId]
                    val thumbnailPeers = remoteVideoTracks.keys.filter { it != effectiveMainId }

                    Column(Modifier.fillMaxSize()) {
                        // Главное видео — занимает всё доступное место
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color.Black)
                        ) {
                            if (mainTrack != null) {
                                RemoteVideoView(
                                    track    = mainTrack,
                                    label    = ChatStorage.getContactName(context, effectiveMainId).ifBlank { effectiveMainId },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Стрип миниатюр: появляется когда участников > 1
                        if (thumbnailPeers.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .background(Color.Black.copy(alpha = 0.7f)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(thumbnailPeers, key = { it }) { pid ->
                                    val thumbTrack = remoteVideoTracks[pid] ?: return@items
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .aspectRatio(3f / 4f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(2.dp, c.accent, RoundedCornerShape(8.dp))
                                            .clickable { mainPeerId = pid }
                                    ) {
                                        RemoteVideoView(
                                            track    = thumbTrack,
                                            label    = ChatStorage.getContactName(context, pid).ifBlank { pid },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }

                        // Резервируем высоту под панель управления
                        Spacer(Modifier.height(200.dp))
                    }
                }
            } else {
                // 1-to-1: remote video на весь экран
                val remoteTrack = remoteVideoTracks[peerId]
                if (remoteTrack != null) {
                    RemoteVideoView(
                        track = remoteTrack,
                        label = "",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Surface(shape = CircleShape, color = c.primaryBlue, modifier = Modifier.size(90.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        peerName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                        fontSize = 38.sp, color = Color.White,
                                        fontWeight = FontWeight.Bold, fontFamily = JetBrainsMono
                                    )
                                }
                            }
                            Text(peerName, color = Color.White, fontFamily = JetBrainsMono, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(s.activeConnecting, color = c.textPrimary.copy(alpha = 0.5f), fontFamily = JetBrainsMono, fontSize = 14.sp)
                        }
                    }
                }
            }

            // Своё видео — маленькое, в углу
            if (!isCamOff && localVideoTrack != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(width = 90.dp, height = 130.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.card)
                ) {
                    LocalVideoView(track = localVideoTrack, isFrontCamera = isFrontCamera, modifier = Modifier.fillMaxSize())
                }
            }
        } else {
            // Аудиозвонок — показываем аватар
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = c.primaryBlue,
                        modifier = Modifier.size(100.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                peerName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                fontSize = 44.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontFamily = JetBrainsMono
                            )
                        }
                    }
                    Text(
                        peerName,
                        color = Color.White,
                        fontFamily = JetBrainsMono,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // ── Нижняя панель ─────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, c.callBg.copy(alpha = 0.9f))
                    )
                )
                .padding(bottom = 40.dp, top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Имя и таймер
            Text(
                peerName,
                color = Color.White,
                fontFamily = JetBrainsMono,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                formatDuration(seconds),
                color = c.accent,
                fontFamily = JetBrainsMono,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(20.dp))

            // ── Ряд вспомогательных кнопок ────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallCallButton(
                    label  = if (isMuted) s.activeUnmute else s.activeMute,
                    icon   = if (isMuted) "🎙️" else "🔇",
                    active = isMuted,
                    onClick = { isMuted = CallManager.toggleMute() }
                )
                if (isVideo) {
                    SmallCallButton(
                        label  = if (isCamOff) s.activeCamOn else s.activeCamOff,
                        icon   = if (isCamOff) "📷" else "🚫",
                        active = isCamOff,
                        onClick = { isCamOff = CallManager.toggleCamera() }
                    )
                    SmallCallButton(
                        label   = "Камера",
                        iconRes = R.drawable.ic_flip_camera,
                        onClick = {
                            CallManager.switchCamera()
                            isFrontCamera = !isFrontCamera
                        }
                    )
                }
                SmallCallButton(
                    label       = if (isSpeaker) s.activeEarpiece else s.activeSpeaker,
                    icon        = if (isSpeaker) "🔊" else "👂",
                    active      = isSpeaker,
                    activeColor = c.primaryBlue,
                    onClick     = { isSpeaker = CallManager.toggleSpeaker(context) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Кнопка завершения — большая, по центру ────────────────────
            HangUpButton(label = s.activeHangUp) {
                context.startService(Intent(context, CallService::class.java).apply {
                    action = CallService.ACTION_END
                })
                CallManager.hangUp()
                onHangUp()
            }
        }
    }
}

// Небольшая круглая кнопка для вспомогательных действий (мут, камера, динамик)
@Composable
private fun SmallCallButton(
    label: String,
    icon: String = "",
    iconRes: Int? = null,
    active: Boolean = false,
    activeColor: Color = Color(0xFF444466),
    onClick: () -> Unit
) {
    val c = LocalBeaconColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (active) activeColor else c.cardAlt,
            modifier = Modifier.size(54.dp),
            onClick = onClick
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = label,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(icon, fontSize = 21.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            label,
            color = c.textPrimary.copy(alpha = 0.7f),
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Большая красная кнопка завершения звонка — всегда по центру
@Composable
private fun HangUpButton(label: String, onClick: () -> Unit) {
    val c = LocalBeaconColors.current
    val haptic = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = Color(0xFFCC2222),
            modifier = Modifier.size(68.dp),
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            label,
            color = c.textPrimary.copy(alpha = 0.7f),
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun RemoteVideoView(track: VideoTrack, label: String, modifier: Modifier) {
    val c = LocalBeaconColors.current
    Box(modifier = modifier.background(c.callBg)) {
        AndroidView(
            factory = { ctx ->
                // TextureViewRenderer вместо SurfaceViewRenderer:
                // SurfaceView имеет асинхронный Surface lifecycle — Surface создаётся
                // отдельно через SurfaceHolder.Callback.surfaceCreated(). Если WebRTC
                // rendering thread пытается создать EGL surface до готовности Surface,
                // makeCurrent() бросает RuntimeException на native-потоке → JNI fatal crash.
                // TextureView работает через SurfaceTexture, который доступен сразу при
                // создании View — нет async lifecycle, нет гонки с EGL.
                SurfaceViewRenderer(ctx).apply {
                    try {
                        val egl = CallManager.getEglBase()?.eglBaseContext ?: return@apply
                        init(egl, null)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                        track.addSink(this)
                    } catch (e: Exception) {
                        android.util.Log.e("VideoView", "RemoteVideoView init error: ${e.message}")
                    }
                }
            },
            onRelease = { renderer ->
                try { track.removeSink(renderer) } catch (_: Exception) {}
                try { renderer.release() } catch (_: Exception) {}
            },
            modifier = Modifier.fillMaxSize()
        )
        if (label.isNotBlank()) {
            Text(
                label,
                color = Color.White,
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color(0x88000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun LocalVideoView(track: VideoTrack?, isFrontCamera: Boolean, modifier: Modifier) {
    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                try {
                    val egl = CallManager.getEglBase()?.eglBaseContext ?: return@apply
                    init(egl, null)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    setMirror(true)
                    track?.addSink(this)
                } catch (e: Exception) {
                    android.util.Log.e("VideoView", "LocalVideoView init error: ${e.message}")
                }
            }
        },
        update = { renderer ->
            renderer.setMirror(isFrontCamera)
        },
        onRelease = { renderer ->
            try { track?.removeSink(renderer) } catch (_: Exception) {}
            try { renderer.release() } catch (_: Exception) {}
        },
        modifier = modifier
    )
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}
