package com.gising.ui.screens.connections

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gising.data.model.EmergencyContact
import com.gising.data.model.firebase.CircleMember
import com.gising.ui.screens.ContactsViewModel
import com.gising.ui.screens.formatRelativeTime
import com.gising.ui.theme.SeismicDisplayFont
import com.gising.ui.theme.SeismicFont
import com.gising.ui.theme.SeismicHot
import com.gising.ui.theme.seismicEntrance
import com.gising.ui.theme.seismicTacticalBackground

/**
 * People — "SeismicWatch · People Monitor". A tactical roster of the user's family & friends: a header
 * with a top-right notification bell, a search field, status stat-chips, and contact cards grouped
 * into "Needs Attention" and "Confirmed Safe", each with quick call / message / locate actions and
 * an optimistic check-in. Backed by the real emergency-contacts list plus any live-location circle
 * members; managing circles & adding contacts stays one tap away behind the FAB.
 */
@Composable
fun PeopleScreen(
    circlesViewModel: CirclesViewModel,
    contactsViewModel: ContactsViewModel,
    onOpenNotifications: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenChat: (peerUid: String, peerName: String) -> Unit = { _, _ -> },
) {
    val circles by circlesViewModel.circles.collectAsState()
    val members by circlesViewModel.members.collectAsState()
    val contacts by contactsViewModel.contacts.collectAsState()

    // Ensure a circle is selected so its live members populate the monitor.
    LaunchedEffect(circles) {
        if (circles.isNotEmpty() && circlesViewModel.selectedCircleId.value == null) {
            circlesViewModel.select(circles.first().id)
        }
    }

    var manage by remember { mutableStateOf(false) }

    if (manage) {
        ManageOverlay(onBack = { manage = false }) {
            FamilyHubScreen(circlesViewModel = circlesViewModel, contactsViewModel = contactsViewModel)
        }
        return
    }

    PeopleMonitor(
        members = members,
        contacts = contacts,
        onOpenMap = onOpenMap,
        onManage = { manage = true },
        onOpenChat = onOpenChat,
    )
}

// ─── Derived person model ─────────────────────────────────────────────────────
private enum class PersonStatus(val label: String, val color: Color) {
    SOS("SOS ALERT", SeismicHot.Red),
    UNCONFIRMED("UNCONFIRMED", Color(0xFFF59E0B)),
    SAFE("SAFE", SeismicHot.Safe),
}

private data class Person(
    val id: String,
    val name: String,
    val initials: String,
    val photoUrl: String,
    val status: PersonStatus,
    val subtitle: String,
    val checkInMs: Long?,
    val phone: String?,
    val avatar: Color,
    /** Firebase uid when this person is a real app user (circle member) → enables in-app chat.
     *  Null for phone-only emergency contacts, who fall back to SMS. */
    val chatUid: String? = null,
)

private val AVATAR_PALETTE = listOf(
    Color(0xFFFF2D55), Color(0xFFFF6A00), Color(0xFF22C55E),
    Color(0xFF4D7CFE), Color(0xFF9B5DE5), Color(0xFFB5651D),
)

private fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifBlank { "?" }

