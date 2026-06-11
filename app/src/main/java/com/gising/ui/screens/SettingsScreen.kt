package com.gising.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cyclone
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.gising.ui.screens.account.AccountViewModel
import com.gising.ui.screens.account.ProfileViewModel
import com.gising.ui.theme.SeismicBlinkingDot
import com.gising.ui.theme.SeismicDisplayFont
import com.gising.ui.theme.SeismicFont
import com.gising.ui.theme.SeismicHot
import com.gising.ui.theme.seismicEntrance
import com.gising.ui.theme.seismicTacticalBackground

/**
 * Settings — "SeismicWatch · Cyber-Urgency". A grouped, card-based settings surface on the tactical dark
 * ground: profile header, per-hazard notification toggles, monitored regions, emergency contacts,
 * editable personal info, app preferences, advanced safety controls, and about/help — all wired to
 * the real DataStore prefs, FirebaseAuth profile, and emergency-contacts DB.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel(),
    accountViewModel: AccountViewModel = viewModel(),
    contactsViewModel: ContactsViewModel = viewModel(),
    onOpenProfile: () -> Unit = {},
    onOpenPeople: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsState()
    val profile by profileViewModel.ui.collectAsState()
    val contacts by contactsViewModel.contacts.collectAsState()
    val context = LocalContext.current

    var showMeshDisclosure by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var thresholdOpen by remember { mutableStateOf(false) }

    // Editable personal-info fields, seeded from the real sources.
    var fullName by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var mobile by remember(settings.mobileNumber) { mutableStateOf(settings.mobileNumber) }
    var address by remember(settings.homeAddress) { mutableStateOf(settings.homeAddress) }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val accountMessage by accountViewModel.message.collectAsState()
    LaunchedEffect(accountMessage) {
        accountMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            accountViewModel.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize().seismicTacticalBackground()) {
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = SeismicFont)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding(),
            ) {
                Spacer(Modifier.height(14.dp))
                Header(modifier = Modifier.seismicEntrance(visible, 0))
                Spacer(Modifier.height(18.dp))

                ProfileCard(
                    name = profile.displayName.ifBlank { "Your profile" },
                    initial = profile.initial,
                    photoUrl = profile.photoUrl,
                    contact = listOfNotNull(
                        profile.email.ifBlank { null },
                        settings.mobileNumber.ifBlank { null },
                    ).joinToString(" · ").ifBlank { "Tap edit to complete your profile" },
                    onEdit = onOpenProfile,
                    modifier = Modifier.seismicEntrance(visible, 40),
                )
                Spacer(Modifier.height(22.dp))

                // ── Notification preferences ──────────────────────────────────
                SectionLabel("NOTIFICATION PREFERENCES", Modifier.seismicEntrance(visible, 80))
                SettingsCard(Modifier.seismicEntrance(visible, 100)) {
                    ToggleRow(Icons.Outlined.GraphicEq, SeismicHot.Red, "Earthquake Alerts", "PHIVOLCS real-time seismic events", settings.alertEarthquake, viewModel::setAlertEarthquake)
                    RowDivider()
                    ToggleRow(Icons.Outlined.Cyclone, SeismicHot.Orange, "Typhoon Warnings", "PAGASA storm signals & advisories", settings.alertTyphoon, viewModel::setAlertTyphoon)
                    RowDivider()
                    ToggleRow(Icons.Outlined.WaterDrop, SeismicHot.Signal, "Flood Advisories", "NDRRMC flood & landslide warnings", settings.alertFlood, viewModel::setAlertFlood)
                    RowDivider()
                    ToggleRow(Icons.Outlined.NotificationsActive, SeismicHot.Red, "Sound & Vibration", "Alert sound & haptic feedback", settings.enableSound && settings.enableVibration, viewModel::setSoundVibration)
                    RowDivider()
                    NavRow(
                        Icons.Outlined.NotificationsActive, SeismicHot.Red,
                        "Emergency Override",
                        "Force max volume & break silent/DND for severe alerts",
                        tint = SeismicHot.Red,
                    ) {
                        runCatching {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))

                // ── Monitored regions ─────────────────────────────────────────
                // ── Emergency contacts ────────────────────────────────────────
                SectionLabel("EMERGENCY CONTACTS", Modifier.seismicEntrance(visible, 200))
                SettingsCard(Modifier.seismicEntrance(visible, 220)) {
                    if (contacts.isEmpty()) {
                        NavRow(Icons.Outlined.Add, SeismicHot.Red, "Add Emergency Contact", "Up to 5 contacts can be notified", tint = SeismicHot.Red, onClick = onOpenPeople)
                    } else {
                        contacts.forEachIndexed { i, c ->
                            if (i > 0) RowDivider()
                            ContactRow(
                                name = c.name,
                                detail = listOfNotNull(c.relationship.ifBlank { null }, c.phone.ifBlank { null }).joinToString(" · "),
                                avatar = AVATAR_COLORS[i % AVATAR_COLORS.size],
                                onCall = { dial(context, c.phone) },
                            )
                        }
                        RowDivider()
                        NavRow(Icons.Outlined.Add, SeismicHot.Red, "Add Emergency Contact", "Up to 5 contacts can be notified", tint = SeismicHot.Red, onClick = onOpenPeople)
                    }
                }
                Spacer(Modifier.height(22.dp))

                // ── Personal information ──────────────────────────────────────
                SectionLabel("PERSONAL INFORMATION", Modifier.seismicEntrance(visible, 260))
                SettingsCard(Modifier.seismicEntrance(visible, 280)) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SeismicInput("FULL NAME", fullName, { fullName = it }, "Your name")
                        SeismicInput("MOBILE NUMBER", mobile, { mobile = it }, "+63 9XX XXX XXXX", KeyboardType.Phone)
                        SeismicInput("HOME ADDRESS", address, { address = it }, "City, Province")
                        SaveCta(
                            saving = profile.saving,
                            onClick = {
                                profileViewModel.onNameChange(fullName)
                                profileViewModel.save()
                                viewModel.setMobileNumber(mobile)
                                viewModel.setHomeAddress(address)
                                Toast.makeText(context, "Changes saved", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))

                // ── App preferences ───────────────────────────────────────────
                SectionLabel("APP PREFERENCES", Modifier.seismicEntrance(visible, 320))
                SettingsCard(Modifier.seismicEntrance(visible, 340)) {
                    NavRow(Icons.Outlined.Tune, SeismicHot.Red, "Alert Threshold", "Magnitude ${"%.1f".format(settings.minMagnitude)}+ · Signal 2+", onClick = { thresholdOpen = !thresholdOpen })
                    AnimatedVisibility(thresholdOpen, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        ThresholdSlider(value = settings.minMagnitude.toFloat(), onValue = { viewModel.setMinMagnitude(it.toDouble()) })
                    }
                    RowDivider()
                    NavRow(Icons.Outlined.Translate, SeismicHot.Orange, "Language", "English (Philippines)", onClick = {
                        Toast.makeText(context, "More languages are coming soon.", Toast.LENGTH_SHORT).show()
                    })
                    RowDivider()
                    ToggleRow(Icons.Outlined.Sync, SeismicHot.Red, "Background Sync", "Keep alerts up-to-date always", settings.backgroundSync, viewModel::setBackgroundSync)
                }
                Spacer(Modifier.height(22.dp))

                // ── Appearance ────────────────────────────────────────────────
                SectionLabel("APPEARANCE", Modifier.seismicEntrance(visible, 348))
                SettingsCard(Modifier.seismicEntrance(visible, 352)) {
                    ThemePickerRow(
                        selected = com.gising.ui.theme.AppTheme.fromKey(settings.appTheme),
                        onSelect = viewModel::setAppTheme,
                    )
                    RowDivider()
                    ThemeModeRow(
                        selected = com.gising.ui.theme.ThemeMode.fromKey(settings.themeMode),
                        onSelect = viewModel::setThemeMode,
                    )
                }
                Spacer(Modifier.height(22.dp))

                // ── Safety & emergency (advanced — preserved) ─────────────────
                SectionLabel("SAFETY & EMERGENCY", Modifier.seismicEntrance(visible, 360))
                SettingsCard(Modifier.seismicEntrance(visible, 380)) {
                    ToggleRow(Icons.Outlined.FlashOn, SeismicHot.Orange, "Flashlight Strobe", "Flashes the camera LED on M5+ alerts", settings.enableFlashlight, viewModel::setFlashlight)
                    RowDivider()
                    ToggleRow(Icons.Outlined.Sms, SeismicHot.Red, "SMS Failback", "Always text contacts your coordinates", settings.smsFailback, viewModel::setSmsFailback)
                    RowDivider()
                    ToggleRow(Icons.Outlined.Hub, SeismicHot.Signal, "Offline Mesh Relay", "Forward nearby SOS when towers are down", settings.meshRelay, onToggle = {
                        if (it) showMeshDisclosure = true else viewModel.setMeshRelay(false)
                    })
                    RowDivider()
                    ToggleRow(Icons.Outlined.TouchApp, SeismicHot.Orange, "Power-button Panic", "Press power 5× fast to fire an SOS", settings.panicGestures, viewModel::setPanicGestures)
                }
                Spacer(Modifier.height(22.dp))

                // ── About & help ──────────────────────────────────────────────
                SectionLabel("ABOUT & HELP", Modifier.seismicEntrance(visible, 400))
                SettingsCard(Modifier.seismicEntrance(visible, 420)) {
                    NavRow(Icons.Outlined.HelpOutline, SeismicHot.Red, "Help Center", "FAQs and user guides", onClick = { openUrl(context, HELP_URL) })
                    RowDivider()
                    NavRow(Icons.Outlined.Storage, SeismicHot.Orange, "Data Sources", "PHIVOLCS · PAGASA · NDRRMC", onClick = {})
                    RowDivider()
                    NavRow(Icons.Outlined.Shield, SeismicHot.Red, "Privacy Policy", "How we handle your data", onClick = { openUrl(context, PRIVACY_POLICY_URL) })
                    RowDivider()
                    NavRow(Icons.Outlined.Info, SeismicHot.Muted, "App Version", "Up to date", trailingText = "v${com.gising.BuildConfig.VERSION_NAME}", onClick = {})
                }
                Spacer(Modifier.height(20.dp))

                SignOutButton(onClick = { accountViewModel.signOut() }, modifier = Modifier.seismicEntrance(visible, 440))
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Delete account & all data", color = SeismicHot.Muted, fontSize = 12.sp, fontFamily = SeismicFont)
                }

                Spacer(Modifier.height(110.dp))
            }
        }
    }

    if (showMeshDisclosure) {
        com.gising.ui.screens.connections.NearbyMeshDisclosureDialog(
            onAccept = { showMeshDisclosure = false; viewModel.setMeshRelay(true) },
            onDecline = { showMeshDisclosure = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete everything?") },
            text = { Text("This permanently deletes your account, removes you from all circles, and erases your location data. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; accountViewModel.deleteAccount() }) { Text("Delete", color = SeismicHot.Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────
@Composable
private fun Header(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Settings", fontFamily = SeismicDisplayFont, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, color = SeismicHot.White)
        }
    }
}

// ─── Profile ─────────────────────────────────────────────────────────────────
@Composable
private fun ProfileCard(name: String, initial: String, photoUrl: String, contact: String, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SeismicHot.Card)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 52px avatar with a gradient ring.
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(SeismicHot.Gradient).padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(SeismicHot.Card), contentAlignment = Alignment.Center) {
                if (photoUrl.isNotBlank()) {
                    AsyncImage(model = photoUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
                } else {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(SeismicHot.Gradient), contentAlignment = Alignment.Center) {
                        Text(initial, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
                    }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(contact, fontSize = 12.sp, color = SeismicHot.Muted, maxLines = 2)
        }
        ScaleButton(onClick = onEdit) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(SeismicHot.Red.copy(alpha = 0.12f)).border(1.dp, SeismicHot.Red.copy(alpha = 0.25f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Edit, "Edit profile", tint = SeismicHot.Red, modifier = Modifier.size(17.dp)) }
        }
    }
}

// ─── Cards & rows ────────────────────────────────────────────────────────────
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 24.dp, bottom = 10.dp, top = 0.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        color = SeismicHot.Muted,
    )
}

@Composable
private fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(20.dp), spotColor = Color.Black, ambientColor = Color.Black)
            .clip(RoundedCornerShape(20.dp))
            .background(SeismicHot.Card)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        content = content,
    )
}

/** Inset divider — starts past the 36dp icon box so it aligns under the text. */
@Composable
private fun RowDivider() {
    Box(Modifier.padding(start = 50.dp).fillMaxWidth().height(1.dp).background(SeismicHot.Border))
}

