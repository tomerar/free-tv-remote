package io.github.tomerar.freetvremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tomerar.freetvremote.remote.KeyBehavior
import io.github.tomerar.freetvremote.remote.KeyGestures
import io.github.tomerar.freetvremote.ui.Haptics

/** Touch handling shared by every key: press feedback, haptics, key gestures and a TalkBack click action. */
@Composable
fun Modifier.remoteKey(
    code: Int,
    behavior: KeyBehavior,
    description: String,
    gestures: KeyGestures,
    haptics: Haptics,
    onPressedChange: (Boolean) -> Unit,
    onAccessibilityClick: (Int) -> Unit,
): Modifier =
    this
        .pointerInput(code, behavior, gestures, haptics) {
            detectTapGestures(
                onPress = {
                    onPressedChange(true)
                    haptics.tick()
                    gestures.down(code, behavior)
                    try {
                        tryAwaitRelease()
                    } finally {
                        onPressedChange(false)
                        gestures.up(code, behavior)
                    }
                },
            )
        }.semantics {
            role = Role.Button
            contentDescription = description
            onClick(label = description) {
                onAccessibilityClick(code)
                true
            }
        }

@Composable
fun RemoteKeyButton(
    icon: ImageVector,
    description: String,
    code: Int,
    gestures: KeyGestures,
    haptics: Haptics,
    onAccessibilityClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    behavior: KeyBehavior = KeyBehavior.TAP_OR_LONG,
    size: Dp = 56.dp,
    shape: Shape = CircleShape,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier =
            modifier
                .size(size)
                .clip(shape)
                .background(if (pressed) MaterialTheme.colorScheme.primaryContainer else container)
                .remoteKey(code, behavior, description, gestures, haptics, { pressed = it }, onAccessibilityClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor)
    }
}