@Composable
private fun PeopleMonitor(
    members: List<CircleMember>,
    contacts: List<EmergencyContact>,
    onOpenMap: () -> Unit,
    onManage: () -> Unit,
    onOpenChat: (peerUid: String, peerName: String) -> Unit,
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    // Exclude the signed-in user — you don't monitor or chat with yourself.
    val myUid = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }

    var query by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val people = remember(members, contacts, myUid, now) {
        buildPeople(members, contacts, myUid, now)
    }
    val filtered = people.filter {
        query.isBlank() || it.name.contains(query, true) || it.subtitle.contains(query, true)
    }

    Box(Modifier.fillMaxSize().seismicTacticalBackground()) {
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = SeismicFont)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding(),
            ) {
                Spacer(Modifier.height(10.dp))
                MonitorHeader(
                    modifier = Modifier.seismicEntrance(visible, 0),
                )
                Spacer(Modifier.height(16.dp))
                SearchBar(query, { query = it }, modifier = Modifier.seismicEntrance(visible, 40))
                Spacer(Modifier.height(18.dp))

                // Flat roster — each person shows call, chat and live-location actions.
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    filtered.forEachIndexed { i, p ->
                        PersonCard(
                            person = p,
                            onCall = { p.phone?.let { dial(context, it) } },
                            onMessage = {
                                if (p.chatUid != null) onOpenChat(p.chatUid, p.name)
                                else p.phone?.let { sms(context, it) }
                            },
                            onLocate = onOpenMap,
                            modifier = Modifier.seismicEntrance(visible, 80 + i * 30),
                        )
                    }
                }
                Spacer(Modifier.height(120.dp))
            }
        }

        // Add-contact FAB.
        ScaleButton(
            onClick = onManage,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 96.dp),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .shadow(20.dp, RoundedCornerShape(18.dp), spotColor = SeismicHot.Red, ambientColor = SeismicHot.Orange)
                    .clip(RoundedCornerShape(18.dp))
                    .background(SeismicHot.Gradient),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.PersonAdd, "Add contact", tint = SeismicHot.White, modifier = Modifier.size(24.dp)) }
        }
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────
@Composable
private fun MonitorHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Family &", fontFamily = SeismicDisplayFont, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, color = SeismicHot.White)
                Spacer(Modifier.width(6.dp))
                GradientText("Friends", 26.sp)
            }
            Spacer(Modifier.height(2.dp))
            Text("Track your family & friends", fontSize = 12.sp, color = SeismicHot.Muted)
        }
    }
}

@Composable
private fun GradientText(text: String, size: androidx.compose.ui.unit.TextUnit) {
    Text(
        text,
        fontFamily = SeismicDisplayFont,
        fontSize = size,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1).sp,
        style = LocalTextStyle.current.copy(brush = SeismicHot.Gradient),
    )
}

// ─── Search ──────────────────────────────────────────────────────────────────
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SeismicHot.Field)
            .border(1.5.dp, SeismicHot.Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = SeismicHot.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search contacts...", color = SeismicHot.Muted, fontSize = 14.sp)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = SeismicHot.White, fontSize = 14.sp, fontFamily = SeismicFont),
                cursorBrush = Brush.linearGradient(listOf(SeismicHot.Red, SeismicHot.Red)),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─── Person card ─────────────────────────────────────────────────────────────
@Composable
private fun PersonCard(
    person: Person,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Real presence for app users (circle members): "Active now" only fires while they actually have
    // the app open (a live heartbeat), triggered when they open it — not a permanent green dot.
    val presence = if (person.chatUid != null) {
        val p by remember(person.chatUid) {
            com.gising.data.repository.PresenceRepository().observe(person.chatUid)
        }.collectAsState(initial = com.gising.data.repository.Presence())
        p
    } else null
    val activeNow = presence?.isActiveNow() == true

    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SeismicHot.Card)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(person, activeNow = activeNow)
            Column(Modifier.weight(1f)) {
                Text(person.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                // Presence line for app users; falls back to the location subtitle for phone contacts.
                if (presence != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (activeNow) SeismicHot.Safe else SeismicHot.Muted))
                        Text(
                            presence.label(),
                            fontSize = 11.5.sp,
                            fontWeight = if (activeNow) FontWeight.Bold else FontWeight.Normal,
                            color = if (activeNow) SeismicHot.Safe else SeismicHot.Label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.Place, null, tint = SeismicHot.Muted, modifier = Modifier.size(12.dp))
                    Text(person.subtitle, fontSize = 11.5.sp, color = SeismicHot.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                person.checkInMs?.let {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Last seen: ", fontSize = 11.sp, color = SeismicHot.Muted)
                        Text(formatRelativeTime(it), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SeismicHot.Label)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // Actions: call, chat, and locate (live-location monitoring).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconSquare(Icons.Outlined.Call, SeismicHot.Safe, onClick = onCall)
            IconSquare(Icons.Outlined.ChatBubbleOutline, SeismicHot.Red, onClick = onMessage)
            IconSquare(Icons.Outlined.NearMe, SeismicHot.Orange, onClick = onLocate)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Avatar(person: Person, activeNow: Boolean = false) {
    Box(contentAlignment = Alignment.Center) {
        // SOS gets the ripple-ring treatment.
        if (person.status == PersonStatus.SOS) {
            val transition = rememberInfiniteTransition(label = "sos")
            repeat(3) { i ->
                val phase by transition.animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(2000, easing = FastOutSlowInEasing), RepeatMode.Restart,
                        initialStartOffset = StartOffset(i * 650),
                    ),
                    label = "sosRing$i",
                )
                Box(
                    Modifier.size(48.dp).graphicsLayer {
                        val s = 1f + phase * 1.2f; scaleX = s; scaleY = s
                        alpha = (0.55f * (1f - phase)).coerceIn(0f, 1f)
                    }.border(1.5.dp, SeismicHot.Red, CircleShape),
                )
            }
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(
                if (person.status == PersonStatus.SOS) SeismicHot.Gradient
                else Brush.linearGradient(listOf(person.avatar, person.avatar))
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(person.initials, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
        }
        // Status dot, 11px with a 2px ring. A live "active now" heartbeat turns it green regardless of
        // the location-derived status, so presence is reflected the moment the peer opens the app.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(13.dp)
                .clip(CircleShape)
                .background(SeismicHot.Base)
                .padding(1.5.dp)
                .clip(CircleShape)
                .background(if (activeNow) SeismicHot.Safe else person.status.color),
        )
    }
}

@Composable
private fun IconSquare(icon: ImageVector, color: Color, onClick: () -> Unit) {
    ScaleButton(onClick = onClick) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = 0.12f)).border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = color, modifier = Modifier.size(17.dp)) }
    }
}