@Composable
private fun ToggleRow(icon: ImageVector, accent: Color, title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBox(icon, accent)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
            Text(subtitle, fontSize = 11.5.sp, color = SeismicHot.Muted, maxLines = 1)
        }
        GradientToggle(checked, onToggle)
    }
}

@Composable
private fun NavRow(icon: ImageVector, accent: Color, title: String, subtitle: String, tint: Color = SeismicHot.White, trailingText: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBox(icon, accent)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tint)
            Text(subtitle, fontSize = 11.5.sp, color = SeismicHot.Muted, maxLines = 1)
        }
        if (trailingText != null) {
            Box(Modifier.clip(RoundedCornerShape(7.dp)).background(SeismicHot.Red.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 4.dp)) {
                Text(trailingText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SeismicHot.Label)
            }
        } else {
            Icon(Icons.Outlined.ChevronRight, null, tint = SeismicHot.Muted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ContactRow(name: String, detail: String, avatar: Color, onCall: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(avatar), contentAlignment = Alignment.Center) {
            Text(name.trim().firstOrNull()?.uppercase() ?: "?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
        }
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White, maxLines = 1)
            Text(detail, fontSize = 11.5.sp, color = SeismicHot.Muted, maxLines = 1)
        }
        ScaleButton(onClick = onCall) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(SeismicHot.Red.copy(alpha = 0.12f)).border(1.dp, SeismicHot.Red.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Call, "Call", tint = SeismicHot.Red, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun IconBox(icon: ImageVector, accent: Color) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp)) }
}

