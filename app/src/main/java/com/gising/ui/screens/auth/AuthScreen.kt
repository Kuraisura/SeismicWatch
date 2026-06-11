package com.gising.ui.screens.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gising.R
import com.gising.ui.theme.SeismicDisplayFont
import com.gising.ui.theme.SeismicFont
import kotlinx.coroutines.launch

/**
 * Sign-in / sign-up screen — "SeismicWatch · Urgency meets Precision".
 *
 * A self-contained dark design system: a deep black-violet ground with a seismic grid,
 * radial ember glows and a drifting scanline, a ripple-ring brand mark, a focused auth card
 * with custom inset fields, and a gradient CTA with a heavy double-layer glow. Every section
 * eases up into place with a staggered fade-up on first composition.
 *
 * The palette is intentionally local (not the app-wide teal theme) so this screen matches the
 * brand reference exactly without re-tinting the rest of the app.
 */

// Brand typefaces (Clash Grotesk display + Satoshi body) come from the shared theme:
// SeismicFont = Satoshi (body), SeismicDisplayFont = Clash Grotesk (titles / hero).

// ─── Local design tokens ─────────────────────────────────────────────────────
// Delegates to the shared, theme-aware SeismicHot tokens so Auth re-skins with the selected theme.
private object Ink {
    val Base: Color get() = com.gising.ui.theme.SeismicHot.Base
    val Card: Color get() = com.gising.ui.theme.SeismicHot.Card
    val Field: Color get() = com.gising.ui.theme.SeismicHot.Field
    val Border: Color get() = com.gising.ui.theme.SeismicHot.Border
    val Red: Color get() = com.gising.ui.theme.SeismicHot.Red
    val Orange: Color get() = com.gising.ui.theme.SeismicHot.Orange
    val Label: Color get() = com.gising.ui.theme.SeismicHot.Label
    val Inactive: Color get() = com.gising.ui.theme.SeismicHot.Muted
    val White: Color get() = com.gising.ui.theme.SeismicHot.White
    val Grid: Color get() = com.gising.ui.theme.SeismicHot.Grid

