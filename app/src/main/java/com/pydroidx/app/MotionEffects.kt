package com.pydroidx.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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

/** One-shot effect centered on the button actually tapped. No idle frame loop. */
@Composable
internal fun RunBurst(trigger: Int, fileName: String, enabled: Boolean, origin: Offset) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger, enabled) {
        if (enabled && trigger > 0 && origin != Offset.Zero) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
        } else progress.snapTo(1f)
    }
    if (!enabled || progress.value >= 1f || origin == Offset.Zero) return
    val amount = progress.value
    val opacity = ((1f - amount) * 2.2f).coerceIn(0f, 1f)
    val density = LocalDensity.current.density
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val cyan = Color(0xFF31B9FF)
            val mint = Color(0xFF42FFC6)
            val radius = (16f + 75f*amount)*density
            drawCircle(
                brush = Brush.radialGradient(listOf(mint.copy(alpha=0.22f*opacity), Color.Transparent),
                    center=origin, radius=radius*1.3f),
                radius=radius*1.3f, center=origin
            )
            drawCircle(mint.copy(alpha=opacity), radius, origin,
                style=Stroke(width=(2.2f-amount*1.4f)*density))
            drawCircle(cyan.copy(alpha=opacity*0.7f), radius*0.52f, origin,
                style=Stroke(width=1.2f*density))
            repeat(12) { index ->
                val angle = (index*30f-105f)*PI/180
                val start = (18f+65f*amount)*density
                val end = start+(10f+14f*(1f-amount))*density
                fun at(distance: Float) = Offset(
                    origin.x+cos(angle).toFloat()*distance,
                    origin.y+sin(angle).toFloat()*distance)
                drawLine(if(index%2==0) mint else cyan, at(start), at(end),
                    strokeWidth=(1.7f-amount*0.7f)*density*opacity, cap=StrokeCap.Round)
            }
        }
        val labelX = (origin.x/density-85f).coerceIn(8f,(maxWidth.value-170f).coerceAtLeast(8f))
        val labelY = (origin.y/density+68f).coerceAtMost(maxHeight.value-32f)
        Text(
            "▶  Running $fileName",
            color=Color.White.copy(alpha=opacity),
            fontSize=11.sp,
            fontWeight=FontWeight.SemiBold,
            modifier=Modifier.offset(x=labelX.dp,y=labelY.dp)
                .graphicsLayer { translationY=(1f-amount)*7f*density }
                .background(Color(0xCC151B23).copy(alpha=opacity),RoundedCornerShape(9.dp))
                .padding(horizontal=9.dp,vertical=5.dp)
        )
    }
}
