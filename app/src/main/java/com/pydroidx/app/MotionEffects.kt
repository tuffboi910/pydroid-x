package com.pydroidx.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Brief surface feedback, with no shaders, particles or full-screen drawing. */
@Composable
internal fun RunBurst(trigger: Int, fileName: String, enabled: Boolean, origin: Offset) {
    val opacity=remember { Animatable(0f) }
    LaunchedEffect(trigger,enabled) {
        if(enabled && trigger>0 && origin!=Offset.Zero) {
            opacity.snapTo(1f)
            kotlinx.coroutines.delay(600)
            opacity.animateTo(0f,tween(IdeDesign.fast))
        } else opacity.snapTo(0f)
    }
    if(opacity.value>0f) Box(Modifier.fillMaxSize()) {
        Text("Running $fileName",style=MaterialTheme.typography.bodySmall,color=IdeDesign.text,
            modifier=Modifier.align(androidx.compose.ui.Alignment.TopCenter).padding(top=8.dp)
                .graphicsLayer { alpha=opacity.value }.background(IdeDesign.raised,IdeDesign.compact)
                .padding(horizontal=12.dp,vertical=8.dp))
    }
}
