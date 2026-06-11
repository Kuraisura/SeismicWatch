package com.gising.emergency

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.gising.ui.theme.SeismicPalette

/**
 * Home-screen (and, on some launchers, lock-screen) one-tap SOS widget built with Jetpack Glance.
 *
 * Tapping it routes through [SosTriggerAction] → [SosController.arm], so a single touch from the
 * launcher captures the user's location and starts the countdown without ever opening the app.
 * Android removed true lock-screen widgets in 5.0, so the panic UI itself is still surfaced via
 * the full-screen-intent notification the controller posts.
 */
class SosWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        val palette = widgetPalette(context)
        provideContent { SosWidgetContent(palette) }
    }

    @Composable
    private fun SosWidgetContent(p: SeismicPalette) {
        // SOS stays in the danger colour across every theme (critical is red in all palettes).
        val danger = p.critical
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(danger)
                .cornerRadius(26.dp)
                .clickable(actionRunCallback<SosTriggerAction>())
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // White ring halo with a solid core — reads as a panic button.
                Box(
                    modifier = GlanceModifier
                        .size(78.dp)
                        .cornerRadius(39.dp)
                        .background(Color.White.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(54.dp)
                            .cornerRadius(27.dp)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "SOS",
                            style = TextStyle(
                                color = ColorProvider(danger),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    "TAP FOR HELP",
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.92f)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}

/** Glance action that fires the SOS the instant the widget is tapped. */
class SosTriggerAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: androidx.glance.GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        SosController.arm(context.applicationContext, SosSource.WIDGET)
    }
}

class SosWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SosWidget()
}
