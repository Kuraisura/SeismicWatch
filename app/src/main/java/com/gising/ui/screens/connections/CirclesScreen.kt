package com.gising.ui.screens.connections

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gising.data.model.firebase.Circle
import com.gising.data.model.firebase.CircleMember
import com.gising.ui.theme.SeismicHot
import kotlinx.coroutines.launch

/**
 * The "Family" live-location hub: pick a circle, see everyone's latest position, battery
 * and last-seen, manage who's in it, and control your own sharing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CirclesScreen(viewModel: CirclesViewModel) {
    val circles by viewModel.circles.collectAsState()
    val members by viewModel.members.collectAsState()
    val familyPins by viewModel.familyPins.collectAsState()
    val selectedId by viewModel.selectedCircleId.collectAsState()
    val masterSharing by viewModel.masterSharing.collectAsState()
    val bgConsentGiven by viewModel.bgConsentGiven.collectAsState()
    val message by viewModel.message.collectAsState()
    val pendingInvite by viewModel.pendingInvite.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var showConsent by remember { mutableStateOf(false) }

    // ── Enable-sharing flow: prominent disclosure → foreground perm → background perm ──
    fun hasForegroundLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    val enableNow: () -> Unit = { viewModel.setMasterSharing(true) }

    val bgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Foreground sharing works regardless; background grant just extends it.
        enableNow()
    }

    val requestBackground: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            enableNow()
        }
    }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            requestBackground()
        } else {
            scope.launch { snackbar.showSnackbar("Location permission is needed to share with family.") }
        }
    }

    val proceedPermissions: () -> Unit = {
        if (hasForegroundLocation()) requestBackground()
        else foregroundLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    val beginEnable: () -> Unit = {
        if (bgConsentGiven) proceedPermissions() else showConsent = true
    }

    // Auto-select the first circle once loaded.
    LaunchedEffect(circles) {
        if (selectedId == null && circles.isNotEmpty()) viewModel.select(circles.first().id)
    }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }
    // Surface a freshly created invite via the system share sheet.
    LaunchedEffect(pendingInvite) {
        pendingInvite?.let { invite ->
            val text = "Join my family circle \"${invite.circleName}\" on SeismicWatch\n" +
                "Open the app → Family → Join, and enter code: ${invite.code}"
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(share, "Invite to circle"))
            viewModel.consumeInvite()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = SeismicHot.Base,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            MasterSharingBanner(
                enabled = masterSharing,
                onToggle = { enabled ->
                    if (enabled) beginEnable() else viewModel.setMasterSharing(false)
                },
            )

            // Circle selector row
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(circles, key = { it.id }) { circle ->
                    CircleChip(
                        circle = circle,
                        selected = circle.id == selectedId,
                        onClick = { viewModel.select(circle.id) },
                    )
                }
                item {
                    AssistChip(
                        onClick = { showCreate = true },
                        label = { Text("New") },
                        leadingIcon = { Icon(Icons.Filled.Add, null) },
                    )
                }
                item {
                    AssistChip(
                        onClick = { showJoin = true },
                        label = { Text("Join") },
                        leadingIcon = { Icon(Icons.Filled.GroupAdd, null) },
                    )
                }
            }

            HorizontalDivider(color = SeismicHot.Border)

            if (circles.isEmpty()) {
                EmptyCircles(onCreate = { showCreate = true }, onJoin = { showJoin = true })
            } else {
                val selectedCircle = circles.firstOrNull { it.id == selectedId }
                FamilyMap(pins = familyPins)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(members, key = { it.uid }) { member ->
                        MemberCard(
                            member = member,
                            isMe = member.uid == viewModel.myUid,
                            isOwnerViewing = selectedCircle?.ownerUid == viewModel.myUid,
                            onToggleMySharing = { enabled ->
                                selectedId?.let { viewModel.setCircleSharing(it, enabled) }
                            },
                            onRemove = {
                                selectedId?.let { viewModel.removeMember(it, member.uid) }
                            },
                            onLeave = { selectedId?.let { viewModel.leaveCircle(it) } },
                        )
                    }
                    item(key = "invite-cta") {
                        InviteButton(
                            onClick = { selectedId?.let { viewModel.createInvite(it) } },
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        TextInputDialog(
            title = "Create a circle",
            label = "Circle name (e.g. Pamilya)",
            confirmLabel = "Create",
            onConfirm = { name -> viewModel.createCircle(name); showCreate = false },
            onDismiss = { showCreate = false },
        )
    }
    if (showJoin) {
        TextInputDialog(
            title = "Join a circle",
            label = "Invite code",
            confirmLabel = "Join",
            uppercase = true,
            onConfirm = { code -> viewModel.joinByCode(code); showJoin = false },
            onDismiss = { showJoin = false },
        )
    }
    if (showConsent) {
        BackgroundLocationDisclosureDialog(
            onAccept = {
                showConsent = false
                viewModel.setBgConsent(true)
                proceedPermissions()
            },
            onDecline = { showConsent = false },
        )
    }
}

/** Full-width invite action; replaces the floating FAB so it can never overlap a member card. */
@Composable
private fun InviteButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SeismicHot.Safe),
    ) {
        Icon(Icons.Filled.PersonAdd, null, Modifier.size(20.dp), tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(
            "Invite to circle",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Live family map: plots every circle member who's currently sharing a location as a labeled teal
 * pin on the open-source (MapLibre + OpenStreetMap) canvas. Falls back to a short hint when no one
 * is sharing yet.
 */
@Composable
private fun FamilyMap(pins: List<FamilyPin>) {
    // Empty state stays compact so it doesn't claim hero space; expands once people are sharing.
    val targetHeight by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (pins.isEmpty()) 120.dp else 220.dp,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.85f,
            stiffness = 260f,
        ),
        label = "mapHeight",
    )
    Surface(
        color = SeismicHot.Card,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(targetHeight),
    ) {
        if (pins.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No one is sharing their location yet. Once family turn on sharing, you'll see " +
                        "them here on the map.",
                    color = SeismicHot.Muted,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            val mapPins = pins.map { p ->
                com.gising.ui.screens.components.MapPin(
                    id = p.name + "@" + p.updatedAt,
                    lat = p.lat,
                    lon = p.lng,
                    colorInt = 0xFF00C7BE.toInt(),
                    radiusDp = 8f,
                    label = p.name,
                )
            }
            val centerLat = pins.map { it.lat }.average()
            val centerLon = pins.map { it.lng }.average()
            com.gising.ui.screens.components.MapLibreCanvas(
                pins = mapPins,
                initialLat = centerLat,
                initialLon = centerLon,
                initialZoom = if (pins.size == 1) 12.0 else 8.0,
                minZoom = 3.0,
                maxZoom = 16.0,
                showLabels = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MasterSharingBanner(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(color = if (enabled) SeismicHot.Card else SeismicHot.Card) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (enabled) Icons.Filled.LocationOn else Icons.Filled.LocationOff,
                contentDescription = null,
                tint = if (enabled) SeismicHot.Orange else SeismicHot.Muted,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (enabled) "You're sharing your location" else "Location sharing is off",
                    color = SeismicHot.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (enabled) "Only members of your circles can see you."
                    else "Turn on to let your family see where you are.",
                    color = SeismicHot.Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SeismicHot.White,
                    checkedTrackColor = SeismicHot.Red,
                ),
            )
        }
    }
}

@Composable
private fun CircleChip(circle: Circle, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(circle.name.ifBlank { "Circle" }) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SeismicHot.Red,
            selectedLabelColor = SeismicHot.White,
        ),
    )
}

