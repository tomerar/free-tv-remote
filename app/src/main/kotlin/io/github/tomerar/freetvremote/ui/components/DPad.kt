package io.github.tomerar.freetvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tomerar.freetvremote.R
import io.github.tomerar.freetvremote.protocol.remote.KeyCodes
import io.github.tomerar.freetvremote.remote.KeyBehavior
import io.github.tomerar.freetvremote.remote.KeyGestures
import io.github.tomerar.freetvremote.ui.Haptics

private const val INNER_FRACTION = 0.40f
private const val SECTOR_SWEEP = 90f
private const val SECTOR_GAP = 2f
private const val OK_GAP_FRACTION = 0.03f

/** A quarter ring: the touch area (and visible shape) of one D-pad direction. */
private class SectorShape(
    private val startAngle: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val outer = Rect(0f, 0f, size.width, size.height)
        val innerRadius = size.width / 2f * INNER_FRACTION
        val inner =
            Rect(
                size.width / 2f - innerRadius,
                size.height / 2f - innerRadius,
                size.width / 2f + innerRadius,
                size.height / 2f + innerRadius,
            )
        val sweep = SECTOR_SWEEP - 2 * SECTOR_GAP
        val path =
            Path().apply {
                arcTo(outer, startAngle + SECTOR_GAP, sweep, true)
                arcTo(inner, startAngle + SECTOR_GAP + sweep, -sweep, false)
                close()
            }
        return Outline.Generic(path)
    }
}

private class Direction(
    val code: Int,
    val startAngle: Float,
    val icon: ImageVector,
    val label: Int,
    val alignment: Alignment,
)

private val directions =
    listOf(
        Direction(KeyCodes.DPAD_UP, -135f, Icons.Filled.KeyboardArrowUp, R.string.key_up, Alignment.TopCenter),
        Direction(KeyCodes.DPAD_RIGHT, -45f, Icons.Filled.KeyboardArrowRight, R.string.key_right, Alignment.CenterEnd),
        Direction(KeyCodes.DPAD_DOWN, 45f, Icons.Filled.KeyboardArrowDown, R.string.key_down, Alignment.BottomCenter),
        Direction(KeyCodes.DPAD_LEFT, 135f, Icons.Filled.KeyboardArrowLeft, R.string.key_left, Alignment.CenterStart),
    )

/**
 * The circular D-pad. It is always laid out left-to-right: "left" must stay on the physical left
 * of the screen even in right-to-left languages, because it mirrors the TV's navigation.
 */
@Composable
fun DPad(
    gestures: KeyGestures,
    haptics: Haptics,
    onAccessibilityClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
) {
    val dpadDescription = stringResource(R.string.remote_dpad)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = modifier.size(size).semantics { contentDescription = dpadDescription }) {
            directions.forEach { direction ->
                DPadSector(direction, size, gestures, haptics, onAccessibilityClick)
            }
            DPadCenter(size, gestures, haptics, onAccessibilityClick)
        }
    }
}

@Composable
private fun DPadSector(
    direction: Direction,
    size: Dp,
    gestures: KeyGestures,
    haptics: Haptics,
    onAccessibilityClick: (Int) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val label = stringResource(direction.label)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(SectorShape(direction.startAngle))
                .background(if (pressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                .remoteKey(direction.code, KeyBehavior.REPEAT, label, gestures, haptics, { pressed = it }, onAccessibilityClick)
                .padding(size * 0.09f),
        contentAlignment = direction.alignment,
    ) {
        Icon(
            imageVector = direction.icon,
            contentDescription = null,
            modifier = Modifier.size(size * 0.13f),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DPadCenter(size: Dp, gestures: KeyGestures, haptics: Haptics, onAccessibilityClick: (Int) -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val label = stringResource(R.string.key_ok)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .size(size * (INNER_FRACTION - OK_GAP_FRACTION))
                    .clip(CircleShape)
                    .background(if (pressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                    .remoteKey(
                        KeyCodes.DPAD_CENTER,
                        KeyBehavior.TAP_OR_LONG,
                        label,
                        gestures,
                        haptics,
                        { pressed = it },
                        onAccessibilityClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (pressed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
