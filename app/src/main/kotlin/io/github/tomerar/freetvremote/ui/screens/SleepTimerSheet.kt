package io.github.tomerar.freetvremote.ui.screens

import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.tomerar.freetvremote.R
import io.github.tomerar.freetvremote.timer.SleepOutcome
import io.github.tomerar.freetvremote.timer.SleepTimer
import io.github.tomerar.freetvremote.timer.SleepTimerLimits
import io.github.tomerar.freetvremote.timer.SleepTimerNotifier
import io.github.tomerar.freetvremote.timer.SleepTimerResult
import io.github.tomerar.freetvremote.ui.SleepTimerUiState
import io.github.tomerar.freetvremote.ui.formatCountdown
import io.github.tomerar.freetvremote.ui.hoursAndMinutes
import io.github.tomerar.freetvremote.ui.parseMinutes
import io.github.tomerar.freetvremote.ui.stepMinutes
import kotlinx.coroutines.delay
import java.util.Date

private const val TICK_MS = 1_000L
private const val MINUTES_DIGITS_MAX = 3
private const val MS_PER_MINUTE = 60_000L
private val EXTEND_CHOICES = listOf(5, 15, 30)
private val RING_SIZE = 240.dp

/** The two clocks the sheet shows, refreshed every second while it is on screen. */
@Composable
internal fun rememberTimerClock(): State<Pair<Long, Long>> {
    val now = remember { mutableStateOf(SystemClock.elapsedRealtime() to System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now.value = SystemClock.elapsedRealtime() to System.currentTimeMillis()
            delay(TICK_MS)
        }
    }
    return now
}

/** Start, watch, extend and cancel the sleep timer of the selected TV. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepTimerSheet(
    state: SleepTimerUiState,
    tvName: String?,
    actions: SleepTimerActions,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SleepTimerContent(state, tvName, actions)
    }
}

/** The sheet's content without the sheet window, so it can also be drawn on its own (previews, screenshots). */
@Composable
internal fun SleepTimerContent(state: SleepTimerUiState, tvName: String?, actions: SleepTimerActions) {
    val timer = state.active
    val name = timer?.tvName ?: tvName
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Header(
            subtitle =
                name?.let {
                    stringResource(if (timer != null) R.string.timer_subtitle_running else R.string.timer_subtitle_new, it)
                },
        )
        if (timer != null) {
            RunningTimer(timer, actions)
        } else {
            state.last?.let { LastResult(it, actions.onDismissResult) }
            NewTimer(hasTv = tvName != null, initialMinutes = state.initialMinutes, onStart = actions.onStart)
        }
        Notices(state, actions)
    }
}

