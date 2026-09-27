package com.pydroidx.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Resolution-independent line icons; no font-dependent placeholder glyphs. */
@Composable
fun IdeGlyph(name: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp).semantics { contentDescription = name }) {
        val sx = size.width / 24f; val sy = size.height / 24f
        val path = Path()
        fun m(x: Float, y: Float) = path.moveTo(x*sx, y*sy)
        fun l(x: Float, y: Float) = path.lineTo(x*sx, y*sy)
        fun c(x1: Float,y1: Float,x2: Float,y2: Float,x3: Float,y3: Float) = path.cubicTo(x1*sx,y1*sy,x2*sx,y2*sy,x3*sx,y3*sy)
        when (name) {
            "Folders" -> { m(3f,6f);l(9f,6f);l(11f,8f);l(21f,8f);l(21f,20f);l(3f,20f);path.close() }
            "Console" -> { m(3f,4f);l(21f,4f);l(21f,20f);l(3f,20f);path.close();m(6f,8f);l(10f,12f);l(6f,16f);m(13f,16f);l(18f,16f) }
            "Helper" -> { m(12f,5f);c(8f,2f,5f,3f,2f,4f);l(2f,20f);c(5f,18f,8f,18f,12f,21f);c(16f,18f,19f,18f,22f,20f);l(22f,4f);c(19f,3f,16f,2f,12f,5f);l(12f,21f) }
            "Undo" -> { m(8f,5f);l(3f,10f);l(8f,15f);m(3f,10f);l(14f,10f);c(23f,10f,23f,21f,14f,21f) }
            "Redo" -> { m(16f,5f);l(21f,10f);l(16f,15f);m(21f,10f);l(10f,10f);c(1f,10f,1f,21f,10f,21f) }
            "Close" -> { m(6f,6f);l(18f,18f);m(18f,6f);l(6f,18f) }
            "New file" -> { m(12f,4f);l(12f,20f);m(4f,12f);l(20f,12f) }
            else -> {
                drawCircle(color, 7f*sx, style=Stroke(1.6f*sx))
                drawCircle(color, 2.5f*sx, style=Stroke(1.6f*sx))
                for (i in 0..7) {
                    val angle = i*Math.PI/4
                    m(12f+7f*kotlin.math.cos(angle).toFloat(),12f+7f*kotlin.math.sin(angle).toFloat())
                    l(12f+10f*kotlin.math.cos(angle).toFloat(),12f+10f*kotlin.math.sin(angle).toFloat())
                }
            }
        }
        drawPath(path, color, style=Stroke(1.6f*sx, cap=StrokeCap.Round))
    }
}