// ─── Inputs ──────────────────────────────────────────────────────────────────
@Composable
private fun SeismicInput(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, keyboardType: KeyboardType = KeyboardType.Text) {
    Column {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = SeismicHot.Label)
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SeismicHot.Field)
                .border(1.5.dp, SeismicHot.Border, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            if (value.isEmpty()) Text(placeholder, color = SeismicHot.Muted, fontSize = 14.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = SeismicHot.White, fontSize = 14.sp, fontFamily = SeismicFont),
                cursorBrush = Brush.linearGradient(listOf(SeismicHot.Red, SeismicHot.Red)),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ThresholdSlider(value: Float, onValue: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 50.dp, end = 4.dp, bottom = 10.dp)) {
        Text("Alert fires at M${"%.1f".format(value)} and above", fontSize = 11.5.sp, color = SeismicHot.Label)
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = 1f..8f,
            steps = 13,
            colors = SliderDefaults.colors(thumbColor = SeismicHot.Red, activeTrackColor = SeismicHot.Red, inactiveTrackColor = SeismicHot.Border),
        )
    }
}

// ─── Buttons & toggle ────────────────────────────────────────────────────────
@Composable
private fun GradientToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val offset by animateDpAsState(if (checked) 18.dp else 0.dp, label = "thumb")
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(26.dp)
            .clip(CircleShape)
            .then(
                if (checked) Modifier.shadow(10.dp, CircleShape, spotColor = SeismicHot.Red, ambientColor = SeismicHot.Red).background(SeismicHot.Gradient)
                else Modifier.background(Color(0xFF2E1520)).border(1.5.dp, Color(0xFF3D1E28), CircleShape)
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = offset).size(20.dp).clip(CircleShape).background(SeismicHot.White))
    }
}

