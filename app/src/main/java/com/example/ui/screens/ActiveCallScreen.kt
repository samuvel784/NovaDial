package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telecom.ActiveCallSession
import com.example.telecom.CallSessionState
import com.example.ui.MainViewModel
import com.example.ui.theme.ThemeMode
import com.example.util.PhoneNumberUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveCallScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val session by viewModel.activeCallSession.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    var showInCallKeypad by remember { mutableStateOf(false) }
    var dtmfToneDigits by remember { mutableStateOf("") }

    if (session.state == CallSessionState.IDLE) return

    val context = LocalContext.current

    // Background Gradient based on Theme
    val backgroundBrush = when (themeMode) {
        ThemeMode.DAY -> Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFFFFF),
                Color(0xFFF1F5F9),
                Color(0xFFE2E8F0)
            )
        )
        ThemeMode.DARK -> Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1E293B),
                Color(0xFF0F172A),
                Color(0xFF020617)
            )
        )
        ThemeMode.DEFAULT -> Brush.verticalGradient(
            colors = listOf(
                Color(0xFF6B21A8),
                Color(0xFF3B82F6),
                Color(0xFF06B6D4)
            )
        )
    }

    // Text & Control Colors based on Light vs Dark/Default Theme
    val isLight = themeMode == ThemeMode.DAY
    val textPrimary = if (isLight) Color(0xFF0F172A) else Color.White
    val textSecondary = if (isLight) Color(0xFF475569) else Color.White.copy(alpha = 0.75f)
    val buttonOutlineColor = if (isLight) Color(0xFF0F172A) else Color.White.copy(alpha = 0.85f)
    val avatarBgColor = if (isLight) Color(0xFF2563EB) else Color.White
    val avatarIconColor = if (isLight) Color.White else Color(0xFF1E293B)

    // Pulse animation for avatar
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatarPulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .testTag("active_call_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. TOP HEADER: SIM Card Badge & Spam Warning (NO 'X' BUTTON)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (isLight) Color(0xFF0F172A).copy(alpha = 0.08f) else Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SimCard,
                            contentDescription = "SIM Slot",
                            tint = if (isLight) Color(0xFF0284C7) else Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SIM ${session.simSlot}",
                            color = textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (session.isSpam) {
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "⚠️ SPAM CALLER",
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 2. CENTER SECTION: INCOMING vs ACTIVE CALL LAYOUT
            if (session.state == CallSessionState.RINGING || session.isIncoming) {
                // --- INCOMING CALL LAYOUT ---
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    // Contact Name & Number at Top
                    Text(
                        text = PhoneNumberUtils.formatForDisplay(session.number),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        textAlign = TextAlign.Center
                    )

                    if (session.displayName.isNotBlank() && session.displayName != session.number) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = session.displayName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    // Large Circular Avatar
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(avatarBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Caller Avatar",
                            tint = avatarIconColor,
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }

                // INCOMING CALL BUTTONS (ACCEPT & DECLINE)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Accept Button (Green)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(
                                onClick = { viewModel.repository.callManager.answerCall() },
                                modifier = Modifier
                                    .size(72.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .testTag("answer_incoming_call_button"),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF22C55E),
                                    contentColor = Color.White
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Accept",
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Accept", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Quick Message Button
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    val smsIntent = android.content.Intent(
                                        android.content.Intent.ACTION_SENDTO,
                                        android.net.Uri.parse("smsto:${android.net.Uri.encode(session.number)}")
                                    ).apply {
                                        putExtra("sms_body", "Can't talk right now. What's up?")
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    try { context.startActivity(smsIntent) } catch (e: Exception) {}
                                    viewModel.repository.callManager.rejectCall()
                                },
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(buttonOutlineColor.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Message", tint = textPrimary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Message", color = textSecondary, fontSize = 11.sp)
                        }

                        // Decline Button (Red)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(
                                onClick = { viewModel.repository.callManager.rejectCall() },
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .testTag("reject_incoming_call_button"),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEF4444),
                                    contentColor = Color.White
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "Decline",
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Decline", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                // --- ACTIVE CALL LAYOUT (MATCHING REFERENCE IMAGES) ---
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Top: Call Duration / Timer (09:15)
                    val stateText = when (session.state) {
                        CallSessionState.DIALING -> "Dialing..."
                        CallSessionState.RINGING -> "Ringing..."
                        CallSessionState.CONNECTED -> formatDuration(session.durationSeconds)
                        CallSessionState.HELD -> "Call on Hold"
                        CallSessionState.DISCONNECTED -> "Call Ended"
                        CallSessionState.IDLE -> ""
                    }

                    Text(
                        text = stateText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Center: Large Circular Avatar
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(avatarBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Caller Avatar",
                            tint = avatarIconColor,
                            modifier = Modifier.size(68.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Contact Name / Number directly under Avatar
                    Text(
                        text = session.displayName.ifBlank { PhoneNumberUtils.formatForDisplay(session.number) },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (session.displayName.isNotBlank() && session.displayName != session.number) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = PhoneNumberUtils.formatForDisplay(session.number),
                            fontSize = 15.sp,
                            color = textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // In-Call DTMF Dialpad overlay if opened
                if (showInCallKeypad) {
                    Surface(
                        color = if (isLight) Color(0xFFF1F5F9) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (dtmfToneDigits.isEmpty()) "In-Call Keypad" else dtmfToneDigits,
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#').chunked(4).forEach { row ->
                                    Column {
                                        row.forEach { digit ->
                                            IconButton(onClick = {
                                                dtmfToneDigits += digit
                                                viewModel.repository.callManager.playDtmf(digit)
                                            }) {
                                                Text(digit.toString(), color = textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                            TextButton(onClick = { showInCallKeypad = false }) {
                                Text("Hide Keypad", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // 6-BUTTON CONTROL GRID (2 rows x 3 columns matching reference images)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        // ROW 1: speaker, mute, record
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            OutlineInCallButton(
                                icon = if (session.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                label = "speaker",
                                isActive = session.isSpeakerOn,
                                outlineColor = buttonOutlineColor,
                                textColor = textPrimary,
                                onClick = { viewModel.repository.callManager.toggleSpeaker() }
                            )

                            OutlineInCallButton(
                                icon = if (session.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                label = "mute",
                                isActive = session.isMuted,
                                outlineColor = buttonOutlineColor,
                                textColor = textPrimary,
                                onClick = { viewModel.repository.callManager.toggleMute() }
                            )

                            // Real audio route button: earpiece / speaker / wired headset / Bluetooth, whatever the phone offers
                            OutlineInCallButton(
                                icon = Icons.Default.Bluetooth,
                                label = "audio",
                                isActive = session.currentAudioRoute == android.telecom.CallAudioState.ROUTE_BLUETOOTH ||
                                    session.currentAudioRoute == android.telecom.CallAudioState.ROUTE_WIRED_HEADSET,
                                outlineColor = buttonOutlineColor,
                                textColor = textPrimary,
                                onClick = { viewModel.repository.callManager.cycleAudioRoute() }
                            )
                        }

                        // ROW 2: video, keypad, hold
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {

                            OutlineInCallButton(
                                icon = Icons.Default.Dialpad,
                                label = "keypad",
                                isActive = showInCallKeypad,
                                outlineColor = buttonOutlineColor,
                                textColor = textPrimary,
                                onClick = { showInCallKeypad = !showInCallKeypad }
                            )

                            OutlineInCallButton(
                                icon = if (session.isHeld) Icons.Default.PlayArrow else Icons.Default.Pause,
                                label = if (session.isHeld) "unhold" else "hold",
                                isActive = session.isHeld,
                                outlineColor = buttonOutlineColor,
                                textColor = textPrimary,
                                onClick = { viewModel.repository.callManager.toggleHold() }
                            )
                        }
                    }
                }

                // BOTTOM: BIG RED DECLINE / END CALL BUTTON (CENTERED)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Button(
                        onClick = { viewModel.endActiveCall() },
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .testTag("end_active_call_button"),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444),
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Decline",
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Decline",
                        color = textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Clean Circular Outlined Button for Calling Control Grid matching reference designs
 */
@Composable
private fun OutlineInCallButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    outlineColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (isActive) outlineColor else Color.Transparent)
                .border(width = 1.5.dp, color = outlineColor, shape = CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color(0xFF0F172A) else outlineColor,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", mins, secs)
}