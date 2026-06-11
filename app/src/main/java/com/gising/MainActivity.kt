package com.gising

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.gising.ui.theme.SeismicDisplayFont
import com.gising.ui.theme.SeismicFont
import com.gising.ui.theme.SeismicHot
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.gising.data.auth.AuthState
import com.gising.data.model.Earthquake
import com.gising.data.repository.SettingsStore
import com.gising.service.EarthquakeMonitorService
import com.gising.ui.screens.*
import com.gising.ui.screens.auth.AuthScreen
import com.gising.ui.screens.auth.AuthViewModel
import com.gising.ui.theme.SeismicColors
import com.gising.ui.theme.SeismicWatchTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handle results */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        requestPermissions()

        // Start monitoring service
        EarthquakeMonitorService.start(this)

        // Resume live location sharing for a returning user who left it on (off the UI thread).
        lifecycleScope.launch {
            val snapshot = runCatching { SettingsStore(applicationContext).current() }.getOrNull()
            if (snapshot?.locationSharing == true) {
                com.gising.service.LocationSharingService.start(applicationContext)
            }
        }

        setContent {
            // Observe the persisted appearance choice so theme/mode changes re-skin the whole app live.
            val ctx = LocalContext.current
            val settingsStore = remember { SettingsStore(ctx.applicationContext) }
            val prefs by settingsStore.settings.collectAsState(initial = SettingsStore.Snapshot())

            SeismicWatchTheme(
                appTheme = com.gising.ui.theme.AppTheme.fromKey(prefs.appTheme),
                themeMode = com.gising.ui.theme.ThemeMode.fromKey(prefs.themeMode),
            ) {
                val authViewModel: AuthViewModel = viewModel()
                val authState by authViewModel.authState.collectAsState()

                when (val s = authState) {
                    // No splash: while the session resolves, just hold a plain themed background.
                    is AuthState.Loading -> Box(Modifier.fillMaxSize().background(SeismicHot.Base))

                    is AuthState.SignedOut -> AuthScreen(authViewModel)

                    is AuthState.SignedIn -> {
                        // Email verification is encouraged, not required: the app is fully usable
                        // immediately (so friends on a fresh sideloaded build aren't locked out),
                        // with a dismissible reminder banner for unverified password accounts.
                        val isPasswordUser =
                            s.user.providerData.any { it.providerId == "password" }
                        var bannerDismissed by rememberSaveable(s.user.uid) { mutableStateOf(false) }
                        Box(Modifier.fillMaxSize()) {
                            SeismicWatchApp()
                            if (isPasswordUser && !s.emailVerified && !bannerDismissed) {
                                VerifyEmailBanner(
                                    email = s.user.email.orEmpty(),
                                    onResend = { authViewModel.resendVerification() },
                                    onDismiss = { bannerDismissed = true },
                                    modifier = Modifier.align(Alignment.TopCenter),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.VIBRATE)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(perms.toTypedArray())
    }
}

/** Non-blocking reminder for unverified email/password accounts. Dismissible; never gates the app. */
@Composable
private fun VerifyEmailBanner(
    email: String,
    onResend: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
        color = SeismicHot.Field,
        contentColor = SeismicHot.White,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SeismicHot.Border),
        tonalElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.MarkEmailUnread, contentDescription = null, tint = SeismicHot.Orange)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Verify your email",
                    fontFamily = SeismicFont, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    color = SeismicHot.White,
                )
                Text(
                    if (email.isBlank()) "Confirm your address to secure your account."
                    else "We sent a link to $email.",
                    fontFamily = SeismicFont, fontSize = 11.sp, color = SeismicHot.Label,
                )
            }
            TextButton(onClick = onResend) {
                Text("Resend", color = SeismicHot.Orange, fontFamily = SeismicFont, fontSize = 12.sp)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = SeismicHot.Label)
            }
        }
    }
}

private sealed class Dest(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    data object Home : Dest("home", "Home", Icons.Outlined.Home, Icons.Filled.Home)
    data object Reports : Dest("reports", "Reports", Icons.Outlined.Assessment, Icons.Filled.Assessment)
    data object People : Dest("people", "People", Icons.Outlined.Groups, Icons.Filled.Groups)
    data object Settings : Dest("settings", "Settings", Icons.Outlined.Tune, Icons.Filled.Tune)
}

private val bottomDestinations = listOf(Dest.Home, Dest.Reports, Dest.People, Dest.Settings)

// ── Navigation motion tokens ──────────────────────────────────────────────────
private val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EmphasizedInEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val tabRoutes = bottomDestinations.map { it.route }

/** Index of a route within the bottom tab row, or -1 for non-tab (drill-down) routes. */
private fun tabIndexOf(route: String?): Int = tabRoutes.indexOf(route)

