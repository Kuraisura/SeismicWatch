package com.gising.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gising.data.model.EmergencyContact
import com.gising.service.AlertDispatcher
import com.gising.ui.theme.SeismicDisplayFont
import com.gising.ui.theme.SeismicFont
import com.gising.ui.theme.SeismicHot

/**
 * Emergency contacts — the "Alerts" half of the People hub, restyled in the SeismicWatch
 * "Tactical Vibrant" system (obsidian cards, red→orange accents, Clash/Satoshi type). This is
 * the primary "add a person" surface: save family numbers, choose alert channels, test the setup.
 */
@Composable
fun ContactsScreen(viewModel: ContactsViewModel) {
    val contacts by viewModel.contacts.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    var editing by remember { mutableStateOf<EmergencyContact?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val sampleMessage = AlertDispatcher.buildMessage(
        magnitude = 6.1, place = "Test Province", userLat = 14.5995, userLon = 120.9842,
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Intro
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SeismicHot.Red.copy(alpha = 0.10f))
                        .border(1.dp, SeismicHot.Red.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(SeismicHot.Red.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Diversity3, null, tint = SeismicHot.Red, modifier = Modifier.size(20.dp)) }
                    Text(
                        "When a strong quake hits, SeismicWatch can text your family your location " +
                            "automatically, auto-call your top contact, and let you blast Messenger " +
                            "or WhatsApp with one tap.",
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = SeismicHot.White.copy(alpha = 0.85f),
                    )
                }
            }

            // Channel toggles
            item {
                TacticalCard {
                    ToggleRow(
                        icon = Icons.Outlined.NotificationsActive,
                        title = "Family alerts",
                        subtitle = "Master switch for all family notifications",
                        checked = settings.familyAlerts, onToggle = viewModel::setFamilyAlerts,
                    )
                    Divider()
                    ToggleRow(
                        icon = Icons.Outlined.Sms,
                        title = "Auto-SMS",
                        subtitle = "Text everyone automatically when a quake fires",
                        checked = settings.autoSms, onToggle = viewModel::setAutoSms, enabled = settings.familyAlerts,
                    )
                    Divider()
                    ToggleRow(
                        icon = Icons.Outlined.Call,
                        title = "Auto-call top contact",
                        subtitle = "Dial your primary contact on a major quake",
                        checked = settings.autoCall, onToggle = viewModel::setAutoCall, enabled = settings.familyAlerts,
                    )
                }
            }

            item { GroupLabel("SAVED CONTACTS") }

            if (contacts.isEmpty()) {
                item {
                    Text(
                        "No contacts yet. Tap the + button to save your family's numbers.",
                        fontSize = 13.sp,
                        color = SeismicHot.Muted,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            } else {
                items(contacts, key = { it.id }) { contact ->
                    ContactRow(
                        contact = contact,
                        onEdit = { editing = contact; showDialog = true },
                        onDelete = { viewModel.deleteContact(contact) },
                        onMakePrimary = { viewModel.makePrimary(contact) },
                    )
                }
            }

            // Test channels
            item {
                Spacer(Modifier.height(2.dp))
                GroupLabel("TEST YOUR SETUP")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TestButton("SMS", Icons.Outlined.Sms, Modifier.weight(1f)) {
                        AlertDispatcher.composeSms(context, contacts, sampleMessage)
                    }
                    TestButton("Messenger", Icons.Outlined.Send, Modifier.weight(1f)) {
                        AlertDispatcher.shareToMessenger(context, sampleMessage)
                    }
                    TestButton("Share", Icons.Outlined.Share, Modifier.weight(1f)) {
                        AlertDispatcher.shareGeneric(context, sampleMessage)
                    }
                }
            }
        }

        // Gradient "add contact" FAB.
        ScaleButton(
            onClick = { editing = null; showDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 4.dp, bottom = 20.dp),
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

    if (showDialog) {
        ContactEditDialog(
            existing = editing,
            onDismiss = { showDialog = false },
            onSave = { contact ->
                if (editing == null) viewModel.addContact(contact) else viewModel.updateContact(contact)
                showDialog = false
            },
        )
    }
}

