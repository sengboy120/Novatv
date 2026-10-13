package app.novatv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A neon line-art icon (see IconData) drawn with a soft glow. */
@Composable
fun ChannelIcon(key: String, modifier: Modifier = Modifier, size: Dp = 48.dp, glow: Boolean = true) {
    val def = IconData.icons[key] ?: IconData.icons.getValue("network")
    val paths = remember(key) { def.paths.map { PathParser().parsePathString(it).toPath() } }
    val brush = remember(key) {
        Brush.linearGradient(
            colors = listOf(Color(def.c1), Color(def.c2)),
            start = Offset(0f, 0f),
            end = Offset(48f, 48f)
        )
    }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 48f
        withTransform({ scale(s, s, pivot = Offset.Zero) }) {
            if (glow) {
                for (p in paths) {
                    drawPath(
                        p, brush, alpha = 0.25f,
                        style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
            for (p in paths) {
                drawPath(
                    p, brush,
                    style = Stroke(width = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

/** Icon inside a rounded dark tile tinted with the icon's own colour. */
@Composable
fun ChannelBadge(key: String, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val def = IconData.icons[key] ?: IconData.icons.getValue("network")
    val shape = RoundedCornerShape(size / 4)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Color(0xFF0A0E22))
            .border(1.dp, Color(def.c1).copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center
    ) {
        ChannelIcon(key, size = size * 0.66f)
    }
}

@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Nova.cyan,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val base = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier
    Box(
        base
            .shadow(if (focused) 16.dp else 0.dp, shape, ambientColor = accent, spotColor = accent)
            .clip(shape)
            .background(
                when {
                    focused -> accent.copy(alpha = 0.22f)
                    selected -> accent.copy(alpha = 0.12f)
                    else -> Nova.panel
                }
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) accent else accent.copy(alpha = if (selected) 0.8f else 0.3f),
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (focused || selected) Color.White else Nova.text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            letterSpacing = 1.sp
        )
    }
}
