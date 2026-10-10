package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SimAccount
import com.example.ui.theme.AppTheme
import com.example.util.PhoneNumberUtils

data class KeypadKey(
    val digit: Char,
    val letters: String,
    val subText: String = ""
)

val SMART_KEYPAD_KEYS = listOf(
    KeypadKey('1', "", "Voicemail"),
    KeypadKey('2', "ABC"),
    KeypadKey('3', "DEF"),
    KeypadKey('4', "GHI"),
    KeypadKey('5', "JKL"),
    KeypadKey('6', "MNO"),
    KeypadKey('7', "PQRS"),
    KeypadKey('8', "TUV"),
    KeypadKey('9', "WXYZ"),
    KeypadKey('*', ""),
    KeypadKey('0', "+", "+"),
    KeypadKey('#', "")
)

/**
 * Reusable Smart Keypad Component with T9 markings, long-press handling,
 * DTMF feedback, SIM selector, and full theme responsiveness.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmartKeypad(
    dialedDigits: String,
    onDigitPress: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClearAll: () -> Unit,
    onCallClick: (String, Int) -> Unit,
    simAccounts: List<SimAccount> = emptyList(),
    selectedSimSlot: Int = 0,
    onSelectSimSlot: (Int) -> Unit = {},
    onSetDigits: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    var showNumberMenu by remember { mutableStateOf(false) }
    var showPasteMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .testTag("smart_keypad_component"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Digits Display with constant centered area and long-press options
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppTheme.colors.elevated.copy(alpha = 0.4f))
                .combinedClickable(
                    onClick = {
                        if (dialedDigits.isEmpty()) {
                            showPasteMenu = true
                        } else {
                            showNumberMenu = true
                        }
                    },
                    onLongClick = {
                        if (dialedDigits.isNotEmpty()) {
                            showNumberMenu = true
                        } else {
                            showPasteMenu = true
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (dialedDigits.isEmpty()) {
                Text(
                    text = "Tap or hold to paste number",
                    fontSize = 13.sp,
                    color = AppTheme.colors.textMuted.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = PhoneNumberUtils.formatForDisplay(dialedDigits),
                    fontSize = if (dialedDigits.length > 14) 20.sp else if (dialedDigits.length > 10) 24.sp else 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp)
                        .testTag("keypad_dialed_number")
                )
            }

            // Dropdown Menu for entered numbers (Copy, Paste, Cut, Clear)
            DropdownMenu(
                expanded = showNumberMenu,
                onDismissRequest = { showNumberMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("📋 Copy Number") },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(dialedDigits))
                        showNumberMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("📋 Paste Number") },
                    onClick = {
                        val clipText = clipboardManager.getText()?.text
                        if (!clipText.isNullOrBlank()) {
                            val digits = clipText.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                            if (digits.isNotEmpty()) {
                                onSetDigits?.invoke(dialedDigits + digits)
                            }
                        }
                        showNumberMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("✂️ Cut Number") },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(dialedDigits))
                        onClearAll()
                        showNumberMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("🧹 Clear") },
                    onClick = {
                        onClearAll()
                        showNumberMenu = false
                    }
                )
            }

            // Dropdown Menu for empty keypad display or empty background
            DropdownMenu(
                expanded = showPasteMenu,
                onDismissRequest = { showPasteMenu = false }
            ) {
                val clipText = clipboardManager.getText()?.text
                val digits = clipText?.filter { it.isDigit() || it == '+' || it == '*' || it == '#' } ?: ""

                DropdownMenuItem(
                    text = { Text(if (digits.isNotEmpty()) "📋 Paste '$digits'" else "📋 Paste") },
                    onClick = {
                        if (digits.isNotEmpty()) {
                            onSetDigits?.invoke(digits)
                        }
                        showPasteMenu = false
                    }
                )
                if (digits.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text("📞 Paste & Call") },
                        onClick = {
                            onSetDigits?.invoke(digits)
                            onCallClick(digits, selectedSimSlot)
                            showPasteMenu = false
                        }
                    )
                }
            }

            if (dialedDigits.isNotEmpty()) {
                IconButton(
                    onClick = onBackspace,
                    modifier = Modifier
                        .size(44.dp)
                        .align(Alignment.CenterEnd)
                        .testTag("keypad_backspace_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = AppTheme.colors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // SIM Slot Selection (if Dual SIM)
        if (simAccounts.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                simAccounts.forEach { sim ->
                    val isSelected = sim.slotIndex == selectedSimSlot
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectSimSlot(sim.slotIndex) },
                        label = { Text("SIM ${sim.slotIndex + 1}: ${sim.carrierName}", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppTheme.colors.primary.copy(alpha = 0.2f),
                            selectedLabelColor = AppTheme.colors.primary,
                            containerColor = AppTheme.colors.elevated,
                            labelColor = AppTheme.colors.textSecondary
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }

        // 3x4 Dialpad Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SMART_KEYPAD_KEYS.chunked(3).forEach { rowKeys ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    rowKeys.forEach { key ->
                        SmartKeypadButton(
                            key = key,
                            onClick = {
                                onDigitPress(key.digit)
                            },
                            onLongClick = {
                                if (key.digit == '0') {
                                    onDigitPress('+')
                                } else if (key.digit == '1') {
                                    onDigitPress('1') // Voicemail speed dial
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Action Bar: Symmetrical with Constant Erase Slot
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Constant Slot: Clear All
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                if (dialedDigits.isNotEmpty()) {
                    IconButton(
                        onClick = onClearAll,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AppTheme.colors.elevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear All",
                            tint = AppTheme.colors.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            // Central Call Button
            Button(
                onClick = {
                    if (dialedDigits.isNotBlank()) {
                        onCallClick(dialedDigits, selectedSimSlot)
                    }
                },
                modifier = Modifier
                    .size(width = 160.dp, height = 54.dp)
                    .testTag("keypad_call_action_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colors.secondary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(27.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CALL",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            // Right Constant Slot: Dedicated Constant Erase Button
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                if (dialedDigits.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AppTheme.colors.elevated)
                            .combinedClickable(
                                onClick = onBackspace,
                                onLongClick = onClearAll
                            )
                            .testTag("keypad_constant_erase_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Erase",
                            tint = AppTheme.colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AppTheme.colors.elevated.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Erase Disabled",
                            tint = AppTheme.colors.textMuted.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmartKeypadButton(
    key: KeypadKey,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(AppTheme.colors.card)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("keypad_key_${key.digit}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = key.digit.toString(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            if (key.letters.isNotEmpty()) {
                Text(
                    text = key.letters,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.primary,
                    letterSpacing = 0.5.sp
                )
            } else if (key.subText.isNotEmpty()) {
                Text(
                    text = key.subText,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Normal,
                    color = AppTheme.colors.textMuted
                )
            }
        }
    }
}