// ─── Building blocks ──────────────────────────────────────────────────────────
@Composable
private fun TacticalCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SeismicHot.Card)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(SeismicHot.Border.copy(alpha = 0.6f)))
}

@Composable
private fun GroupLabel(text: String) {
    Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = SeismicHot.Muted)
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .background(if (checked && enabled) SeismicHot.Red.copy(alpha = 0.15f) else SeismicHot.Field),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (checked && enabled) SeismicHot.Red else SeismicHot.Muted)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (enabled) SeismicHot.White else SeismicHot.Muted)
            Text(subtitle, fontSize = 11.5.sp, color = SeismicHot.Muted, lineHeight = 15.sp)
        }
        TacticalToggle(checked = checked, enabled = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun TacticalToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val offset by animateDpAsState(if (checked) 18.dp else 0.dp, label = "thumb")
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(26.dp)
            .clip(CircleShape)
            .then(
                if (checked && enabled)
                    Modifier.shadow(10.dp, CircleShape, spotColor = SeismicHot.Red, ambientColor = SeismicHot.Red).background(SeismicHot.Gradient)
                else Modifier.background(Color(0xFF2E1520)).border(1.5.dp, Color(0xFF3D1E28), CircleShape)
            )
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) { onCheckedChange(!checked) } else Modifier
            )
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = offset).size(20.dp).clip(CircleShape).background(if (enabled) SeismicHot.White else SeismicHot.Muted))
    }
}

@Composable
private fun ContactRow(
    contact: EmergencyContact,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMakePrimary: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SeismicHot.Card)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(SeismicHot.Gradient),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                contact.name.take(1).uppercase().ifBlank { "?" },
                fontFamily = SeismicDisplayFont, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(contact.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
                if (contact.isPrimary) {
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp)).background(SeismicHot.Red.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp),
                    ) { Text("TOP", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = SeismicHot.Red) }
                }
            }
            Text(
                buildString {
                    append(contact.phone)
                    if (contact.relationship.isNotBlank()) append(" · ${contact.relationship}")
                },
                fontSize = 12.sp, color = SeismicHot.Muted,
            )
        }
        if (!contact.isPrimary) {
            IconChip(Icons.Outlined.StarBorder, "Make top contact", SeismicHot.Muted, onMakePrimary)
        }
        IconChip(Icons.Outlined.Edit, "Edit", SeismicHot.Muted, onEdit)
        IconChip(Icons.Outlined.Delete, "Delete", SeismicHot.Red, onDelete)
    }
}

@Composable
private fun IconChip(icon: ImageVector, desc: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(SeismicHot.Field).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun TestButton(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SeismicHot.Field)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = SeismicHot.Label, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
    }
}

