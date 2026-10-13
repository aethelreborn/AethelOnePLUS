package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.emotes.EmoteWheelKeybind
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?}

//? if < 26.1 && > 1.8.9 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}

private val WHEEL_DIAMETER = 420.dp
private val INNER_DIAMETER = 190.dp
private val WHEEL_RADIUS = WHEEL_DIAMETER / 2
private val INNER_RADIUS = INNER_DIAMETER / 2

private const val RING_STROKE_W = 1f

private const val SLOT_COUNT = 8

private val PopInEasing = CubicBezierEasing(0.0f, 0.55f, 0.45f, 1.0f)

private data class WheelSlot(val id: Int, val name: String)

class EmoteWheelScreen : ComposeScreen(RenderMode.CONTINUOUS) {
    private val logger = LogManager.getLogger("polyplus/emote-wheel")

    //? if > 1.8.9 {
    override fun shouldCloseOnEsc(): Boolean = true

    //? if <26.1 {
    /*override fun renderBackground(ctx: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) = Unit
    *///?} else {
    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) = Unit
    //?}

    override fun onClose() {
        Minecraft.getInstance().execute { super.onClose() }
    }
    //?}

    @Composable
    override fun compose() {
        Theme {
            EmoteWheelContent(
                logger = logger,
                onRelease = { emoteId ->
                    if (emoteId != null) {
                        PolyPlusClient.SCOPE.launch {
                            CosmeticService.playEmote(emoteId)
                                .onFailure {
                                    logger.error("Failed to play emote {}", emoteId, it)
                                }
                        }
                    }
                    closeWheel()
                },
            )
        }
    }

    private fun closeWheel() {
        //? if > 1.8.9 {
        Minecraft.getInstance().execute {
        //?} else {
        /*Minecraft.getInstance().tell {
        *///?}
            val mc = Minecraft.getInstance()
            //? if >= 26.2 {
            if (mc.gui.screen() === this) mc.gui.setScreen(null)
            //?} elif > 1.8.9 {
            /*if (mc.screen === this) mc.setScreen(null)
            *///?} else {
            /*if (mc.screen === this) mc.openScreen(null)
            *///?}
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EmoteWheelContent(logger: Logger, onRelease: (Int?) -> Unit) {
    val owned = remember {
        CosmeticCatalog.allEmoteDefinitions()
            .filter { it.id in CosmeticCatalog.ownedEmoteIds() }
            .sortedBy { it.id }
    }

    if (owned.isEmpty()) {
        // Without this the wheel is a single inert ring, which reads as a bug.
        EmoteEmptyState()
        return
    }

    if (owned.size > SLOT_COUNT) {
        EmoteListContent(owned, onRelease)
        return
    }

    val slots = remember(owned) {
        List(SLOT_COUNT) { index -> owned.getOrNull(index)?.let { WheelSlot(it.id, it.name) } }
    }

    // Only divide the circle among emotes that actually exist, so a user with two
    // emotes gets two large sectors instead of two slivers among six dead ones.
    val sectorCount = owned.size.coerceAtLeast(1)
    val sectorDeg = 360f / sectorCount

    var mouseXRoot by remember { mutableFloatStateOf(0f) }
    var mouseYRoot by remember { mutableFloatStateOf(0f) }
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    var wheelBounds by remember { mutableStateOf(Rect.Zero) }

    val popIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        popIn.animateTo(1f, animationSpec = tween(180, easing = PopInEasing))
    }

    val wheelRadiusPx = with(LocalDensity.current) { WHEEL_RADIUS.toPx() }
    val innerRadiusPx = with(LocalDensity.current) { INNER_RADIUS.toPx() }

    val hoveredSlot by remember(mouseXRoot, mouseYRoot, popIn.value, sectorCount) {
        derivedStateOf {
            if (popIn.value <= 0.01f) return@derivedStateOf -1
            val cx = wheelBounds.center.x
            val cy = wheelBounds.center.y
            val dx = mouseXRoot - cx
            val dy = mouseYRoot - cy
            val dist = sqrt(dx * dx + dy * dy)
            if (dist in innerRadiusPx * popIn.value..wheelRadiusPx * popIn.value) {
                val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f + 360f) % 360f
                (angle / sectorDeg).toInt().coerceIn(0, sectorCount - 1)
            } else {
                -1
            }
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos {}
            if (!EmoteWheelKeybind.isHeld()) {
                onRelease(slots.getOrNull(hoveredSlot)?.id)
                break
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootPos = it.positionInRoot() }
            .onPointerEvent(PointerEventType.Move) { event ->
                val pos = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                mouseXRoot = pos.x + rootPos.x
                mouseYRoot = pos.y + rootPos.y
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(WHEEL_DIAMETER), contentAlignment = Alignment.Center) {
            WheelCanvas(
                popInValue = popIn.value,
                hoveredSlot = hoveredSlot,
                slots = slots,
                sectorCount = sectorCount,
                sectorDeg = sectorDeg,
                onLayout = { wheelBounds = it },
            )

            val hoveredName = slots.getOrNull(hoveredSlot)?.name
            if (hoveredName != null) {
                Box(
                    modifier = Modifier.widthIn(max = INNER_DIAMETER - 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = hoveredName,
                        style = TextStyle(
                            color = LocalTheme.current.textColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = LocalTheme.current.typography.family,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmoteEmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.widthIn(max = 260.dp),
        ) {
            BasicText(
                text = "No emotes yet",
                style = TextStyle(
                    color = LocalTheme.current.textColor,
                    fontSize = 17.sp,
                    fontFamily = LocalTheme.current.typography.family,
                ),
            )
            BasicText(
                text = "Unlock emotes in the PolyPlus cosmetics menu to use them here.",
                style = TextStyle(
                    color = LocalTheme.current.textColor.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontFamily = LocalTheme.current.typography.family,
                ),
            )
        }
    }
}

@Composable
private fun EmoteListContent(
    owned: List<CosmeticDefinition>,
    onRelease: (Int?) -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (emote in owned) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRelease(emote.id) }
                    .background(LocalTheme.current.componentBackground)
                    .padding(12.dp),
            ) {
                BasicText(
                    text = emote.name,
                    style = TextStyle(
                        color = LocalTheme.current.textColor,
                        fontSize = 16.sp,
                        fontFamily = LocalTheme.current.typography.family,
                    ),
                )
            }
        }
    }
}

@Composable
private fun WheelCanvas(
    popInValue: Float,
    hoveredSlot: Int,
    slots: List<WheelSlot?>,
    sectorCount: Int,
    sectorDeg: Float,
    onLayout: (Rect) -> Unit,
) {
    val accent = Accent
    val filledColor = LocalTheme.current.chipBackground
    val emptyColor = LocalTheme.current.componentBackground
    val ringColor = LocalTheme.current.borderColor
    Canvas(
        modifier = Modifier
            .size(WHEEL_DIAMETER)
            .onGloballyPositioned { onLayout(it.boundsInRoot()) },
    ) {
        if (popInValue <= 0.01f) return@Canvas

        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerR = WHEEL_RADIUS.toPx()
        val innerR = INNER_RADIUS.toPx()
        val midR = (outerR + innerR) / 2f
        val thickness = outerR - innerR

        scale(popInValue) {
            val arcTL = Offset(cx - midR, cy - midR)
            val arcSize = Size(midR * 2, midR * 2)
            val center = Offset(cx, cy)

            for (i in 0 until sectorCount) {
                val startDeg = -90f + i * sectorDeg
                val hasEmote = slots.getOrNull(i) != null
                val isHovered = i == hoveredSlot && hasEmote

                drawArc(
                    color = if (isHovered) accent else if (hasEmote) filledColor else emptyColor,
                    startAngle = startDeg, sweepAngle = sectorDeg, useCenter = false,
                    topLeft = arcTL, size = arcSize, style = Stroke(thickness),
                )
            }

            drawCircle(color = ringColor, radius = outerR + RING_STROKE_W / 2f, center = center, style = Stroke(RING_STROKE_W))
            drawCircle(color = ringColor, radius = innerR - RING_STROKE_W / 2f, center = center, style = Stroke(RING_STROKE_W))
        }
    }
}
