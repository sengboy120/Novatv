package app.novatv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novatv.Logos
import app.novatv.RealChannels

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

/** Icon inside a rounded dark tile tinted with the icon's own colour (real channels show their name). */
@Composable
fun ChannelBadge(key: String, size: Dp = 56.dp, modifier: Modifier = Modifier, chId: String? = null) {
    val acc = remember(key) { accentOf(key) }
    val shape = RoundedCornerShape(size / 4)
    val rc = if (key.startsWith("rc:")) RealChannels.byKey(key.removePrefix("rc:")) else null
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(tint(Color(0xFF0A0A10), acc.c1, 0.16f))
            .border(1.dp, acc.c1.copy(alpha = 0.5f), shape),
        contentAlignment = Alignment.Center
    ) {
        val ctx = LocalContext.current
        // 1st choice: a logo the viewer uploaded for this channel (Settings -> CHANNEL LOGOS)
        val ver = Logos.version.intValue
        val uploaded = if (chId == null) null else remember(chId, ver) { Logos.get(chId) }
        // optional: drop logo_<channel>.png (e.g. logo_hbo.png) into res/drawable and it is used instead of the text
        val logoRes = remember(key) {
            if (rc == null) 0 else ctx.resources.getIdentifier("logo_" + rc.key, "drawable", ctx.packageName)
        }
        if (uploaded != null) {
            Image(
                bitmap = uploaded,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size * 0.12f)
            )
        } else if (rc != null && logoRes != 0) {
            Image(
                painter = painterResource(logoRes),
                contentDescription = rc.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(size * 0.12f)
            )
        } else if (rc != null) {
            val fs = (size.value * (if (rc.badge.length <= 2) 0.36f else if (rc.badge.length == 3) 0.29f else 0.22f)).sp
            Text(
                rc.badge,
                style = TextStyle(brush = acc.brush, fontSize = fs, fontWeight = FontWeight.ExtraBold),
                maxLines = 1
            )
        } else {
            ChannelIcon(key, size = size * 0.66f)
        }
    }
}

@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Nova.cyan,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
    compact: Boolean = false
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
            .padding(horizontal = if (compact) 16.dp else 22.dp, vertical = if (compact) 6.dp else 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (focused || selected) Color.White else Nova.text,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 12.sp else 15.sp,
            letterSpacing = 1.sp
        )
    }
}