@Composable
private fun MemberCard(
    member: CircleMember,
    isMe: Boolean,
    isOwnerViewing: Boolean,
    onToggleMySharing: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onLeave: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val loc = member.lastLocation
    Surface(
        color = SeismicHot.Card,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .background(SeismicHot.Field),
                contentAlignment = Alignment.Center,
            ) {
                if (member.photoUrl.isNotBlank()) {
                    coil.compose.AsyncImage(
                        model = member.photoUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(
                        member.displayName.take(1).uppercase().ifBlank { "?" },
                        color = SeismicHot.Orange,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isMe) "${member.displayName} (You)" else member.displayName,
                        color = SeismicHot.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (member.isOwner) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = SeismicHot.Field,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                "OWNER",
                                color = SeismicHot.Muted,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                // Your own card reads the device battery live (via a broadcast receiver) instead of the
                // last value pushed to Firestore, so it always matches the phone's real percentage.
                val liveBattery = if (isMe) rememberLiveBattery() else null
                val status = when {
                    !member.sharingEnabled -> "Paused — location hidden"
                    loc == null -> "No location yet"
                    else -> {
                        val pct = liveBattery?.first ?: loc.batteryPct
                        val charging = liveBattery?.second ?: loc.isCharging
                        "${lastSeen(loc.updatedAt)} · ${batteryLabel(pct, charging)}"
                    }
                }
                Text(
                    status,
                    color = if (member.sharingEnabled) SeismicHot.Muted else SeismicHot.Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (isMe) {
                // Quick per-circle ghost toggle for myself.
                Switch(
                    checked = member.sharingEnabled,
                    onCheckedChange = onToggleMySharing,
                    colors = SwitchDefaults.colors(checkedTrackColor = SeismicHot.Red),
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More",
                        tint = SeismicHot.Muted)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (isMe) {
                        DropdownMenuItem(
                            text = { Text("Leave circle") },
                            onClick = { menuOpen = false; onLeave() },
                        )
                    } else if (isOwnerViewing) {
                        DropdownMenuItem(
                            text = { Text("Remove from circle") },
                            onClick = { menuOpen = false; onRemove() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCircles(onCreate: () -> Unit, onJoin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No circles yet", color = SeismicHot.White,
            style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Create a family circle and invite people, or join one with a code.",
            color = SeismicHot.Muted,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onCreate,
            colors = ButtonDefaults.buttonColors(containerColor = SeismicHot.Red),
        ) { Text("Create a circle") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onJoin) {
            Text("I have an invite code", color = SeismicHot.Orange)
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    confirmLabel: String,
    uppercase: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SeismicHot.Field,
        title = { Text(title, color = SeismicHot.White) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = if (uppercase) it.uppercase() else it },
                label = { Text(label) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SeismicHot.Red,
                    focusedTextColor = SeismicHot.White,
                    unfocusedTextColor = SeismicHot.White,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text(confirmLabel, color = SeismicHot.Orange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SeismicHot.Muted) }
        },
    )
}

// ── small formatting helpers ─────────────────────────────────────────────────

private fun lastSeen(epochMs: Long): String {
    if (epochMs <= 0) return "No location yet"
    val diff = System.currentTimeMillis() - epochMs
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${(diff / 60_000)}m ago"
        diff < 86_400_000 -> "${(diff / 3_600_000)}h ago"
        else -> "${(diff / 86_400_000)}d ago"
    }
}

/**
 * Live device battery as (percent 0–100, isCharging), refreshed in real time from the sticky
 * ACTION_BATTERY_CHANGED broadcast. Used for the signed-in user's own card so the reading never
 * shows a stale/flat value.
 */
@Composable
private fun rememberLiveBattery(): Pair<Int, Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(readBatterySnapshot(context)) }
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: Intent?) {
                intent ?: return
                state.value = intent.toBatterySnapshot()
            }
        }
        context.registerReceiver(receiver, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return state.value
}

private fun readBatterySnapshot(context: android.content.Context): Pair<Int, Boolean> =
    context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?.toBatterySnapshot() ?: (-1 to false)

private fun Intent.toBatterySnapshot(): Pair<Int, Boolean> {
    val level = getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
    val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
    val status = getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
    val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
        status == android.os.BatteryManager.BATTERY_STATUS_FULL
    return pct to charging
}

private fun batteryLabel(pct: Int, charging: Boolean): String = when {
    pct < 0 -> "Battery —"
    charging -> "⚡ $pct%"
    else -> "$pct%"
}
