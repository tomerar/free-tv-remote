package io.github.tomerar.freetvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tomerar.freetvremote.remote.KeyBehavior
import io.github.tomerar.freetvremote.remote.KeyGestures
import io.github.tomerar.freetvremote.ui.Haptics

/** One key of a rocker: its code, icon and spoken name. */
class RockerKey(
    val code: Int,
    val icon: ImageVector,
    val description: String,
)

/**
 * A vertical rocker like VOL and CH on a physical remote: a small label above, "up" at the top and "down" at the bottom
 * of one pill. Both ends repeat while held.
 */
@Composable
fun KeyRocker(
    label: String,
    groupDescription: String,
    up: RockerKey,
    down: RockerKey,
    gestures: KeyGestures,
    haptics: Haptics,
    onAccessibilityClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.semantics { contentDescription = groupDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier =
                Modifier
                    .width(ROCKER_WIDTH)
                    .height(ROCKER_HEIGHT)
                    .clip(RoundedCornerShape(ROCKER_WIDTH / 2))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(up, down).forEach { key ->
                RemoteKeyButton(
                    icon = key.icon,
                    description = key.description,
                    code = key.code,
                    gestures = gestures,
                    haptics = haptics,
                    onAccessibilityClick = onAccessibilityClick,
                    behavior = KeyBehavior.REPEAT,
                    size = ROCKER_WIDTH - 8.dp,
                    container = Color.Transparent,
                )
            }
        }
    }
}

val ROCKER_WIDTH = 64.dp
val ROCKER_HEIGHT = 148.dp
