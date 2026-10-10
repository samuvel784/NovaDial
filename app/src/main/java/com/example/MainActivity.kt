package com.example

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.screens.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            NovaDialTheme(themeMode = themeMode) {
                NovaDialApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkDefaultDialer(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.checkDefaultDialer(this)
    }

    override fun onStop() {
        super.onStop()
        viewModel.lockApp()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaDialApp(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isAppLocked by viewModel.isAppLocked.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val activeCallSession by viewModel.activeCallSession.collectAsState()
    val postCallSheetSession by viewModel.postCallSheetSession.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Request permissions gracefully on first launch
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val contactsGranted = permissions[Manifest.permission.READ_CONTACTS] == true
        val callLogGranted = permissions[Manifest.permission.READ_CALL_LOG] == true
        if (contactsGranted || callLogGranted) {
            scope.launch {
                viewModel.repository.syncWithDeviceContacts()
                viewModel.refreshDuplicates()
            }
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.ANSWER_PHONE_CALLS
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val isCallActive = activeCallSession.state != com.example.telecom.CallSessionState.IDLE
    if (isAppLocked && !isCallActive) {
        AppLockScreen(viewModel = viewModel)
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = AppTheme.colors.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                val showKeypadFab = currentScreen !is Screen.Dialer &&
                        currentScreen !is Screen.EditContact &&
                        currentScreen !is Screen.Emergency

                if (showKeypadFab) {
                    FloatingActionButton(
                        onClick = { viewModel.navigateTo(Screen.Dialer) },
                        containerColor = AppTheme.colors.primary,
                        contentColor = if (AppTheme.colors.isLight) Color.White else Color.Black,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .testTag("constant_keypad_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dialpad,
                            contentDescription = "Open Keypad",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.End,
            bottomBar = {
                // Bottom navigation bar is visible on root primary tabs: Home, Recent, Favorites
                val isRootTab = currentScreen is Screen.Home ||
                        currentScreen is Screen.Recents ||
                        currentScreen is Screen.Favorites

                if (isRootTab) {
                    NavigationBar(
                        containerColor = AppTheme.colors.surface,
                        contentColor = AppTheme.colors.textPrimary,
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .testTag("main_navigation_bar")
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        // 1. HOME (Default)
                        NavigationBarItem(
                            selected = currentScreen is Screen.Home,
                            onClick = { viewModel.navigateTo(Screen.Home) },
                            icon = { Icon(Icons.Default.People, contentDescription = "Home") },
                            label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = if (AppTheme.colors.isLight) Color.White else Color.Black,
                                selectedTextColor = AppTheme.colors.primary,
                                indicatorColor = AppTheme.colors.primary,
                                unselectedIconColor = AppTheme.colors.textSecondary,
                                unselectedTextColor = AppTheme.colors.textSecondary
                            ),
                            modifier = Modifier.testTag("nav_item_home")
                        )

                        // 2. RECENT (Call Logs & Voicemail)
                        NavigationBarItem(
                            selected = currentScreen is Screen.Recents,
                            onClick = { viewModel.navigateTo(Screen.Recents) },
                            icon = { Icon(Icons.Default.History, contentDescription = "Recent") },
                            label = { Text("Recent", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = if (AppTheme.colors.isLight) Color.White else Color.Black,
                                selectedTextColor = AppTheme.colors.primary,
                                indicatorColor = AppTheme.colors.primary,
                                unselectedIconColor = AppTheme.colors.textSecondary,
                                unselectedTextColor = AppTheme.colors.textSecondary
                            ),
                            modifier = Modifier.testTag("nav_item_recents")
                        )

                        // 3. FAVORITES
                        NavigationBarItem(
                            selected = currentScreen is Screen.Favorites,
                            onClick = { viewModel.navigateTo(Screen.Favorites) },
                            icon = { Icon(Icons.Default.Star, contentDescription = "Favorites") },
                            label = { Text("Favorites", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = if (AppTheme.colors.isLight) Color.White else Color.Black,
                                selectedTextColor = AppTheme.colors.tertiary,
                                indicatorColor = AppTheme.colors.tertiary,
                                unselectedIconColor = AppTheme.colors.textSecondary,
                                unselectedTextColor = AppTheme.colors.textSecondary
                            ),
                            modifier = Modifier.testTag("nav_item_favorites")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (val screen = currentScreen) {
                    is Screen.Home -> HomeScreen(viewModel = viewModel)
                    is Screen.Recents -> RecentsScreen(viewModel = viewModel)
                    is Screen.Dialer -> DialerScreen(viewModel = viewModel)
                    is Screen.Favorites -> FavoritesScreen(viewModel = viewModel)
                    is Screen.Contacts -> HomeScreen(viewModel = viewModel)
                    is Screen.ImportedContacts -> ImportedContactsScreen(viewModel = viewModel)
                    is Screen.ContactDetail -> ContactDetailScreen(contactId = screen.contactId, viewModel = viewModel)
                    is Screen.EditContact -> EditContactScreen(contactId = screen.contactId, viewModel = viewModel)
                    is Screen.DuplicateReview -> DuplicateReviewScreen(viewModel = viewModel)
                    is Screen.Backup -> BackupScreen(viewModel = viewModel)
                    is Screen.Settings -> SettingsScreen(viewModel = viewModel)
                    is Screen.Analytics -> AnalyticsScreen(viewModel = viewModel)
                    is Screen.Emergency -> EmergencyScreen(viewModel = viewModel)
                    is Screen.SpamBlocklistManager -> SpamBlocklistManagerScreen(viewModel = viewModel)
                }
            }
        }

        // Active Call Screen Overlay with real-time Signal Strength & Quality Indicators
        AnimatedVisibility(
            visible = activeCallSession.state != com.example.telecom.CallSessionState.IDLE,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ActiveCallScreen(viewModel = viewModel)
        }

        // Post-Call Quick Notes & Action Bar Sheet
        postCallSheetSession?.let { session ->
            com.example.ui.components.PostCallSheet(
                session = session,
                onDismiss = { viewModel.dismissPostCallSheet() },
                onSaveNote = { num, name, note ->
                    viewModel.addPostCallNote(num, name, note)
                },
                onSaveContact = { num ->
                    viewModel.setDialerNumber(num)
                    viewModel.navigateTo(Screen.EditContact())
                    viewModel.dismissPostCallSheet()
                },
                onReportSpam = { num, cat, comment ->
                    viewModel.submitSpamReport(num, cat, comment)
                }
            )
        }
    }
}
