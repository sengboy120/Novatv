package app.novatv.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** What kind of device we are running on. */
object Device {
    /** True on Android TV / Shield (D-pad remote), false on phones, tablets and car head units. */
    fun isTv(ctx: Context): Boolean {
        val um = ctx.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return um?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
}

/**
 * A tap target that is NOT a focus target - so adding it never changes how the D-pad moves.
 * Used for the touch-only buttons (close, minus / plus ...).
 */
fun Modifier.tapOnly(onTap: () -> Unit): Modifier = composed {
    val cb = rememberUpdatedState(onTap)
    pointerInput(Unit) {
        detectTapGestures { cb.value.invoke() }
    }
}

/** Swallows touches so they do not fall through to whatever is drawn underneath a panel. */
fun Modifier.swallowTouch(): Modifier = pointerInput(Unit) {
    detectTapGestures { }
}

/**
 * Whole-screen touch layer for the player:
 * tap = show / hide the controls, double tap = skip back (left half) / ahead (right half),
 * swipe left = next channel, swipe right = previous channel.
 */
fun Modifier.touchLayer(
    onTap: () -> Unit,
    onDoubleTap: (leftHalf: Boolean) -> Unit,
    onSwipe: (dir: Int) -> Unit
): Modifier = composed {
    val tap = rememberUpdatedState(onTap)
    val dbl = rememberUpdatedState(onDoubleTap)
    val swp = rememberUpdatedState(onSwipe)
    this
        .pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { o -> dbl.value.invoke(o.x < size.width / 2f) },
                onTap = { tap.value.invoke() }
            )
        }
        .pointerInput(Unit) {
            val threshold = 90.dp.toPx()
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    if (total < -threshold) swp.value.invoke(1)
                    else if (total > threshold) swp.value.invoke(-1)
                    total = 0f
                },
                onDragCancel = { total = 0f },
                onHorizontalDrag = { change, dx ->
                    total += dx
                    change.consume()
                }
            )
        }
}
