/*
 * Copyright (C) 2026 MovStore
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.CallManager
import com.example.R
import com.example.safeState
import com.example.model.Contact
import com.example.ui.theme.LocalAmoledMode
import com.example.util.CallAudioHelper
import com.example.util.CallAudioRecorder
import com.example.util.CallGestureSensorManager
import com.example.util.RecordingFeedbackHelper
import com.example.util.RichHapticEngine

@Composable
fun ActiveCallScreen(
    contactName: String,
    contactNumber: String,
    preferredSim: String,
    quickResponses: List<String>,
    onHangUp: () -> Unit,
    onQuickDecline: (String) -> Unit,
    isIncoming: Boolean = false,
    contacts: List<Contact> = emptyList(),
    onAnswer: () -> Unit = {},
    callState: Int = Call.STATE_DISCONNECTED,
    recordingEnabled: Boolean = false,
    autoTuneVolume: Boolean = true,
    recordingChimeEnabled: Boolean = false,
    alwaysRecordEnabled: Boolean = false,
    onSaveRecording: (Long, String) -> Unit = { _, _ -> },
    callNotesEnabled: Boolean = true,
    onSaveNote: (String) -> Unit = {},
    onMinimize: (() -> Unit)? = null,
    avatarShapeType: String = "circular",
    isPocketProtectionEnabled: Boolean = false,
    isFake: Boolean = false,
    fakeState: String = "RINGING",
    onFakeAnswer: () -> Unit = {},
    onFakeHangUp: () -> Unit = {}
) {
    BackHandler(enabled = true) {
        onMinimize?.invoke()
    }

    val context = LocalContext.current
    val activeStartTimestamp by CallManager.activeStartTimestamp.collectAsStateWithLifecycle()

    val audioState by CallManager.audioState.collectAsStateWithLifecycle()
    val waitingCall by CallManager.waitingCall.collectAsStateWithLifecycle()
    val allCalls by CallManager.calls.collectAsStateWithLifecycle()
    val currentCall by CallManager.currentCall.collectAsStateWithLifecycle()
    val recorderIsActive by CallAudioRecorder.isRecording.collectAsStateWithLifecycle()

    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var isBluetoothOn by remember { mutableStateOf(false) }
    var isOnHold by remember { mutableStateOf(false) }
    var isAddCallDialogOpen by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingStartTime by remember { mutableLongStateOf(0L) }
    var fakeActiveStartTimestamp by remember { mutableLongStateOf(0L) }

    // FIXED: Sync mute, bluetooth, and speaker routes with system audio updates (e.g. notifications / watch)
    LaunchedEffect(audioState) {
        audioState?.let {
            isBluetoothOn = (it.route == CallAudioState.ROUTE_BLUETOOTH)
            isSpeakerOn = (it.route == CallAudioState.ROUTE_SPEAKER)
            isMuted = it.isMuted
        }
    }

    // FIXED: Sync hold status with real carrier network state
    val currentCallState = if (isFake) {
        if (fakeState == "ACTIVE") Call.STATE_ACTIVE else Call.STATE_RINGING
    } else {
        callState
    }

    LaunchedEffect(currentCallState) {
        isOnHold = (currentCallState == Call.STATE_HOLDING)
    }

    LaunchedEffect(recorderIsActive) {
        if (recorderIsActive) {
            isRecording = true
            if (recordingStartTime == 0L) recordingStartTime = System.currentTimeMillis()
        } else {
            isRecording = false
            recordingStartTime = 0L
        }
    }

    val currentIsRecording by rememberUpdatedState(isRecording)
    val currentOnSaveRecording by rememberUpdatedState(onSaveRecording)

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Microphone permission granted. Tap Record to start.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Microphone permission required for call recording", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (currentIsRecording || CallAudioRecorder.isRecording.value) {
                val result = CallAudioRecorder.stopRecording()
                CallAudioHelper.restoreAudioState(context, CallManager.inCallService)
                val file = result.file
                if (file != null && file.exists() && file.length() > 0L) {
                    currentOnSaveRecording(result.durationSeconds.coerceAtLeast(1L), file.absolutePath)
                }
            } else {
                CallAudioHelper.restoreAudioState(context, CallManager.inCallService)
            }
        }
    }

    LaunchedEffect(isFake, fakeState) {
        if (isFake && fakeState == "ACTIVE") {
            fakeActiveStartTimestamp = System.currentTimeMillis()
            if (alwaysRecordEnabled && !CallAudioRecorder.isRecording.value) {
                RecordingFeedbackHelper.triggerRecordingStartFeedback(context, recordingChimeEnabled)
                CallAudioRecorder.startRecording(context, contactNumber)
            }
        }
    }

    val isAmoled = LocalAmoledMode.current
    val surfaceColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    val heldCall = remember(allCalls) { allCalls.firstOrNull { it.safeState == Call.STATE_HOLDING } }

    var isQuickDeclineMenuOpen by remember { mutableStateOf(false) }
    var isInCallDialpadOpen by remember { mutableStateOf(false) }

    var fakeParticipants by remember(contactName, contactNumber) {
        mutableStateOf(listOf(Pair(contactName, contactNumber)))
    }

    val participants = remember(isFake, currentCall, allCalls, contactName, contactNumber, contacts, fakeParticipants) {
        if (isFake) {
            fakeParticipants
        } else {
            val activeList = allCalls.filter { it.safeState != Call.STATE_DISCONNECTED }
            val conferenceCall = activeList.find {
                it.children.isNotEmpty() || it.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true
            }
            if (conferenceCall != null && conferenceCall.children.isNotEmpty()) {
                conferenceCall.children.map { child ->
                    val num = child.details?.handle?.schemeSpecificPart ?: ""
                    val name = contacts.find { it.number == num }?.name ?: num
                    Pair(name, num)
                }
            } else {
                val num = currentCall?.details?.handle?.schemeSpecificPart ?: contactNumber
                val name = contacts.find { it.number == num }?.name ?: contactName.ifEmpty { num }
                listOf(Pair(name, num))
            }
        }
    }

    val isConferenceActiveFlow by CallManager.isConferenceActive.collectAsStateWithLifecycle()
    val isConference = remember(isFake, fakeParticipants, currentCall, allCalls, isConferenceActiveFlow) {
        if (isFake) {
            fakeParticipants.size > 1
        } else {
            isConferenceActiveFlow || CallManager.isConference(currentCall) || allCalls.any {
                it.safeState != Call.STATE_DISCONNECTED && CallManager.isConference(it)
            }
        }
    }

    var isNear by remember { mutableStateOf(false) }
    val powerManager = remember(context) { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val isProximitySupported = remember(powerManager) {
        powerManager?.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) == true
    }

    val isHeadsetOn = remember(audioState) {
        audioState?.route == CallAudioState.ROUTE_WIRED_HEADSET
    }
    val isEarpieceActive = (audioState?.route == CallAudioState.ROUTE_EARPIECE) ||
            (audioState == null && !isSpeakerOn && !isBluetoothOn && !isHeadsetOn)

    val shouldActivateProximity = isEarpieceActive && !isSpeakerOn && !isBluetoothOn && !isHeadsetOn &&
            (currentCallState in listOf(Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_RINGING))

    DisposableEffect(shouldActivateProximity, powerManager) {
        var wakeLock: PowerManager.WakeLock? = null
        if (shouldActivateProximity && isProximitySupported && powerManager != null) {
            try {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "NovaDial:InCallProximityWakeLock"
                )
                wakeLock.acquire(12 * 60 * 60 * 1000L)
            } catch (_: Exception) {}
        }
        onDispose {
            try {
                if (wakeLock?.isHeld == true) wakeLock.release()
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        val gestureManager = CallGestureSensorManager(
            context = context,
            onProximityChanged = { near -> isNear = near },
            onFlipFaceDown = {
                if (callState == Call.STATE_RINGING) {
                    CallManager.silenceRinger(context)
                }
            }
        )
        gestureManager.startListening()
        onDispose { gestureManager.stopListening() }
    }

    // ---------------------------------------------------------------------------------------
    //  Modern backdrop: gradient tint that shifts with call state + soft radial glow + pulse
    // ---------------------------------------------------------------------------------------
    val callAccent by animateColorAsState(
        targetValue = when {
            isOnHold || currentCallState == Call.STATE_HOLDING -> Color(0xFFF59E0B)
            currentCallState == Call.STATE_RINGING -> Color(0xFF7C3AED)
            currentCallState == Call.STATE_DIALING ||
                    currentCallState == Call.STATE_CONNECTING -> Color(0xFF2563EB)
            currentCallState == Call.STATE_ACTIVE -> Color(0xFF22C55E)
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "callAccent"
    )

    val backdrop by remember(isAmoled) {
        derivedStateOf {
            if (isAmoled) {
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0A0A), Color(0xFF000000))
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        surfaceColor,
                        surfaceColor.copy(alpha = 0.96f)
                    )
                )
            }
        }
    }

    // The screen "breathes" only while the call is ringing or connecting. During a normal conversation the glow stays
    // still, so there is no animation running every frame (smoother, and much kinder to the battery).
    val isPulsing = currentCallState == Call.STATE_RINGING ||
        currentCallState == Call.STATE_DIALING ||
        currentCallState == Call.STATE_CONNECTING
    val pulseState: State<Float> = if (isPulsing) {
        val infinite = rememberInfiniteTransition(label = "ambientPulse")
        infinite.animateFloat(
            initialValue = 0.55f,
            targetValue = if (currentCallState == Call.STATE_RINGING) 1f else 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (currentCallState == Call.STATE_RINGING) 1200 else 2200,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseValue"
        )
    } else {
        remember { mutableStateOf(0.7f) }
    }
    val pulse by pulseState

    // Subtle entry animation for the whole shell.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val shellAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "shellAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent
        ) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(backdrop)
            ) {
                // Ambient radial glow that sits behind the content, colored by call state.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.55f)
                        .align(Alignment.TopCenter)
                        .graphicsLayer { alpha = 0.34f * pulse }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    callAccent.copy(alpha = 0.55f),
                                    callAccent.copy(alpha = 0.18f),
                                    Color.Transparent
                                ),
                                radius = 900f
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = shellAlpha }
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    InCallHeader(
                        isOnHold = isOnHold,
                        callState = currentCallState,
                        participants = participants,
                        preferredSim = preferredSim,
                        contactName = contactName,
                        contactNumber = contactNumber,
                        heldCall = heldCall,
                        contacts = contacts,
                        onMerge = {
                            if (isFake) {
                                Toast.makeText(context, "📞 Merged call (Simulation)", Toast.LENGTH_SHORT).show()
                            } else {
                                CallManager.mergeCalls()
                            }
                        },
                        isConference = isConference,
                        isRecording = isRecording,
                        activeStartTimestamp = activeStartTimestamp,
                        isFake = isFake,
                        fakeState = fakeState,
                        fakeActiveStartTimestamp = fakeActiveStartTimestamp
                    )

                    waitingCall?.let { call ->
                        InCallWaitingCallDialog(
                            waitingCall = call,
                            contacts = contacts,
                            avatarShapeType = avatarShapeType
                        )
                    }

                    if (isQuickDeclineMenuOpen) {
                        InCallQuickDeclineSheet(
                            contactNumber = contactNumber,
                            quickResponses = quickResponses,
                            onClose = { isQuickDeclineMenuOpen = false },
                            onQuickDecline = onQuickDecline
                        )
                    } else if (isInCallDialpadOpen) {
                        InCallKeypad(
                            onClose = { isInCallDialpadOpen = false },
                            avatarShapeType = avatarShapeType
                        )
                    } else {
                        InCallAvatarDisplay(
                            participants = participants,
                            contactName = contactName,
                            contactNumber = contactNumber,
                            contacts = contacts,
                            avatarShapeType = avatarShapeType,
                            isConference = isConference
                        )
                    }

                    if (isAddCallDialogOpen) {
                        InCallAddCallDialog(
                            contacts = contacts,
                            onDismiss = { isAddCallDialogOpen = false },
                            onAddCall = { finalName, number ->
                                if (isFake) {
                                    fakeParticipants = fakeParticipants + Pair(finalName, number)
                                } else {
                                    CallManager.placeCall(context, number)
                                }
                            },
                            avatarShapeType = avatarShapeType
                        )
                    }

                    InCallControlGrid(
                        isInCallDialpadOpen = isInCallDialpadOpen,
                        onToggleDialpad = {
                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                            isInCallDialpadOpen = !isInCallDialpadOpen
                        },
                        isMuted = isMuted,
                        onToggleMute = {
                            val next = !isMuted
                            RichHapticEngine.performHaptic(context, if (next) RichHapticEngine.HapticStyle.WARNING else RichHapticEngine.HapticStyle.CLICK)
                            isMuted = next
                            CallManager.setMuted(next)
                        },
                        isSpeakerOn = isSpeakerOn,
                        onToggleSpeaker = {
                            val next = !isSpeakerOn
                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.CLICK)
                            isSpeakerOn = next
                            CallManager.setSpeaker(next)
                        },
                        isOnHold = isOnHold,
                        onToggleHold = {
                            val next = !isOnHold
                            RichHapticEngine.performHaptic(context, if (next) RichHapticEngine.HapticStyle.WARNING else RichHapticEngine.HapticStyle.CLICK)
                            isOnHold = next
                            CallManager.setHold(next)
                        },
                        isBluetoothOn = isBluetoothOn,
                        onToggleBluetooth = {
                            val next = !isBluetoothOn
                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.CLICK)
                            isBluetoothOn = next
                            CallManager.setBluetooth(next)
                        },
                        isAddCallDialogOpen = isAddCallDialogOpen,
                        onOpenAddCallDialog = {
                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.CLICK)
                            isAddCallDialogOpen = true
                        },
                        recordingEnabled = recordingEnabled,
                        isRecording = isRecording,
                        onToggleRecording = {
                            if (isRecording || CallAudioRecorder.isRecording.value) {
                                RecordingFeedbackHelper.triggerRecordingStopFeedback(context, recordingChimeEnabled)
                                val result = CallAudioRecorder.stopRecording()
                                if (autoTuneVolume) {
                                    CallAudioHelper.restoreAudioState(context, CallManager.inCallService)
                                }
                                val file = result.file
                                if (file != null && file.exists() && file.length() > 0L) {
                                    onSaveRecording(result.durationSeconds.coerceAtLeast(1L), file.absolutePath)
                                    Toast.makeText(context, context.getString(R.string.recording_saved), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Call recording failed or file empty", Toast.LENGTH_SHORT).show()
                                }
                                isRecording = false
                            } else {
                                val hasAudioPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (!hasAudioPermission) {
                                    Toast.makeText(context, "Microphone permission required for call recording", Toast.LENGTH_SHORT).show()
                                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@InCallControlGrid
                                }

                                RecordingFeedbackHelper.triggerRecordingStartFeedback(context, recordingChimeEnabled)
                                val currentRoute = audioState?.route ?: CallAudioState.ROUTE_EARPIECE
                                val isHeadset = currentRoute == CallAudioState.ROUTE_BLUETOOTH || currentRoute == CallAudioState.ROUTE_WIRED_HEADSET

                                if (autoTuneVolume && !isHeadset) {
                                    CallAudioHelper.prepareSpeakerForRecording(
                                        context = context,
                                        inCallService = CallManager.inCallService,
                                        currentAudioState = audioState
                                    )
                                }
                                val started = CallAudioRecorder.startRecording(context, contactNumber)
                                if (started) {
                                    recordingStartTime = System.currentTimeMillis()
                                    isRecording = true
                                    if (isHeadset) {
                                        Toast.makeText(context, context.getString(R.string.headset_recording_warning), Toast.LENGTH_LONG).show()
                                    } else if (autoTuneVolume) {
                                        Toast.makeText(context, "⏺️ Recording started. Speaker tuned for balanced audio.", Toast.LENGTH_SHORT).show()
                                    } else if (!isSpeakerOn) {
                                        Toast.makeText(context, "⏺️ Recording started. Tip: Turn ON Speakerphone.", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.recording_started), Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    if (autoTuneVolume && !isHeadset) {
                                        CallAudioHelper.restoreAudioState(context, CallManager.inCallService)
                                    }
                                    Toast.makeText(context, "Cannot start recording. Grant Microphone permission.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        isQuickNotesOpen = isQuickDeclineMenuOpen,
                        onOpenQuickNotes = {
                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.CLICK)
                            isQuickDeclineMenuOpen = !isQuickDeclineMenuOpen
                        },
                        avatarShapeType = avatarShapeType
                    )

                    InCallBottomBar(
                        isIncoming = if (isFake) (fakeState == "RINGING") else isIncoming,
                        onAnswer = if (isFake) onFakeAnswer else onAnswer,
                        onHangUp = if (isFake) onFakeHangUp else onHangUp,
                        onToggleQuickDeclineMenu = { isQuickDeclineMenuOpen = !isQuickDeclineMenuOpen },
                        avatarShapeType = avatarShapeType
                    )
                }

                // Modern floating pill minimize handle (replaces plain top-start icon)
                if (onMinimize != null) {
                    ModernMinimizeHandle(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 6.dp),
                        onClick = {
                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.CLICK)
                            onMinimize()
                        }
                    )
                }
            }
        }

        if (isNear && shouldActivateProximity) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isPocketProtectionEnabled) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = stringResource(R.string.pocket_lock_active),
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.pocket_lock_active),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.pocket_lock_desc),
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/*  Modern floating minimize handle                                                            */
/* ------------------------------------------------------------------------------------------ */

/**
 * A slim rounded pill at the top of the call screen containing a chevron-down.
 * Doubles as a drag/tap target that mirrors the modern "swipe to minimize" affordance
 * found in stock dialers, while remaining a simple click target so behaviour is unchanged.
 */
@Composable
private fun ModernMinimizeHandle(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "minimizeScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
            .padding(horizontal = 22.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Minimize Call",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}