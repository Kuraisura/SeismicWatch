package com.gising.ui.screens.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gising.ui.theme.SeismicColors

/**
 * Google Play "prominent disclosure" for background location.
 *
 * Policy requires that, BEFORE requesting the ACCESS_BACKGROUND_LOCATION permission, the
 * app shows an in-context dialog that (1) names the data collected, (2) says it is used in
 * the background / when the app is closed, and (3) requires an affirmative action to accept.
 * This dialog satisfies all three; only on "Allow" do we request the permission + start
 * sharing. See PRIVACY_POLICY.md and SECURITY.md.
 */
@Composable
fun BackgroundLocationDisclosureDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        containerColor = SeismicColors.Slate,
        icon = {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SeismicColors.Ember)
        },
        title = {
            Text("Share location with your family", color = SeismicColors.Chalk)
        },
        text = {
            Column {
                Bullet("SeismicWatch collects this device's location and shares it with the family circles you choose.")
                Bullet("It updates even when the app is closed or not in use, so your family always sees where you are in an emergency.")
                Bullet("Only people in your circles can see you. You can pause or turn this off at any time, and a notification always shows while you're sharing.")
                Spacer(Modifier.height(8.dp))
                Text(
                    "By tapping Allow, you consent to background location collection for this purpose.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SeismicColors.Mist,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("Allow", color = SeismicColors.EmberGlow)
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("Not now", color = SeismicColors.Fog)
            }
        },
    )
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text("•", color = SeismicColors.Ember)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = SeismicColors.Chalk)
    }
}
