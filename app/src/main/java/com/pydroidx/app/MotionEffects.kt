package com.pydroidx.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A one-shot, touch-transparent launch effect; no frame loop while editing. */
@Composable
internal fun RunBurst(trigger: Int, fileName: String, enabled: Boolean) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger, enabled) {
        if (enabled && trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(850, easing = FastOutSlowInEasing))
        } else progress.snapTo(1f)
    }
    if (!enabled || progress.value >= 1f) return
    val amount = progress.value
    val opacity = ((1f - amount) * 2.2f).coerceIn(0f, 1f)
    val density = LocalDensity.current.density
    Box(Modifier.fillMaxWidth().statusBarsPadding().height(190.dp)) {
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            val origin = androidx.compose.ui.geometry.Offset(size.width - 44f*density, 40f*density)
            val cyan = Color(0xFF31B9FF)
            val mint = Color(0xFF42FFC6)
            val radius = (20f + 110f*amount)*density
            drawCircle(
                brush = Brush.radialGradient(listOf(mint.copy(alpha=0.28f*opacity), Color.Transparent),
                    center=origin, radius=radius*1.35f),
                radius=radius*1.35f, center=origin
            )
            drawCircle(mint.copy(alpha=opacity), radius, origin,
                style=Stroke(width=(2.5f-amount*1.7f)*density))
            drawCircle(cyan.copy(alpha=opacity*0.7f), radius*0.58f, origin,
                style=Stroke(width=1.3f*density))
            repeat(12) { index ->
                val angle = (index*30f-105f)*PI/180
                val start = (25f+95f*amount)*density
                val end = start+(14f+24f*(1f-amount))*density
                fun at(distance: Float) = androidx.compose.ui.geometry.Offset(
                    origin.x+cos(angle).toFloat()*distance,
                    origin.y+sin(angle).toFloat()*distance)
                drawLine(if(index%2==0) mint else cyan, at(start), at(end),
                    strokeWidth=(2f-amount)*density*opacity, cap=StrokeCap.Round)
            }
        }
        Text(
            "▶  Running $fileName",
            color=Color.White.copy(alpha=opacity),
            fontSize=13.sp,
            fontWeight=FontWeight.SemiBold,
            modifier=Modifier.align(Alignment.TopCenter).offset(y=104.dp)
                .graphicsLayer { translationY=(1f-amount)*9f*density }
        )
    }
}