@Composable
private fun SaveCta(saving: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "saveScale")
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .fillMaxWidth()
            .height(50.dp)
            .shadow(20.dp, RoundedCornerShape(14.dp), spotColor = SeismicHot.Red, ambientColor = SeismicHot.Orange)
            .clip(RoundedCornerShape(14.dp))
            .background(SeismicHot.Gradient)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent)), RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Shield, null, tint = SeismicHot.White, modifier = Modifier.size(18.dp))
            Text(if (saving) "Saving…" else "Save Changes", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SeismicHot.White)
        }
    }
}

@Composable
private fun SignOutButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SeismicHot.Red.copy(alpha = 0.08f))
            .border(1.dp, SeismicHot.Red.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Logout, null, tint = SeismicHot.Red, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("Sign Out", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SeismicHot.Red)
    }
}

@Composable
private fun ScaleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "scaleBtn")
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) { content() }
}

// ─── Appearance: theme + mode pickers ─────────────────────────────────────────
@Composable
private fun ThemePickerRow(
    selected: com.gising.ui.theme.AppTheme,
    onSelect: (com.gising.ui.theme.AppTheme) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text("Color Theme", color = SeismicHot.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = SeismicFont)
        Text(selected.displayName, color = SeismicHot.Label, fontSize = 11.sp, fontFamily = SeismicFont)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.gising.ui.theme.AppTheme.entries.forEach { theme ->
                val p = com.gising.ui.theme.SeismicPalettes.paletteFor(theme, dark = true)
                val isSel = theme == selected
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(p.accent, p.accentAlt)))
                        .border(
                            width = if (isSel) 2.5.dp else 1.dp,
                            color = if (isSel) SeismicHot.White else SeismicHot.Border,
                            shape = CircleShape,
                        )
                        .clickable { onSelect(theme) },
                )
            }
        }
    }
}

