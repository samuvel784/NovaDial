package com.example.ui.screens

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.theme.AppTheme

@Composable
fun AppLockScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyguardManager = remember {
        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    }
    val isPhoneLockSet = remember { keyguardManager?.isDeviceSecure == true }
    var authError by remember { mutableStateOf<String?>(null) }

    // Launcher for Android's system Keyguard Device Credential (Phone's own PIN / Pattern / Password / Biometric)
    val deviceCredentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.unlockApp()
        } else {
            authError = "Phone authentication was not completed"
        }
    }

    fun promptPhoneLock() {
        authError = null
        if (keyguardManager != null && keyguardManager.isDeviceSecure) {
            val intent = keyguardManager.createConfirmDeviceCredentialIntent(
                "Unlock NovaDial",
                "Use your phone's screen lock (PIN, Pattern, Password, or Biometrics)"
            )
            if (intent != null) {
                deviceCredentialLauncher.launch(intent)
            } else {
                authError = "System device credential prompt is unavailable."
            }
        } else {
            authError = "No device screen lock set up in Android Settings. Please configure a PIN, Pattern, or Biometrics in system settings."
        }
    }

    // Auto-prompt on appearance
    LaunchedEffect(Unit) {
        if (isPhoneLockSet) {
            promptPhoneLock()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
            .testTag("app_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPhoneLockSet) Icons.Default.PhonelinkLock else Icons.Default.Lock,
                    contentDescription = "Phone Lock",
                    tint = AppTheme.colors.primary,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "NovaDial Locked",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isPhoneLockSet) {
                    "Protected with your phone's screen lock (PIN, Pattern, Password, or Biometrics)"
                } else {
                    "Tap unlock to access NovaDial contacts and dialer"
                },
                fontSize = 14.sp,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (authError != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = authError!!,
                    color = AppTheme.colors.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Primary Unlock Button
            Button(
                onClick = { promptPhoneLock() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("unlock_phone_lock_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colors.primary,
                    contentColor = if (AppTheme.colors.isLight) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isPhoneLockSet) "Unlock with Phone Lock" else "Unlock NovaDial",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = AppTheme.colors.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isPhoneLockSet) "Device Credential System Active" else "Phone Screen Lock Detection Ready",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }
        }
    }
}
