package com.pydroidx.app

import android.animation.ValueAnimator
import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared production chrome. Content colors (syntax/ANSI) remain independent. */
object IdeDesign {
    val background = Color(0xFF0D1118)
    val surface = Color(0xFF171D28)
    val raised = Color(0xFF222B39)
    val highest = Color(0xFF2C3748)
    val outline = Color(0xFF3A4657)
    val text = Color(0xFFF0F1F3)
    val muted = Color(0xFFAAB8CB)
    val accent = Color(0xFF78ADFF)
    val error = Color(0xFFF0ABA8)
    val warning = Color(0xFFE4C28A)
    val success = Color(0xFFAACDB4)
    val compact = RoundedCornerShape(10.dp)
    val card = RoundedCornerShape(16.dp)
    val sheet = RoundedCornerShape(topStart=24.dp, topEnd=24.dp)
    const val fast = 120
    const val standard = 200
    const val enter = 240
    fun color(hex: String, fallback: Color): Color =
        runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(fallback)
    fun foreground(color: Color): Color = if (
        .2126f*color.red + .7152f*color.green + .0722f*color.blue > .58f
    ) Color(0xFF0D1118) else text
    // Earlier releases saved their neon defaults alongside every setting change.
    // Render those defaults neutrally without overwriting any stored preference.
    fun legacySurface(hex: String, fallback: Color, vararg legacy: String): Color =
        if (legacy.any { it.equals(hex, true) }) fallback else color(hex, fallback)
}

@Composable
fun IdeTheme(accentHex: String, backgroundHex: String, content: @Composable () -> Unit) {
    val accent = IdeDesign.color(accentHex, IdeDesign.accent)
    val background = IdeDesign.color(backgroundHex, IdeDesign.background)
    val scheme = darkColorScheme(
        primary=accent, onPrimary=IdeDesign.foreground(accent),
        primaryContainer=accent.copy(alpha=.16f), onPrimaryContainer=IdeDesign.text,
        secondary=accent, onSecondary=IdeDesign.foreground(accent),
        secondaryContainer=accent.copy(alpha=.12f), onSecondaryContainer=IdeDesign.text,
        tertiary=accent, onTertiary=IdeDesign.foreground(accent),
        background=background, onBackground=IdeDesign.text,
        surface=IdeDesign.surface, onSurface=IdeDesign.text,
        surfaceVariant=IdeDesign.raised, onSurfaceVariant=IdeDesign.muted,
        surfaceContainerLowest=background, surfaceContainerLow=IdeDesign.surface,
        surfaceContainer=IdeDesign.surface, surfaceContainerHigh=IdeDesign.raised,
        surfaceContainerHighest=IdeDesign.highest,
        outline=IdeDesign.outline, outlineVariant=IdeDesign.outline.copy(alpha=.6f),
        error=IdeDesign.error, onError=Color(0xFF291414),
        errorContainer=Color(0xFF392526), onErrorContainer=IdeDesign.error,
        surfaceTint=Color.Transparent
    )
    MaterialTheme(colorScheme=scheme,
        shapes=Shapes(extraSmall=RoundedCornerShape(6.dp), small=IdeDesign.compact,
            medium=IdeDesign.card, large=RoundedCornerShape(20.dp), extraLarge=RoundedCornerShape(24.dp)),
        typography=Typography(
            headlineLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=30.sp,lineHeight=36.sp,fontWeight=FontWeight.SemiBold),
            headlineSmall=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=24.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold),
            titleLarge=TextStyle(fontSize=20.sp,lineHeight=26.sp,fontWeight=FontWeight.SemiBold),
            titleMedium=TextStyle(fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.Medium),
            bodyLarge=TextStyle(fontSize=16.sp,lineHeight=24.sp),
            bodyMedium=TextStyle(fontSize=14.sp,lineHeight=20.sp),
            bodySmall=TextStyle(fontSize=12.sp,lineHeight=18.sp),
            labelLarge=TextStyle(fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium),
            labelMedium=TextStyle(fontSize=12.sp,lineHeight=16.sp,fontWeight=FontWeight.Medium),
            labelSmall=TextStyle(fontSize=11.sp,lineHeight=16.sp,fontWeight=FontWeight.Medium)
        ),content=content)
}

@Composable
fun IdePageHeading(title: String, subtitle: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min=60.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(3.dp).height(32.dp).background(MaterialTheme.colorScheme.primary,
            RoundedCornerShape(3.dp)))
        Column(Modifier.weight(1f)) {
            Text(title,style=MaterialTheme.typography.headlineSmall,color=IdeDesign.text)
            if(subtitle.isNotBlank()) Text(subtitle,style=MaterialTheme.typography.bodySmall,
                color=IdeDesign.muted,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun IdeWorkspaceHero(project: String, files: Int, onOpen: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(onClick=onOpen, color=IdeDesign.surface, shape=IdeDesign.card,
        border=BorderStroke(1.dp,accent.copy(alpha=.35f)),modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min=140.dp)) {
            Box(Modifier.width(6.dp).height(140.dp).background(accent))
            Column(Modifier.weight(1f).padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("CURRENT WORKSPACE",color=accent,style=MaterialTheme.typography.labelSmall,
                    fontWeight=FontWeight.Bold,letterSpacing=1.2.sp)
                Text(project,color=IdeDesign.text,style=MaterialTheme.typography.headlineSmall,
                    maxLines=1,overflow=TextOverflow.Ellipsis)
                Text("$files Python files  ·  Open workspace",color=IdeDesign.muted,
                    style=MaterialTheme.typography.bodyMedium)
            }
            Box(Modifier.padding(18.dp).size(44.dp).background(accent.copy(alpha=.18f),IdeDesign.compact),
                contentAlignment=Alignment.Center) { IdeGlyph("Folders",accent,Modifier.size(24.dp)) }
        }
    }
}