@Composable
private fun ThemeModeRow(
    selected: com.gising.ui.theme.ThemeMode,
    onSelect: (com.gising.ui.theme.ThemeMode) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Mode", Modifier.weight(1f), color = SeismicHot.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = SeismicFont)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.gising.ui.theme.ThemeMode.entries.forEach { mode ->
                val isSel = mode == selected
                val label = when (mode) {
                    com.gising.ui.theme.ThemeMode.SYSTEM -> "System"
                    com.gising.ui.theme.ThemeMode.LIGHT -> "Light"
                    com.gising.ui.theme.ThemeMode.DARK -> "Dark"
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) SeismicHot.Red.copy(alpha = 0.15f) else Color.Transparent)
                        .border(1.dp, if (isSel) SeismicHot.Red else SeismicHot.Border, RoundedCornerShape(10.dp))
                        .clickable { onSelect(mode) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(label, color = if (isSel) SeismicHot.Red else SeismicHot.Label, fontSize = 12.sp, fontFamily = SeismicFont)
                }
            }
        }
    }
}

// ─── Data & helpers ──────────────────────────────────────────────────────────
private val AVATAR_COLORS = listOf(
    Color(0xFFFF2D55), Color(0xFFFF6A00), Color(0xFF22C55E), Color(0xFF4D7CFE), Color(0xFF9B5DE5),
)

private const val PRIVACY_POLICY_URL = "https://gising.app/privacy"
private const val HELP_URL = "https://gising.app/help"

private fun dial(context: android.content.Context, phone: String) {
    if (phone.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