@Composable
fun SeismicWatchApp() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    val contactsViewModel: ContactsViewModel = viewModel()
    val circlesViewModel: com.gising.ui.screens.connections.CirclesViewModel = viewModel()
    val profileViewModel: com.gising.ui.screens.account.ProfileViewModel = viewModel()
    val profileUi by profileViewModel.ui.collectAsState()

    // Feed the device's location into distance/shaking estimates + the user's weather card, and
    // derive a human-readable area label for the home top bar. Uses a FRESH fix (falling back to
    // last-known) so the weather reflects where the user actually is, not the stale Manila default.
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        com.gising.service.LocationProvider.awaitCurrent(context)?.let { (lat, lon) ->
            viewModel.updateLocation(lat, lon)
            runCatching {
                @Suppress("DEPRECATION")
                android.location.Geocoder(context, java.util.Locale.getDefault())
                    .getFromLocation(lat, lon, 1)
                    ?.firstOrNull()
            }.getOrNull()?.let { addr ->
                val label = listOfNotNull(addr.locality ?: addr.subAdminArea, addr.adminArea)
                    .joinToString(", ")
                viewModel.setLocationLabel(label)
            }
        }
    }

    // Hold selected earthquake in state for the detail screen
    var selectedQuake by remember { mutableStateOf<Earthquake?>(null) }
    // Set when "View Area" is tapped on a quake's detail → the Live Map opens framed on that event.
    var mapFocusQuake by remember { mutableStateOf<Earthquake?>(null) }
    val launchSos = {
        com.gising.emergency.SosController.launchActivity(context, com.gising.emergency.SosSource.IN_APP)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomDestinations.map { it.route }

    // Live "someone joined" banner — fed by the circles listener, shown over everything.
    var joinEvent by remember { mutableStateOf<com.gising.ui.screens.connections.MemberJoinEvent?>(null) }
    LaunchedEffect(Unit) {
        circlesViewModel.memberJoined.collect { joinEvent = it }
    }

    // ── Presence: heartbeat "active now" while the app is foregrounded; flip to offline in the
    //    background. This is what makes the People/Chat "Active now" reflect reality (item 4).
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val presence = com.gising.data.repository.PresenceRepository()
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                presence.markActive()
                kotlinx.coroutines.delay(60_000)
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        val presence = com.gising.data.repository.PresenceRepository()
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> presence.markActive()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> presence.markInactive()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── In-app Messenger-style chat banner: pop a heads-up when a peer messages me while I'm on
    //    another screen. rememberUpdatedState keeps the long-lived collector reading fresh values.
    val members by circlesViewModel.members.collectAsState()
    var incomingMessage by remember { mutableStateOf<com.gising.ui.screens.components.MessageBannerData?>(null) }
    val membersState = rememberUpdatedState(members)
    val routeState = rememberUpdatedState(currentRoute)
    val openPeerState = rememberUpdatedState(navBackStackEntry?.arguments?.getString("peerUid"))
    LaunchedEffect(Unit) {
        com.gising.data.repository.ChatInboxRepository().observeIncoming().collect { msg ->
            // Don't interrupt with a banner for the conversation already on screen.
            val inThisChat = routeState.value?.startsWith("chat/") == true &&
                openPeerState.value == msg.peerUid
            if (inThisChat) return@collect
            val name = membersState.value.firstOrNull { it.uid == msg.peerUid }
                ?.displayName?.takeIf { it.isNotBlank() } ?: "New message"
            incomingMessage = com.gising.ui.screens.components.MessageBannerData(msg.peerUid, name, msg.text)
        }
    }

    // Free client-side chat retention: once per app launch, delete conversations with no new message
    // for 7 days. (Firestore's server-side TTL needs the paid Blaze plan; this stays on free Spark.)
    LaunchedEffect(Unit) {
        runCatching { com.gising.data.repository.ChatRepository().purgeExpiredConversations() }
    }

    Box(Modifier.fillMaxSize()) {
    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                val current = navBackStackEntry?.destination
                fun isSel(d: Dest) = current?.hierarchy?.any { it.route == d.route } == true
                // Fixed, docked navigation bar matching the SeismicWatch reference: a flat #0e080b bar
                // with a 1px hot-border top edge, red active / burgundy inactive items, and 10px
                // uppercase labels. Spans edge-to-edge with the system nav inset folded in.
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(SeismicHot.Border))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SeismicHot.Nav)
                            .navigationBarsPadding()
                            .height(64.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        bottomDestinations.forEach { dest ->
                            BottomItem(dest, isSel(dest)) { navigateTo(dest.route) }
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Home.route,
            modifier = Modifier.padding(padding),
            // Snappy, lightweight transitions: short fades (GPU-composited, cheap) keep navigation
            // feeling instant on older devices instead of animating two full screens for 300ms+.
            enterTransition = {
                val from = tabIndexOf(initialState.destination.route)
                val to = tabIndexOf(targetState.destination.route)
                if (from >= 0 && to >= 0) {
                    val dir = if (to >= from) 1 else -1
                    slideInHorizontally(tween(190, easing = EmphasizedEasing)) { full -> (full * 0.08f * dir).toInt() } +
                        fadeIn(tween(140))
                } else {
                    scaleIn(initialScale = 0.96f, animationSpec = tween(190, easing = EmphasizedEasing)) + fadeIn(tween(140))
                }
            },
            exitTransition = { fadeOut(tween(110)) },
            popEnterTransition = {
                val from = tabIndexOf(initialState.destination.route)
                val to = tabIndexOf(targetState.destination.route)
                if (from >= 0 && to >= 0) {
                    val dir = if (to >= from) 1 else -1
                    slideInHorizontally(tween(190, easing = EmphasizedEasing)) { full -> (full * 0.08f * dir).toInt() } +
                        fadeIn(tween(140))
                } else {
                    scaleIn(initialScale = 0.97f, animationSpec = tween(190, easing = EmphasizedEasing)) + fadeIn(tween(140))
                }
            },
            popExitTransition = { fadeOut(tween(120)) }
        ) {
            composable(Dest.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onEarthquakeClick = { quake ->
                        selectedQuake = quake
                        navController.navigate("detail")
                    },
                    onSos = { launchSos() },
                    onCheckSavedPlaces = { navigateTo(Dest.People.route) },
                    onOpenMap = { navController.navigate("livemap") },
                    onOpenCyclone = { navController.navigate("cyclone") },
                    userName = profileUi.displayName,
                    userInitial = profileUi.initial,
                    userPhotoUrl = profileUi.photoUrl,
                    onOpenProfile = { navController.navigate("profile") },
                    onOpenNotifications = { navController.navigate("notifications") },
                )
            }

            composable(Dest.Reports.route) {
                ReportsScreen(
                    viewModel = viewModel,
                    onEarthquakeClick = { quake ->
                        selectedQuake = quake
                        navController.navigate("detail")
                    },
                    onOpenCyclone = { navController.navigate("cyclone") },
                    onOpenNotifications = { navController.navigate("notifications") },
                )
            }

            composable(Dest.People.route) {
                com.gising.ui.screens.connections.PeopleScreen(
                    circlesViewModel = circlesViewModel,
                    contactsViewModel = contactsViewModel,
                    onOpenNotifications = { navController.navigate("notifications") },
                    onOpenMap = { navController.navigate("livemap") },
                    onOpenChat = { peerUid, peerName ->
                        navController.navigate("chat/$peerUid/${android.net.Uri.encode(peerName)}")
                    },
                )
            }

            composable("chat/{peerUid}/{peerName}") { backStackEntry ->
                val peerUid = backStackEntry.arguments?.getString("peerUid").orEmpty()
                val peerName = android.net.Uri.decode(
                    backStackEntry.arguments?.getString("peerName").orEmpty()
                )
                com.gising.ui.screens.chat.ChatScreen(
                    peerUid = peerUid,
                    peerName = peerName.ifBlank { "Chat" },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Dest.Settings.route) {
                SettingsScreen(
                    onOpenProfile = { navController.navigate("profile") },
                    onOpenPeople = { navigateTo(Dest.People.route) },
                )
            }

            composable("profile") {
                ProfileScreen(
                    viewModel = profileViewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            composable("livemap") {
                LiveMapScreen(
                    viewModel = viewModel,
                    onEarthquakeClick = { quake ->
                        selectedQuake = quake
                        navController.navigate("detail")
                    },
                    onOpenCyclone = { navController.navigate("cyclone") },
                    initialFocus = mapFocusQuake,
                    onFocusConsumed = { mapFocusQuake = null },
                    onBack = { navController.popBackStack() },
                )
            }

            composable("cyclone") {
                CycloneTrackerScreen(onBack = { navController.popBackStack() })
            }

            composable("detail") {
                val quake = selectedQuake
                if (quake != null) {
                    DetailScreen(
                        earthquake = quake,
                        viewModel = viewModel,
                        onViewArea = { eq ->
                            mapFocusQuake = eq
                            navController.navigate("livemap")
                        },
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    navController.popBackStack()
                }
            }

            composable("notifications") {
                NotificationsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onEarthquakeClick = { quake ->
                        selectedQuake = quake
                        navController.navigate("detail")
                    },
                )
            }
        }
    }

        com.gising.ui.screens.connections.MemberJoinBanner(
            event = joinEvent,
            onDismiss = { joinEvent = null },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp),
        )

        // Heads-up chat banner rides above everything else.
        com.gising.ui.screens.components.InAppMessageBanner(
            data = incomingMessage,
            onOpen = { d ->
                incomingMessage = null
                navController.navigate("chat/${d.peerUid}/${android.net.Uri.encode(d.name)}")
            },
            onDismiss = { incomingMessage = null },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BottomItem(
    dest: Dest,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (selected) SeismicHot.Red else SeismicHot.Muted
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
        ),
        label = "navIconScale",
    )
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (selected) dest.selectedIcon else dest.icon,
            contentDescription = dest.label,
            tint = color,
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        )
        Spacer(Modifier.height(5.dp))
        Text(
            dest.label.uppercase(),
            fontFamily = SeismicFont,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = color,
        )
    }
}