@Composable
private fun Header(subtitle: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Column {
            Text(stringResource(R.string.timer_title), style = MaterialTheme.typography.titleLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewTimer(hasTv: Boolean, initialMinutes: Int, onStart: (Int) -> Unit) {
    // Starts from the last duration the user asked for; keyed so it follows the stored value once it is loaded.
    var text by rememberSaveable(initialMinutes) { mutableStateOf(initialMinutes.toString()) }
    val minutes = parseMinutes(text)
    val clock by rememberTimerClock()

    MinutesPicker(
        text = text,
        minutes = minutes,
        onTextChange = { text = it },
        onStep = { up -> text = stepMinutes(minutes ?: initialMinutes, up).toString() },
    )
    Text(
        text =
            minutes?.let { stringResource(R.string.timer_ends_at, timeText(clock.second + it * MS_PER_MINUTE)) }
                ?: stringResource(R.string.timer_custom_hint),
        style = MaterialTheme.typography.bodyLarge,
        color = if (minutes == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.heightIn(min = 24.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SleepTimerLimits.PRESETS.forEach { preset ->
            val selected = minutes == preset
            FilterChip(
                selected = selected,
                onClick = { text = preset.toString() },
                label = { Text(durationText(preset)) },
                leadingIcon =
                    if (selected) {
                        (
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        )
                    } else {
                        null
                    },
            )
        }
    }
    Button(
        onClick = { minutes?.let(onStart) },
        enabled = minutes != null && hasTv,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.timer_start), modifier = Modifier.padding(start = 8.dp))
    }
    if (!hasTv) {
        Text(stringResource(R.string.timer_no_tv), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** A big number you can type into, with minus and plus on its sides, like the timer of the Clock app. */
@Composable
private fun MinutesPicker(text: String, minutes: Int?, onTextChange: (String) -> Unit, onStep: (up: Boolean) -> Unit) {
    val label = stringResource(R.string.timer_custom_label)
    val shorter = stringResource(R.string.timer_shorter)
    val longer = stringResource(R.string.timer_longer)
    // Like the keys of the remote: minus on the left and plus on the right in every language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalIconButton(onClick = { onStep(false) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.Remove, contentDescription = shorter)
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicTextField(
                        value = text,
                        onValueChange = { input -> onTextChange(input.filter(Char::isDigit).take(MINUTES_DIGITS_MAX)) },
                        singleLine = true,
                        textStyle =
                            MaterialTheme.typography.displayMedium.copy(
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum",
                                color = if (minutes == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                    )
                    Text(
                        text =
                            minutes?.takeIf { it >= MINUTES_PER_HOUR }?.let { durationText(it) }
                                ?: stringResource(R.string.timer_unit_minutes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                FilledTonalIconButton(onClick = { onStep(true) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = longer)
                }
            }
        }
    }
}

@Composable
private fun RunningTimer(timer: SleepTimer, actions: SleepTimerActions) {
    val clock by rememberTimerClock()
    val remaining = timer.remainingMs(clock.first)
    val running = timer.phase == SleepTimer.Phase.RUNNING
    // The ring shrinks as the time runs out.
    val progress = if (timer.durationMs > 0) (remaining.toFloat() / timer.durationMs).coerceIn(0f, 1f) else 0f
    val countdown = formatCountdown(remaining)
    val left = stringResource(R.string.timer_left)

    Box(modifier = Modifier.size(RING_SIZE), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 12.dp,
            trackColor = MaterialTheme.colorScheme.secondaryContainer,
            strokeCap = StrokeCap.Round,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            Text(
                // At zero the alarm is about to go off (or the TV is being switched off): never show a frozen 00:00.
                text = if (running || remaining == 0L) stringResource(R.string.timer_shutting_down) else countdown,
                style =
                    if (running || remaining == 0L) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum")
                    },
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                // Read out as a whole, not on every tick.
                modifier = Modifier.semantics { contentDescription = "$left $countdown" },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    Icons.Filled.Alarm,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.timer_ends_at, timeText(clock.second + remaining)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        EXTEND_CHOICES.forEach { minutes ->
            FilledTonalButton(
                onClick = { actions.onExtend(minutes) },
                enabled = !running,
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.timer_extend, minutes), maxLines = 1, softWrap = false)
            }
        }
    }
    OutlinedButton(
        onClick = actions.onCancel,
        enabled = !running,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = if (running) 0.3f else 1f)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.timer_cancel), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun LastResult(result: SleepTimerResult, onDismiss: () -> Unit) {
    val (icon, container, content) = resultLook(result.outcome)
    Surface(shape = RoundedCornerShape(20.dp), color = container, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.padding(top = 2.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(SleepTimerNotifier.resultText(result.outcome), result.tvName),
                        style = MaterialTheme.typography.bodyLarge,
                        color = content,
                    )
                    Text(timeText(result.atWallMs), style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.7f))
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.timer_dismiss), color = content)
            }
        }
    }
}

/** Success is green-ish (primary), plain information is neutral, a problem uses the error colors. */
@Composable
private fun resultLook(outcome: SleepOutcome): Triple<ImageVector, Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (outcome) {
        SleepOutcome.TURNED_OFF -> {
            Triple(Icons.Filled.CheckCircle, scheme.primaryContainer, scheme.onPrimaryContainer)
        }

        SleepOutcome.SENT, SleepOutcome.ALREADY_OFF, SleepOutcome.TV_REMOVED -> {
            Triple(Icons.Filled.Info, scheme.surfaceContainerHigh, scheme.onSurface)
        }

        else -> {
            Triple(Icons.Filled.Warning, scheme.errorContainer, scheme.onErrorContainer)
        }
    }
}

@Composable
private fun Notices(state: SleepTimerUiState, actions: SleepTimerActions) {
    if (!state.exactAllowed) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        stringResource(R.string.timer_exact_needed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                TextButton(onClick = actions.onOpenExactSettings, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.timer_exact_grant))
                }
            }
        }
    }
    if (!state.notificationsAllowed) {
        NoticeRow(Icons.Filled.NotificationsOff, stringResource(R.string.timer_notifications_off))
    }
    NoticeRow(Icons.Filled.Info, stringResource(R.string.timer_note))
    NoticeRow(Icons.Filled.Alarm, stringResource(R.string.timer_alarm_note))
}

@Composable
private fun NoticeRow(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val MINUTES_PER_HOUR = 60

@Composable
private fun durationText(minutes: Int): String {
    val (hours, rest) = hoursAndMinutes(minutes)
    return when {
        hours == 0 -> stringResource(R.string.timer_duration_min, rest)
        rest == 0 -> stringResource(R.string.timer_duration_h, hours)
        else -> stringResource(R.string.timer_duration_h_min, hours, rest)
    }
}

@Composable
private fun timeText(wallMs: Long): String = DateFormat.getTimeFormat(LocalContext.current).format(Date(wallMs))