    // Brand ramp used for branding and CTAs (re-themes per palette).
    val Gradient: Brush get() = com.gising.ui.theme.SeismicHot.Gradient
}

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val state by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var passwordVisible by remember { mutableStateOf(false) }
    var keepSignedIn by remember { mutableStateOf(true) }

    // One-time entrance: flips true on first composition so each section eases up, staggered.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    LaunchedEffect(state.error, state.info) {
        val msg = state.error ?: state.info
        if (msg != null) {
            snackbarHost.showSnackbar(msg)
            viewModel.consumeMessages()
        }
    }

    val isSignUp = state.mode == AuthMode.SignUp

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = Ink.Base,
    ) { padding ->
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = SeismicFont)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .seismicBackground(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(48.dp))

                // ── Branding header ───────────────────────────────────────────
                Box(Modifier.entrance(visible, 0)) { RippleLogo() }

                Spacer(Modifier.height(18.dp))
                Text(
                    text = buildAnnotatedString {
                        append("Seismic")
                        withStyle(SpanStyle(brush = Ink.Gradient)) { append("Watch") }
                    },
                    fontFamily = SeismicDisplayFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    letterSpacing = (-1.5).sp,
                    color = Ink.White,
                    modifier = Modifier.entrance(visible, 70),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "EARTHQUAKE & TYPHOON MONITOR",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.4.sp,
                    color = Ink.Label,
                    modifier = Modifier.entrance(visible, 120),
                )

                Spacer(Modifier.height(16.dp))
                Box(Modifier.entrance(visible, 160)) { LiveStatusPill() }

                Spacer(Modifier.height(26.dp))

                // ── Auth card ─────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .entrance(visible, 210)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Ink.Card)
                        .border(1.dp, Ink.Border, RoundedCornerShape(16.dp))
                        .padding(18.dp),
                ) {
                    Text(
                        text = if (isSignUp) "Create account" else "Welcome back",
                        fontFamily = SeismicDisplayFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        letterSpacing = (-0.6).sp,
                        color = Ink.White,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = if (isSignUp)
                            "Join SeismicWatch to keep your family safe and informed."
                        else "Sign in to stay informed and safe.",
                        fontSize = 13.5.sp,
                        color = Ink.Inactive,
                    )

                    Spacer(Modifier.height(20.dp))

                    if (isSignUp) {
                        SeismicField(
                            label = "FULL NAME",
                            value = state.displayName,
                            onValueChange = viewModel::onDisplayNameChange,
                            placeholder = "Juan Dela Cruz",
                            leading = Icons.Filled.Person,
                            keyboardType = KeyboardType.Text,
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    SeismicField(
                        label = "EMAIL OR PHONE",
                        value = state.email,
                        onValueChange = viewModel::onEmailChange,
                        placeholder = "juan@example.com or +63 9XX...",
                        leading = Icons.Outlined.Email,
                        keyboardType = KeyboardType.Email,
                    )

                    Spacer(Modifier.height(16.dp))

                    SeismicField(
                        label = "PASSWORD",
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        placeholder = "Enter your password",
                        leading = Icons.Outlined.Lock,
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (passwordVisible)
                            VisualTransformation.None else PasswordVisualTransformation(),
                        trailingAction = if (!isSignUp) "Forgot password?" else null,
                        onTrailingActionClick = viewModel::sendPasswordReset,
                        trailing = {
                            Icon(
                                imageVector = if (passwordVisible)
                                    Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = Ink.Inactive,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickableNoRipple { passwordVisible = !passwordVisible },
                            )
                        },
                    )

                    Spacer(Modifier.height(16.dp))

                    KeepSignedInRow(checked = keepSignedIn, onToggle = { keepSignedIn = !keepSignedIn })

                    Spacer(Modifier.height(18.dp))

                    GradientCtaButton(
                        text = if (isSignUp) "Create Account" else "Sign In",
                        loading = state.isLoading,
                        onClick = viewModel::submit,
                    )

                    Spacer(Modifier.height(20.dp))
                    DividerWithLabel("or continue with")
                    Spacer(Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SecondaryButton(
                            modifier = Modifier.weight(1f),
                            label = "Google",
                            enabled = !state.isLoading,
                            leadingDrawable = R.drawable.ic_google_g,
                            onClick = { viewModel.signInWithGoogle(context) },
                        )
                        SecondaryButton(
                            modifier = Modifier.weight(1f),
                            label = "Phone OTP",
                            enabled = !state.isLoading,
                            leadingIcon = Icons.Filled.Phone,
                            onClick = {
                                scope.launch {
                                    snackbarHost.showSnackbar("Phone OTP sign-in is coming soon.")
                                }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Toggle row ────────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.entrance(visible, 270),
                ) {
                    Text(
                        if (isSignUp) "Already have an account? " else "New to SeismicWatch? ",
                        color = Ink.Inactive,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = if (isSignUp) "Sign in" else "Create an account",
                        color = Ink.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.clickableNoRipple { viewModel.toggleMode() },
                    )
                }

                Spacer(Modifier.height(18.dp))
                LegalFooter(modifier = Modifier.entrance(visible, 320))
                Spacer(Modifier.height(28.dp))
            }
        }
        }
    }
}

// ─── Background ──────────────────────────────────────────────────────────────
/**
 * The "high-tech monitor" ground: a flat black-violet base, three radial ember glows,
 * a 28dp seismic grid, and a 2dp scanline drifting top-to-bottom on a 6s loop.
 */
