package com.gising.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gising.ui.screens.account.ProfileViewModel
import com.gising.ui.theme.SeismicBlinkingDot
import com.gising.ui.theme.SeismicDisplayFont
import com.gising.ui.theme.SeismicFont
import com.gising.ui.theme.SeismicHot
import com.gising.ui.theme.seismicEntrance
import com.gising.ui.theme.seismicTacticalBackground

/**
 * Editable profile — "SeismicWatch · Tactical Vibrant".
 *
 * Reached from the home brand mark. Restyled to the same obsidian / red→orange ground used by Auth,
 * Home and Reports: a seismic grid + ember glow, Clash Grotesk titles + Satoshi body, a gradient
 * avatar ring with a camera badge, inset tactical fields, and a glowing gradient save CTA. Email is
 * read-only (it's the account key); saving propagates to Auth, the private profile doc and circles.
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current

    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::onPhotoPicked) }

    LaunchedEffect(ui.savedAt) {
        if (ui.savedAt > 0L) {
            android.widget.Toast.makeText(context, "Profile saved", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(ui.error) {
        ui.error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val pickPhoto = {
        photoPicker.launch(
            androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .seismicTacticalBackground(),
        contentAlignment = Alignment.TopCenter,
    ) {
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = SeismicFont)) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── Top bar ─────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .seismicEntrance(visible, 0)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BackButton(onBack)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "Profile",
                            fontFamily = SeismicDisplayFont,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp,
                            color = SeismicHot.White,
                        )
                        Text(
                            ".",
                            fontFamily = SeismicDisplayFont,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = SeismicHot.Red,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ── Avatar with gradient ring + camera badge ────────────────────
                Box(
                    modifier = Modifier.seismicEntrance(visible, 60),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .size(116.dp)
                            .shadow(22.dp, CircleShape, spotColor = SeismicHot.Red, ambientColor = SeismicHot.Orange)
                            .clip(CircleShape)
                            .background(SeismicHot.Gradient)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(SeismicHot.Field)
                            .clickable(enabled = !ui.uploadingPhoto, onClick = pickPhoto),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            ui.uploadingPhoto ->
                                CircularProgressIndicator(Modifier.size(28.dp), color = SeismicHot.Red, strokeWidth = 2.dp)
                            ui.photoUrl.isNotBlank() ->
                                coil.compose.AsyncImage(
                                    model = ui.photoUrl,
                                    contentDescription = "Profile photo",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                )
                            else ->
                                Text(
                                    ui.initial,
                                    fontFamily = SeismicDisplayFont,
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SeismicHot.White,
                                )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SeismicHot.Base)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(SeismicHot.Gradient)
                            .clickable(enabled = !ui.uploadingPhoto, onClick = pickPhoto),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = "Change photo",
                            tint = SeismicHot.White,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    ui.displayName.ifBlank { "Your name" },
                    fontFamily = SeismicDisplayFont,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                    color = SeismicHot.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.seismicEntrance(visible, 100),
                )
                if (ui.email.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        ui.email,
                        fontSize = 13.sp,
                        color = SeismicHot.Muted,
                        modifier = Modifier.seismicEntrance(visible, 130),
                    )
                }

                Spacer(Modifier.height(12.dp))
                Box(Modifier.seismicEntrance(visible, 160)) { AccountPill() }

                Spacer(Modifier.height(30.dp))

                // ── Fields ──────────────────────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    TacticalField(
                        label = "DISPLAY NAME",
                        value = ui.displayName,
                        onValueChange = viewModel::onNameChange,
                        placeholder = "Your name",
                        leading = Icons.Outlined.Person,
                        capitalization = KeyboardCapitalization.Words,
                        modifier = Modifier.seismicEntrance(visible, 190),
                    )

                    Column(modifier = Modifier.seismicEntrance(visible, 220)) {
                        ReadonlyField(
                            label = "EMAIL",
                            value = ui.email,
                            leading = Icons.Outlined.Mail,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.Lock, null, tint = SeismicHot.Muted, modifier = Modifier.size(13.dp))
                            Text(
                                "Email is tied to your account and can't be changed here.",
                                fontSize = 11.sp,
                                color = SeismicHot.Muted,
                            )
                        }
                    }

                    Spacer(Modifier.height(2.dp))

                    GradientSaveButton(
                        loading = ui.saving,
                        onClick = viewModel::save,
                        modifier = Modifier.seismicEntrance(visible, 250),
                    )
                }

                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

// ─── Back button ───────────────────────────────────────────────────────────────
@Composable
private fun BackButton(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(SeismicHot.Card)
            .border(1.dp, SeismicHot.Border, RoundedCornerShape(13.dp))
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.ArrowBack, "Back", tint = SeismicHot.White, modifier = Modifier.size(20.dp))
    }
}

// ─── Account-active pill ─────────────────────────────────────────────────────────
@Composable
private fun AccountPill() {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(SeismicHot.Safe.copy(alpha = 0.12f))
            .border(1.dp, SeismicHot.Safe.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SeismicBlinkingDot(SeismicHot.Safe, 6.dp)
        Text(
            "ACCOUNT ACTIVE",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = SeismicHot.Safe,
        )
    }
}

// ─── Editable inset field ────────────────────────────────────────────────────────
@Composable
private fun TacticalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leading: ImageVector,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp, color = SeismicHot.Label)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(SeismicHot.Field)
                .border(1.5.dp, SeismicHot.Border, RoundedCornerShape(13.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(leading, null, tint = SeismicHot.Label, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, color = SeismicHot.Muted, fontSize = 15.sp)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = SeismicHot.White, fontSize = 15.sp, fontFamily = SeismicFont),
                    cursorBrush = Brush.linearGradient(listOf(SeismicHot.Red, SeismicHot.Orange)),
                    keyboardOptions = KeyboardOptions(capitalization = capitalization),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ─── Read-only field (email) ─────────────────────────────────────────────────────
@Composable
private fun ReadonlyField(
    label: String,
    value: String,
    leading: ImageVector,
) {
    Column {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp, color = SeismicHot.Muted)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(SeismicHot.Field.copy(alpha = 0.5f))
                .border(1.dp, SeismicHot.Border.copy(alpha = 0.6f), RoundedCornerShape(13.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(leading, null, tint = SeismicHot.Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                value.ifBlank { "—" },
                color = SeismicHot.Label,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ─── Gradient save CTA ───────────────────────────────────────────────────────────
@Composable
private fun GradientSaveButton(
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "saveScale")
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .fillMaxWidth()
            .height(54.dp)
            .shadow(24.dp, RoundedCornerShape(16.dp), spotColor = SeismicHot.Red, ambientColor = SeismicHot.Orange)
            .clip(RoundedCornerShape(16.dp))
            .background(SeismicHot.Gradient)
            .border(
                BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))),
                RoundedCornerShape(16.dp),
            )
            .clickable(interactionSource = interaction, indication = null, enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = SeismicHot.White, strokeWidth = 2.dp)
        } else {
            Text("Save Changes", color = SeismicHot.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.3.sp)
        }
    }
}
