package com.gising.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gising.data.model.firebase.ChatMessage
import com.gising.data.repository.ChatRepository
import com.gising.ui.theme.SeismicFont
import com.gising.ui.theme.SeismicHot
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Messenger / Facebook-style 1:1 conversation. Messages stream live from Firestore via
 * [ChatRepository]; the repository is held in [remember] (it is stateless and cheap) and the message
 * flow is collected directly — no ViewModel needed for such a small surface.
 *
 * Bubbles: my messages right-aligned on the brand gradient, the peer's left-aligned on a card. Day
 * separators ("Today" / "Yesterday" / date) break up the log and a small timestamp sits under the
 * last bubble of each sender run. Fully themed via [SeismicHot], so it recolors with the app theme.
 */
@Composable
fun ChatScreen(
    peerUid: String,
    peerName: String,
    onBack: () -> Unit,
) {
    val repo = remember { ChatRepository() }
    val myUid = repo.myUid.orEmpty()
    val messages by remember(peerUid) { repo.observeMessages(peerUid) }.collectAsState(initial = emptyList())
    // Live presence for the peer → real "Active now" / "Active Xm ago" instead of a fixed label.
    val presence by remember(peerUid) {
        com.gising.data.repository.PresenceRepository().observe(peerUid)
    }.collectAsState(initial = com.gising.data.repository.Presence())
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // "Download conversation" — export the current transcript to a user-picked .txt via the Storage
    // Access Framework (no storage permission needed, works fully offline). The messages are already
    // in memory here, so the export is just a formatted dump of [messages].
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(buildTranscript(peerName, myUid, messages).toByteArray())
                }
            }.isSuccess
            android.widget.Toast.makeText(
                context,
                if (ok) "Conversation saved" else "Couldn't save conversation",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    var draft by remember { mutableStateOf("") }

    // Keep the newest message in view as the conversation grows / keyboard opens.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(SeismicHot.Base)
            .imePadding(),
    ) {
        ChatTopBar(
            peerName = peerName,
            statusLabel = presence.label(),
            isActive = presence.isActiveNow(),
            onBack = onBack,
            onDownload = {
                val safeName = peerName.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "chat" }
                exportLauncher.launch("SeismicWatch chat - $safeName.txt")
            },
        )

        // ── Message log ──
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (messages.isEmpty()) {
                item { EmptyConversation(peerName) }
            }
            itemsIndexed(messages, key = { i, m -> m.id.ifEmpty { "idx_$i" } }) { i, msg ->
                val prev = messages.getOrNull(i - 1)
                val next = messages.getOrNull(i + 1)
                val mine = msg.senderUid == myUid

                // Day separator when the calendar day changes.
                if (prev == null || !sameDay(prev.sentAt, msg.sentAt)) {
                    DaySeparator(msg.sentAt)
                }

                // Last bubble in a same-sender run shows the timestamp.
                val runEnds = next == null || next.senderUid != msg.senderUid ||
                    !sameDay(next.sentAt, msg.sentAt)

                MessageBubble(
                    text = msg.text,
                    mine = mine,
                    time = if (runEnds) timeLabel(msg.sentAt) else null,
                )
            }
        }

        ChatInputBar(
            value = draft,
            onValueChange = { draft = it },
            onSend = {
                val text = draft.trim()
                if (text.isNotEmpty()) {
                    draft = ""
                    scope.launch { runCatching { repo.sendMessage(peerUid, text) } }
                }
            },
        )
    }
}

// ─── Top bar ─────────────────────────────────────────────────────────────────
@Composable
private fun ChatTopBar(
    peerName: String,
    statusLabel: String,
    isActive: Boolean,
    onBack: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SeismicHot.Nav)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = SeismicHot.White, modifier = Modifier.size(22.dp))
        }
        InitialsAvatar(peerName, size = 38.dp)
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                peerName,
                color = SeismicHot.White,
                fontFamily = SeismicFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(SeismicHot.Safe))
                    Spacer(Modifier.size(5.dp))
                }
                Text(
                    statusLabel,
                    color = if (isActive) SeismicHot.Safe else SeismicHot.Muted,
                    fontSize = 11.sp,
                    fontFamily = SeismicFont,
                )
            }
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onDownload),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.FileDownload,
                "Download conversation",
                tint = SeismicHot.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(SeismicHot.Border))
}