// Static high-tech ground (three ember glows + 28dp grid), built once via drawWithCache. The old
// drifting scanline was removed so the sign-in screen isn't redrawing the full-screen gradients every
// frame (cheaper, smoother on low-end devices). The ripple logo still provides motion.
private fun Modifier.seismicBackground(): Modifier =
    this
        .background(Ink.Base)
        .drawWithCache {
            val w = size.width
            val h = size.height
            val glow1 = Brush.radialGradient(listOf(Ink.Red.copy(alpha = 0.22f), Color.Transparent), Offset(w / 2f, 0f), 300.dp.toPx())
            val glow2 = Brush.radialGradient(listOf(Ink.Orange.copy(alpha = 0.18f), Color.Transparent), Offset(w * 0.95f, h * 0.55f), 180.dp.toPx())
            val glow3 = Brush.radialGradient(listOf(Ink.Red.copy(alpha = 0.16f), Color.Transparent), Offset(0f, h * 0.80f), 140.dp.toPx())
            val cell = 28.dp.toPx()
            onDrawBehind {
                drawRect(glow1, size = Size(w, h))
                drawRect(glow2, size = Size(w, h))
                drawRect(glow3, size = Size(w, h))
                var x = 0f
                while (x <= w) { drawLine(Ink.Grid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f); x += cell }
                var y = 0f
                while (y <= h) { drawLine(Ink.Grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f); y += cell }
            }
        }

// ─── Brand mark ──────────────────────────────────────────────────────────────
/** 68dp gradient shell holding the seismic-wave glyph, ringed by three staggered ripple borders. */
@Composable
private fun RippleLogo() {
    val transition = rememberInfiniteTransition(label = "ripple")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
        // Three rings, each offset by a third of the 2s cycle.
        repeat(3) { i ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(i * 650),
                ),
                label = "ring$i",
            )
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .graphicsLayer {
                        val scale = 1f + phase * 1.2f
                        scaleX = scale
                        scaleY = scale
                        alpha = (0.55f * (1f - phase)).coerceIn(0f, 1f)
                    }
                    .border(1.5.dp, Ink.Red, CircleShape),
            )
        }
        // Gradient shell.
        Box(
            modifier = Modifier
                .size(68.dp)
                .shadow(18.dp, RoundedCornerShape(22.dp), spotColor = Ink.Red, ambientColor = Ink.Red)
                .clip(RoundedCornerShape(22.dp))
                .background(Ink.Gradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_seismic_wave),
                contentDescription = "SeismicWatch logo",
                tint = Ink.White,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

// ─── Live pill ───────────────────────────────────────────────────────────────
@Composable
private fun LiveStatusPill() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotAlpha",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(Ink.Red.copy(alpha = 0.12f))
            .border(1.dp, Ink.Red.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .graphicsLayer { alpha = dotAlpha }
                .clip(CircleShape)
                .background(Ink.Red),
        )
        Text(
            text = "MONITORING ACTIVE — PH REGION",
            fontWeight = FontWeight.Bold,
            fontSize = 10.5.sp,
            letterSpacing = 1.0.sp,
            color = Ink.Label,
        )
    }
}

// ─── Input field ─────────────────────────────────────────────────────────────
@Composable
private fun SeismicField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leading: ImageVector,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingAction: String? = null,
    onTrailingActionClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                color = Ink.Label,
            )
            if (trailingAction != null) {
                Text(
                    text = trailingAction,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Ink.Red,
                    modifier = Modifier.clickableNoRipple { onTrailingActionClick?.invoke() },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Ink.Field)
                .border(1.5.dp, Ink.Border, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(leading, contentDescription = null, tint = Ink.Inactive, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, color = Ink.Inactive, fontSize = 14.sp)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Ink.White, fontSize = 14.sp, fontFamily = SeismicFont),
                    cursorBrush = Brush.linearGradient(listOf(Ink.Red, Ink.Red)),
                    visualTransformation = visualTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

@Composable
private fun KeepSignedInRow(checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickableNoRipple(onToggle),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(5.dp))
                .then(
                    if (checked) Modifier.background(Ink.Gradient)
                    else Modifier.border(1.5.dp, Ink.Border, RoundedCornerShape(5.dp))
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text("✓", color = Ink.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text("Keep me signed in", color = Ink.Inactive, fontSize = 13.sp)
    }
}

// ─── Buttons ─────────────────────────────────────────────────────────────────
@Composable
private fun GradientCtaButton(text: String, loading: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "ctaScale")

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .fillMaxWidth()
            .height(54.dp)
            // Double-layer glow: sharp red + soft orange spread.
            .shadow(24.dp, RoundedCornerShape(16.dp), spotColor = Ink.Red, ambientColor = Ink.Orange)
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.Gradient)
            // Subtle inset top highlight.
            .border(
                BorderStroke(1.dp, Brush.verticalGradient(listOf(Ink.White.copy(alpha = 0.18f), Color.Transparent))),
                RoundedCornerShape(16.dp),
            )
            .clickableButton(enabled = !loading, interactionSource = interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Ink.White, strokeWidth = 2.dp)
        } else {
            Text(text, color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun SecondaryButton(
    modifier: Modifier = Modifier,
    label: String,
    enabled: Boolean,
    leadingIcon: ImageVector? = null,
    leadingDrawable: Int? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Ink.Field)
            .border(1.dp, Ink.Border, RoundedCornerShape(12.dp))
            .clickableButton(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leadingDrawable != null -> Icon(
                painterResource(leadingDrawable),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(18.dp),
            )
            leadingIcon != null -> Icon(
                leadingIcon,
                contentDescription = null,
                tint = Ink.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(label, color = Ink.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun DividerWithLabel(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).height(1.dp).background(Ink.Border))
        Text(label, color = Ink.Inactive, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Ink.Border))
    }
}

@Composable
private fun LegalFooter(modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            append("By signing in, you agree to SeismicWatch's ")
            withStyle(SpanStyle(color = Ink.Label, fontWeight = FontWeight.SemiBold)) { append("Terms of Service") }
            append(" and ")
            withStyle(SpanStyle(color = Ink.Label, fontWeight = FontWeight.SemiBold)) { append("Privacy Policy") }
            append(". Your location data helps us send you accurate alerts.")
        },
        color = Ink.Inactive,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(horizontal = 16.dp),
    )
}