// ─── Generic press-to-scale wrapper ──────────────────────────────────────────
@Composable
private fun ScaleButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "scaleBtn")
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) { content() }
}

// ─── Manage overlay (existing add/circle flows) ───────────────────────────────
@Composable
private fun ManageOverlay(onBack: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(SeismicHot.Base)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScaleButton(onClick = onBack) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(SeismicHot.Card).border(1.dp, SeismicHot.Border, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.ArrowBack, "Back", tint = SeismicHot.White, modifier = Modifier.size(18.dp)) }
                }
                Spacer(Modifier.width(12.dp))
                Text("Manage people", fontFamily = SeismicDisplayFont, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
            }
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

// ─── Builders & intents ───────────────────────────────────────────────────────
private fun buildPeople(
    members: List<CircleMember>,
    contacts: List<EmergencyContact>,
    selfUid: String?,
    now: Long,
): List<Person> {
    val out = mutableListOf<Person>()

    members.forEachIndexed { i, m ->
        if (m.uid == selfUid) return@forEachIndexed   // never list / chat with yourself
        val loc = m.lastLocation
        // Recent location → "live" presence dot; otherwise a faint "unconfirmed" dot. This drives
        // only the small avatar indicator, not any safe/unsafe labeling.
        val recent = m.sharingEnabled && loc != null && now - loc.updatedAt <= 20L * 60_000
        out += Person(
            id = "m-${m.uid}",
            name = m.displayName.ifBlank { "Member" },
            initials = initialsOf(m.displayName),
            photoUrl = m.photoUrl,
            status = if (recent) PersonStatus.SAFE else PersonStatus.UNCONFIRMED,
            subtitle = when {
                !m.sharingEnabled -> "Location sharing off"
                loc == null -> "Waiting for location"
                else -> "Sharing live location"
            },
            checkInMs = loc?.updatedAt,
            phone = null,
            avatar = AVATAR_PALETTE[i % AVATAR_PALETTE.size],
            chatUid = m.uid,
        )
    }

    contacts.forEachIndexed { i, c ->
        out += Person(
            id = "c-${c.id}",
            name = c.name,
            initials = initialsOf(c.name),
            photoUrl = "",
            status = PersonStatus.UNCONFIRMED,
            subtitle = c.relationship.ifBlank { "Emergency contact" },
            checkInMs = null,
            phone = c.phone.ifBlank { null },
            avatar = AVATAR_PALETTE[(members.size + i) % AVATAR_PALETTE.size],
        )
    }
    return out
}

private fun dial(context: android.content.Context, phone: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
}

private fun sms(context: android.content.Context, phone: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))) }
}