// ─── Add / edit dialog ────────────────────────────────────────────────────────
@Composable
private fun ContactEditDialog(
    existing: EmergencyContact?,
    onDismiss: () -> Unit,
    onSave: (EmergencyContact) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var relationship by remember { mutableStateOf(existing?.relationship ?: "") }
    var messengerLink by remember { mutableStateOf(existing?.messengerLink ?: "") }
    var notifyBySms by remember { mutableStateOf(existing?.notifyBySms ?: true) }
    var isPrimary by remember { mutableStateOf(existing?.isPrimary ?: false) }

    val normalizedPhone = com.gising.util.PhoneUtils.normalize(phone)
    val phoneError = phone.isNotBlank() && normalizedPhone == null
    val canSave = name.isNotBlank() && normalizedPhone != null

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SeismicHot.Card)
                .border(1.dp, SeismicHot.Border, RoundedCornerShape(22.dp))
                .padding(20.dp),
        ) {
            Text(
                if (existing == null) "Add contact" else "Edit contact",
                fontFamily = SeismicDisplayFont, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp, color = SeismicHot.White,
            )
            Spacer(Modifier.height(4.dp))
            Text("Saved to your emergency circle", fontSize = 12.sp, color = SeismicHot.Muted)
            Spacer(Modifier.height(18.dp))

            TacticalField(
                value = name, onValueChange = { name = it },
                label = "Name", placeholder = "e.g. Mom",
            )
            Spacer(Modifier.height(12.dp))
            TacticalField(
                value = phone, onValueChange = { phone = it },
                label = "Phone", placeholder = "0917 123 4567",
                keyboardType = KeyboardType.Phone,
                isError = phoneError,
                helper = when {
                    phoneError -> "Enter a valid PH number (09… or +639…)"
                    normalizedPhone != null -> "Will send to $normalizedPhone"
                    else -> "Mobile or landline"
                },
            )
            Spacer(Modifier.height(12.dp))
            TacticalField(
                value = relationship, onValueChange = { relationship = it },
                label = "Relationship (optional)", placeholder = "e.g. Sister",
            )
            Spacer(Modifier.height(12.dp))
            TacticalField(
                value = messengerLink, onValueChange = { messengerLink = it },
                label = "Messenger link (optional)", placeholder = "m.me/…",
            )
            Spacer(Modifier.height(16.dp))

            CheckRow("Include in auto-SMS", notifyBySms) { notifyBySms = it }
            Spacer(Modifier.height(10.dp))
            CheckRow("Top contact (auto-called)", isPrimary) { isPrimary = it }

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SeismicHot.Field)
                        .border(1.dp, SeismicHot.Border, RoundedCornerShape(14.dp))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SeismicHot.Muted) }
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .then(
                            if (canSave) Modifier.shadow(16.dp, RoundedCornerShape(14.dp), spotColor = SeismicHot.Red, ambientColor = SeismicHot.Orange)
                            else Modifier
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (canSave) SeismicHot.Gradient else SolidColor(SeismicHot.Field))
                        .clickable(enabled = canSave) {
                            onSave(
                                (existing ?: EmergencyContact(name = "", phone = "")).copy(
                                    name = name.trim(),
                                    phone = normalizedPhone!!,
                                    relationship = relationship.trim(),
                                    messengerLink = messengerLink.trim(),
                                    notifyBySms = notifyBySms,
                                    isPrimary = isPrimary,
                                )
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) { Text("Save", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (canSave) SeismicHot.White else SeismicHot.Muted) }
            }
        }
    }
}

@Composable
private fun TacticalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    helper: String? = null,
) {
    Column {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, color = SeismicHot.Label)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SeismicHot.Field)
                .border(1.5.dp, if (isError) SeismicHot.Red else SeismicHot.Border, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) Text(placeholder, color = SeismicHot.Muted, fontSize = 14.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = SeismicHot.White, fontSize = 14.sp, fontFamily = SeismicFont),
                cursorBrush = SolidColor(SeismicHot.Red),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (helper != null) {
            Spacer(Modifier.height(5.dp))
            Text(helper, fontSize = 10.5.sp, color = if (isError) SeismicHot.Red else SeismicHot.Muted)
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
        ) { onToggle(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .then(
                    if (checked) Modifier.background(SeismicHot.Gradient)
                    else Modifier.background(SeismicHot.Field).border(1.5.dp, SeismicHot.Border, RoundedCornerShape(7.dp))
                ),
            contentAlignment = Alignment.Center,
        ) { if (checked) Icon(Icons.Outlined.Check, null, tint = SeismicHot.White, modifier = Modifier.size(15.dp)) }
        Text(label, fontSize = 13.sp, color = SeismicHot.White)
    }
}

@Composable
private fun ScaleButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "scaleBtn")
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) { content() }
}