// ─── Helpers ─────────────────────────────────────────────────────────────────
/** Fades + slides a section up 20dp into place once [visible] turns true, after [delayMillis]. */
@Composable
private fun Modifier.entrance(visible: Boolean, delayMillis: Int): Modifier {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 460, delayMillis = delayMillis),
        label = "entranceAlpha",
    )
    val translateY by animateFloatAsState(
        targetValue = if (visible) 0f else 50f,
        animationSpec = tween(durationMillis = 520, delayMillis = delayMillis, easing = FastOutSlowInEasing),
        label = "entranceY",
    )
    return this.graphicsLayer {
        this.alpha = alpha
        this.translationY = translateY
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickableButton(enabled = true, onClick = onClick)

/**
 * Ripple-free clickable. Optionally shares an [interactionSource] so callers can read press state
 * (used by the CTA for its scale-on-press feedback).
 */
private fun Modifier.clickableButton(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    clickable(interactionSource = source, indication = null, enabled = enabled) { onClick() }
}

// ─── VerifyEmail ─────────────────────────────────────────────────────────────
/**
 * Shown when a password user is signed in but hasn't verified their email yet. Restyled to the
 * same SeismicWatch ground so the flow stays visually cohesive.
 */
@Composable
fun VerifyEmailScreen(email: String, viewModel: AuthViewModel) {
    val state by viewModel.ui.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(state.error, state.info) {
        val msg = state.error ?: state.info
        if (msg != null) {
            snackbarHost.showSnackbar(msg)
            viewModel.consumeMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = Ink.Base,
    ) { padding ->
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = SeismicFont)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .seismicBackground()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .shadow(18.dp, RoundedCornerShape(22.dp), spotColor = Ink.Red, ambientColor = Ink.Red)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Ink.Gradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MailOutline, contentDescription = null, tint = Ink.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text("Verify your email", fontFamily = SeismicDisplayFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Ink.White)
            Spacer(Modifier.height(10.dp))
            Text(
                "We sent a verification link to\n$email\nOpen it, then tap \"I've verified\".",
                fontSize = 13.5.sp,
                color = Ink.Inactive,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            GradientCtaButton(text = "I've verified", loading = false, onClick = viewModel::refreshVerification)
            Spacer(Modifier.height(14.dp))
            Text(
                "Resend email",
                color = Ink.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clickableNoRipple(viewModel::resendVerification),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Sign out",
                color = Ink.Inactive,
                fontSize = 13.sp,
                modifier = Modifier.clickableNoRipple(viewModel::signOut),
            )
        }
        }
    }
}