// ─── Bubble ──────────────────────────────────────────────────────────────────
@Composable
private fun MessageBubble(text: String, mine: Boolean, time: String?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (mine) 18.dp else 5.dp,
                        bottomEnd = if (mine) 5.dp else 18.dp,
                    )
                )
                .then(
                    if (mine) Modifier.background(SeismicHot.Gradient)
                    else Modifier.background(SeismicHot.Card).border(
                        1.dp, SeismicHot.Border,
                        RoundedCornerShape(
                            topStart = 18.dp, topEnd = 18.dp, bottomStart = 5.dp, bottomEnd = 18.dp,
                        ),
                    )
                )
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(
                text,
                // My bubble sits on the saturated gradient → always light text, even on light themes.
                color = if (mine) Color.White else SeismicHot.White,
                fontSize = 14.5.sp,
                fontFamily = SeismicFont,
            )
        }
        if (time != null) {
            Text(
                time,
                color = SeismicHot.Muted,
                fontSize = 10.sp,
                fontFamily = SeismicFont,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

// ─── Input bar ───────────────────────────────────────────────────────────────
@Composable
private fun ChatInputBar(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SeismicHot.Nav)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp)),
            placeholder = { Text("Message…", color = SeismicHot.Muted, fontFamily = SeismicFont) },
            textStyle = LocalTextStyle.current.copy(fontFamily = SeismicFont, fontSize = 15.sp),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SeismicHot.Field,
                unfocusedContainerColor = SeismicHot.Field,
                disabledContainerColor = SeismicHot.Field,
                focusedTextColor = SeismicHot.White,
                unfocusedTextColor = SeismicHot.White,
                cursorColor = SeismicHot.Red,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
        val canSend = value.isNotBlank()
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .then(
                    if (canSend) Modifier.background(SeismicHot.Gradient)
                    else Modifier.background(SeismicHot.Field)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = canSend,
                    onClick = onSend,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Send,
                "Send",
                tint = if (canSend) SeismicHot.White else SeismicHot.Muted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ─── Pieces ──────────────────────────────────────────────────────────────────
@Composable
private fun DaySeparator(date: Date?) {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(
            dayLabel(date),
            color = SeismicHot.Muted,
            fontSize = 11.sp,
            fontFamily = SeismicFont,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EmptyConversation(peerName: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InitialsAvatar(peerName, size = 72.dp)
        Spacer(Modifier.height(14.dp))
        Text(peerName, color = SeismicHot.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, fontFamily = SeismicFont)
        Spacer(Modifier.height(6.dp))
        Text(
            "Say hello — this conversation is private and synced across your devices.",
            color = SeismicHot.Label,
            fontSize = 13.sp,
            fontFamily = SeismicFont,
            modifier = Modifier.widthIn(max = 280.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun InitialsAvatar(name: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(SeismicHot.Gradient),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initialsOf(name),
            color = SeismicHot.White,
            fontWeight = FontWeight.Bold,
            fontFamily = SeismicFont,
            fontSize = (size.value * 0.38f).sp,
        )
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────
/** Formats the in-memory conversation as a plain-text transcript for offline download. */
private fun buildTranscript(peerName: String, myUid: String, messages: List<ChatMessage>): String {
    val stamp = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    val header = "SeismicWatch — conversation with $peerName\n" +
        "Exported ${stamp.format(Date())}\n" +
        "${messages.size} message(s)\n" +
        "────────────────────────────────────────\n\n"
    val body = messages.joinToString("\n") { m ->
        val who = if (m.senderUid == myUid) "You" else peerName
        val time = m.sentAt?.let { stamp.format(it) } ?: "Sending…"
        "[$time] $who: ${m.text}"
    }
    return header + body + "\n"
}

private fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifBlank { "?" }

private fun sameDay(a: Date?, b: Date?): Boolean {
    if (a == null || b == null) return a == null && b == null
    val ca = Calendar.getInstance().apply { time = a }
    val cb = Calendar.getInstance().apply { time = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

private fun timeLabel(date: Date?): String =
    date?.let { SimpleDateFormat("h:mm a", Locale.getDefault()).format(it) } ?: ""

private fun dayLabel(date: Date?): String {
    date ?: return "Sending…"
    val today = Calendar.getInstance()
    val that = Calendar.getInstance().apply { time = date }
    val yest = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDayCal(today, that) -> "Today"
        sameDayCal(yest, that) -> "Yesterday"
        today.get(Calendar.YEAR) == that.get(Calendar.YEAR) ->
            SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
    }
}

private fun sameDayCal(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
