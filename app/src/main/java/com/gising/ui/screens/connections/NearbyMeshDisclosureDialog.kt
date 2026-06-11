package com.gising.ui.screens.connections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
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
 * Google Play "prominent disclosure" for the offline mesh relay.
 *
 * Enabling the relay uses the Nearby permission group (BLUETOOTH_ADVERTISE / BLUETOOTH_CONNECT /
 * BLUETOOTH_SCAN / NEARBY_WIFI_DEVICES) and runs a FOREGROUND_SERVICE_SPECIAL_USE service so SOS
 * packets keep relaying when the app is closed. Play policy requires that, BEFORE requesting those
 * permissions / starting that service, the app shows an in-context disclosure that (1) names what
 * the app does, (2) says it runs in the background / when the app is closed, and (3) requires an
 * affirmative action to accept. This dialog satisfies all three; only on "Turn on" do we request
 * the permissions and start the service. See PRIVACY_POLICY.md and SECURITY.md.
 */
@Composable
fun NearbyMeshDisclosureDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        containerColor = SeismicColors.Slate,
        icon = {
            Icon(Icons.Filled.Hub, contentDescription = null, tint = SeismicColors.Ember)
        },
        title = {
            Text("Turn on the offline mesh relay", color = SeismicColors.Chalk)
        },
        text = {
            Column {
                Bullet(
                    "SeismicWatch uses Bluetooth and Wi-Fi to discover and connect to nearby devices " +
                        "(the Nearby permission). No location is collected for this — it is used " +
                        "only to pass emergency SOS messages between phones.",
                )
                Bullet(
                    "It keeps forwarding nearby SOS signals even when the app is closed or not in " +
                        "use, running a foreground service so your phone can act as a relay when " +
                        "cell towers are down. A notification always shows while the relay is on.",
                )
                Bullet(
                    "This uses extra battery. You can turn the relay off at any time, and no " +
                        "personal data is stored or shared — only SOS packets are passed along.",
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "By tapping Turn on, you consent to using Bluetooth/Wi-Fi nearby connections " +
                        "and a background service to relay emergency SOS messages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SeismicColors.Mist,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("Turn on", color = SeismicColors.EmberGlow)
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
