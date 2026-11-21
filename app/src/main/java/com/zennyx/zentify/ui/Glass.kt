package com.zennyx.zentify.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

val Bg = Color(0xFF0A0C12)
val Accent = Color(0xFFB79CFF)
val TextHi = Color.White.copy(alpha = 0.95f)
val TextLo = Color.White.copy(alpha = 0.58f)

/** Frosted-glass surface: translucent gradient fill + light edge. */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    strength: Float = 0.10f,
): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = strength + 0.06f), Color.White.copy(alpha = (strength - 0.04f).coerceAtLeast(0.01f))),
        ),
        shape,
    )
    .border(
        1.dp,
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.14f))),
        shape,
    )

/** Soft colour orbs + the current artwork, heavily blurred, behind everything. */
@Composable
fun GlassBackground(artUrl: String?, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        Box(
            Modifier.size(340.dp).offset((-100).dp, (-70).dp)
                .background(Brush.radialGradient(listOf(Color(0x558B5CF6), Color.Transparent)), CircleShape),
        )
        Box(
            Modifier.size(380.dp).align(Alignment.BottomEnd).offset(120.dp, 100.dp)
                .background(Brush.radialGradient(listOf(Color(0x4400D1FF), Color.Transparent)), CircleShape),
        )
        if (!artUrl.isNullOrBlank() && Build.VERSION.SDK_INT >= 31) {
            AsyncImage(
                model = artUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(72.dp, BlurredEdgeTreatment.Unbounded).alpha(0.42f),
            )
        }
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x550A0C12), Color(0xCC0A0C12)))),
        )
        content()
    }
}

enum class Ctl { Play, Pause, Next, Prev }

@Composable
fun CtlIcon(kind: Ctl, iconSize: Dp = 24.dp, tint: Color = Color.White) {
    Canvas(Modifier.size(iconSize)) {
        val w = this.size.width
        val h = this.size.height
        fun tri(x1: Float, x2: Float) = Path().apply {
            moveTo(w * x1, h * 0.2f); lineTo(w * x2, h * 0.5f); lineTo(w * x1, h * 0.8f); close()
        }
        when (kind) {
            Ctl.Play -> drawPath(
                Path().apply {
                    moveTo(w * 0.28f, h * 0.16f); lineTo(w * 0.86f, h * 0.5f); lineTo(w * 0.28f, h * 0.84f); close()
                },
                tint,
            )
            Ctl.Pause -> {
                drawRoundRect(tint, Offset(w * 0.22f, h * 0.16f), Size(w * 0.20f, h * 0.68f), CornerRadius(w * 0.05f))
                drawRoundRect(tint, Offset(w * 0.58f, h * 0.16f), Size(w * 0.20f, h * 0.68f), CornerRadius(w * 0.05f))
            }
            Ctl.Next -> {
                drawPath(tri(0.12f, 0.68f), tint)
                drawRoundRect(tint, Offset(w * 0.74f, h * 0.2f), Size(w * 0.12f, h * 0.6f), CornerRadius(w * 0.04f))
            }
            Ctl.Prev -> {
                drawPath(tri(0.88f, 0.32f), tint)
                drawRoundRect(tint, Offset(w * 0.14f, h * 0.2f), Size(w * 0.12f, h * 0.6f), CornerRadius(w * 0.04f))
            }
        }
    }
}

@Composable
fun GlassCircle(
    diameter: Dp,
    onClick: () -> Unit,
    strength: Float = 0.12f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier.size(diameter).glass(CircleShape, strength).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
