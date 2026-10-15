package com.anish1050.ledger.ui.motion

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

// One place for the app's motion language: physics springs rather than fixed
// curves (the main reason iOS UI feels "alive"), plus tuned haptics.
object Motion {
    /** Screen and sheet movement: settles quickly with a hint of overshoot. */
    val screen = spring<IntOffset>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
    val screenFade = spring<Float>(stiffness = Spring.StiffnessMediumLow)
    val screenDp = spring<Dp>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    /** Press feedback: fast and bouncy so it tracks the finger. */
    val press = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
    /** Charts and indicators. */
    val chart = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessVeryLow)
}

enum class Haptic(private val constant: Int) {
    Tap(HapticFeedbackConstants.KEYBOARD_TAP),
    Tick(HapticFeedbackConstants.CLOCK_TICK),
    Confirm(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS),
    Reject(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS);

    fun play(view: View) {
        view.performHapticFeedback(constant)
    }
}

/**
 * Clickable that shrinks under the finger with a spring and plays a haptic,
 * instead of a ripple. Use for every tappable surface so the app feels uniform.
 */
fun Modifier.pressable(haptic: Haptic = Haptic.Tap, pressedScale: Float = 0.94f, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, Motion.press, label = "press")
    val view = LocalView.current
    graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null) {
            haptic.play(view)
            onClick()
        }
}
