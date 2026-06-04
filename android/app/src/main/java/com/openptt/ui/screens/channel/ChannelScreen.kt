package com.openptt.ui.screens.channel

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openptt.ui.theme.PttDenied
import com.openptt.ui.theme.PttIdle
import com.openptt.ui.theme.PttTransmitting

/**
 * PTT button states
 */
enum class PttState {
    IDLE,
    REQUESTING,
    TRANSMITTING,
    DENIED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelId: String,
    onBack: () -> Unit,
    viewModel: ChannelViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(channelId) {
        viewModel.initChannel(channelId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            uiState.channelName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${uiState.onlineUsers} online",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Connection status indicator
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = when (uiState.connectionStatus) {
                            "connected" -> PttIdle
                            "connecting" -> Color.Yellow
                            else -> PttDenied
                        }
                    ) {}
                    Spacer(modifier = Modifier.width(16.dp))
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.3f))

            // Active speaker display
            if (uiState.activeSpeaker != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.activeSpeaker!!,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.3f))

            // PTT Status text
            Text(
                text = when (uiState.pttState) {
                    PttState.IDLE -> "Hold to Talk"
                    PttState.REQUESTING -> "Requesting…"
                    PttState.TRANSMITTING -> "Transmitting"
                    PttState.DENIED -> "Channel Busy"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // === THE BIG PTT BUTTON ===
            val toneGenerator = remember {
                android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, android.media.ToneGenerator.MAX_VOLUME)
            }
            DisposableEffect(Unit) {
                onDispose {
                    toneGenerator.release()
                }
            }

            PttButton(
                state = uiState.pttState,
                onPttDown = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 50)
                    viewModel.pttDown()
                },
                onPttUp = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.pttUp()
                }
            )

            Spacer(modifier = Modifier.weight(0.4f))
        }
    }
}

@Composable
fun PttButton(
    state: PttState,
    onPttDown: () -> Unit,
    onPttUp: () -> Unit
) {
    // Animated color based on state
    val buttonColor by animateColorAsState(
        targetValue = when (state) {
            PttState.IDLE -> PttIdle
            PttState.REQUESTING -> PttIdle.copy(alpha = 0.7f)
            PttState.TRANSMITTING -> PttTransmitting
            PttState.DENIED -> PttDenied
        },
        animationSpec = tween(durationMillis = 200),
        label = "pttColor"
    )

    // Pulsing animation for idle state
    val infiniteTransition = rememberInfiniteTransition(label = "pttPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Scale animation when pressing
    val scale by animateFloatAsState(
        targetValue = when (state) {
            PttState.TRANSMITTING -> 1.15f
            PttState.DENIED -> 0.95f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.6f),
        label = "scale"
    )

    val finalScale = if (state == PttState.IDLE) pulseScale else scale

    Box(
        modifier = Modifier
            .size(200.dp)
            .scale(finalScale)
            .shadow(
                elevation = if (state == PttState.TRANSMITTING) 24.dp else 8.dp,
                shape = CircleShape,
                ambientColor = buttonColor.copy(alpha = 0.3f),
                spotColor = buttonColor.copy(alpha = 0.5f)
            )
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        buttonColor,
                        buttonColor.copy(alpha = 0.8f)
                    )
                )
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPttDown()
                        tryAwaitRelease()
                        onPttUp()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (state) {
                PttState.TRANSMITTING -> Icons.Default.Mic
                PttState.DENIED -> Icons.Default.MicOff
                else -> Icons.Default.Mic
            },
            contentDescription = "Push to Talk",
            tint = Color.White,
            modifier = Modifier.size(72.dp)
        )
    }
}
