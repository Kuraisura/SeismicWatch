package com.gising.emergency

/**
 * State model for the panic / SOS subsystem. A single [SosSnapshot] is owned by [SosController]
 * and observed by every surface (the panic UI, the QS tile, services), so they can never
 * disagree about what is happening.
 */

/** Lifecycle of a single SOS episode. */
enum class SosPhase {
    IDLE,          // nothing happening
    ARMING,        // countdown running — user can still cancel
    BROADCASTING,  // actively transmitting across channels
    SENT,          // at least one channel succeeded
    CANCELLED,     // user cancelled during ARMING
}

/**
 * The threat type. Deliberately defaults to [UNSPECIFIED] — the alert fires generic FIRST and is
 * classified LATER, so the user never has to choose a category before help is on the way.
 */
enum class SosCategory(val label: String, val smsTag: String) {
    UNSPECIFIED("Emergency", "SOS"),
    MEDICAL("Medical", "MEDICAL"),
    FIRE("Fire", "FIRE"),
    HAZARD("Hazard", "HAZARD"),
    TRAPPED("Trapped", "TRAPPED"),
}

/** Where an SOS was triggered from — drives analytics + tile/widget state. */
enum class SosSource { TILE, WIDGET, POWER, IN_APP }

enum class ChannelStatus { PENDING, SENDING, SENT, FAILED, SKIPPED }

/** Result of one delivery channel (Cloud / SMS / Mesh), streamed live into the snapshot. */
data class ChannelResult(
    val channel: String,
    val status: ChannelStatus,
    val detail: String = "",
)

/** Immutable snapshot of the whole SOS episode at a moment in time. */
data class SosSnapshot(
    val phase: SosPhase = SosPhase.IDLE,
    val secondsLeft: Int = 0,
    val category: SosCategory = SosCategory.UNSPECIFIED,
    val location: Pair<Double, Double>? = null,
    val source: SosSource = SosSource.IN_APP,
    val channels: List<ChannelResult> = emptyList(),
    val startedAt: Long = 0L,
) {
    val isActive: Boolean get() = phase == SosPhase.ARMING ||
        phase == SosPhase.BROADCASTING || phase == SosPhase.SENT

    /** True once any channel has confirmed delivery. */
    val anyDelivered: Boolean get() = channels.any { it.status == ChannelStatus.SENT }

    companion object {
        const val CHANNEL_CLOUD = "Cloud"
        const val CHANNEL_SMS = "SMS"
        const val CHANNEL_MESH = "Mesh"
    }
}