@Composable
fun IdeAssistantWelcome() {
    val accent=MaterialTheme.colorScheme.primary
    Surface(color=accent.copy(alpha=.13f),shape=IdeDesign.card,
        border=BorderStroke(1.dp,accent.copy(alpha=.34f)),modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(52.dp).background(accent.copy(alpha=.20f),IdeDesign.card),
                contentAlignment=Alignment.Center) { IdeGlyph("Astro",accent,Modifier.size(28.dp)) }
            Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text("ASTRO · YOUR CODING PARTNER",color=accent,
                    style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                Text("What are we building?",color=IdeDesign.text,style=MaterialTheme.typography.titleLarge)
                Text("Explore ideas, understand code, or review a change together.",
                    color=IdeDesign.muted,style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun IdeActionTile(title: String, subtitle: String = "", glyph: String, enabled: Boolean = true,
                  motion: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed && motion && ValueAnimator.areAnimatorsEnabled()) .98f else 1f,
        animationSpec=if(motion && ValueAnimator.areAnimatorsEnabled()) spring(dampingRatio=1f,stiffness=Spring.StiffnessHigh) else tween(0),label="tile feedback")
    val accent=MaterialTheme.colorScheme.primary
    Surface(color=accent.copy(alpha=.11f),shape=IdeDesign.card,border=BorderStroke(1.dp,accent.copy(alpha=.22f)),
        modifier=modifier.graphicsLayer { scaleX=scale;scaleY=scale;alpha=if(enabled) 1f else .45f }
            .clickable(enabled=enabled,interactionSource=interaction,indication=ripple(),onClick=onClick)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(38.dp).background(accent.copy(alpha=.17f),IdeDesign.compact),contentAlignment=Alignment.Center) {
                IdeGlyph(glyph,accent,Modifier.size(22.dp))
            }
            Column {
                Text(title,color=IdeDesign.text,style=MaterialTheme.typography.titleMedium)
                if(subtitle.isNotBlank()) Text(subtitle,color=IdeDesign.muted,style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun IdeEmptyState(title: String, description: String, glyph: String, modifier: Modifier = Modifier,
                  action: @Composable () -> Unit = {}) {
    Column(modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(56.dp).background(IdeDesign.raised,IdeDesign.card),contentAlignment=Alignment.Center) {
            IdeGlyph(glyph,IdeDesign.muted,Modifier.size(26.dp))
        }
        Text(title,style=MaterialTheme.typography.titleMedium,color=IdeDesign.text)
        Text(description,style=MaterialTheme.typography.bodyMedium,color=IdeDesign.muted,
            textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        action()
    }
}

@Composable
fun IdeNavigation(selected: Int, height: Float, onSelected: (Int) -> Unit) {
    val accent=MaterialTheme.colorScheme.primary
    NavigationBar(containerColor=IdeDesign.surface,tonalElevation=0.dp,
        windowInsets=WindowInsets(0,0,0,0),modifier=Modifier.height(height.coerceIn(64f,88f).dp)) {
        listOf("Home" to "Home","Code" to "Code","Console" to "Console","Astro" to "Astro","Settings" to "Settings")
            .forEachIndexed { index,(title,glyph) ->
                NavigationBarItem(selected=selected==index,onClick={onSelected(index)},
                    icon={Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        IdeGlyph(glyph,if(selected==index) accent else IdeDesign.muted)
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.width(if(selected==index) 14.dp else 0.dp).height(2.dp)
                            .background(accent, RoundedCornerShape(2.dp)))
                    }},
                    label={Text(title,fontSize=11.sp,maxLines=1)},
                    colors=NavigationBarItemDefaults.colors(
                        selectedIconColor=MaterialTheme.colorScheme.primary,selectedTextColor=IdeDesign.text,
                        unselectedIconColor=IdeDesign.muted,unselectedTextColor=IdeDesign.muted,
                        indicatorColor=accent.copy(alpha=.18f)))
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeSheet(title: String, subtitle: String = "", onDismiss: () -> Unit,
             content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=IdeDesign.surface,
        contentColor=IdeDesign.text,shape=IdeDesign.sheet,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).navigationBarsPadding()
            .padding(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            IdePageHeading(title,subtitle)
            content()
        }
    }
}

@Composable
fun IdeSheetAction(label: String, glyph: String, enabled: Boolean = true, destructive: Boolean = false,
                   onClick: () -> Unit) {
    val tint=if(destructive) IdeDesign.error else IdeDesign.text
    Surface(onClick=onClick,enabled=enabled,color=Color.Transparent,shape=IdeDesign.compact,
        modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal=12.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            IdeGlyph(glyph,tint.copy(alpha=if(enabled) 1f else .38f))
            Text(label,color=tint.copy(alpha=if(enabled) 1f else .38f),style=MaterialTheme.typography.bodyLarge)
        }
    }
}
