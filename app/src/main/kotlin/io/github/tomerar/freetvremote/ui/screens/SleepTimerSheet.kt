package io.github.tomerar.freetvremote.ui.screens

import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
private val EXTEND_CHOICES = listOf(5, 15, 30)

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
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.timer_title), style = MaterialTheme.typography.titleLarge)
        val timer = state.active
        val name = timer?.tvName ?: tvName
        name?.let {
            Text(
                stringResource(R.string.timer_for_tv, it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (timer != null) {
            RunningTimer(timer, actions)
        } else {
            state.last?.let { LastResult(it, actions.onDismissResult) }
            NewTimer(hasTv = tvName != null, onStart = actions.onStart)
        }
        Notices(state, actions)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewTimer(hasTv: Boolean, onStart: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(SleepTimerLimits.DEFAULT_MINUTES.toString()) }
    val minutes = parseMinutes(text)
    val clock by rememberTimerClock()

    Text(
        text = minutes?.let { durationText(it) } ?: "—",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Text(
        text = minutes?.let { stringResource(R.string.timer_ends_at, timeText(clock.second + it * MS_PER_MINUTE)) }.orEmpty(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.heightIn(min = 20.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SleepTimerLimits.PRESETS.forEach { preset ->
            FilterChip(
                selected = minutes == preset,
                onClick = { text = preset.toString() },
                label = { Text(durationText(preset)) },
            )
        }
    }
    // Like the keys of the remote: minus on the left and plus on the right in every language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val shorter = stringResource(R.string.timer_shorter)
            IconButton(onClick = {
                text = stepMinutes(minutes ?: SleepTimerLimits.DEFAULT_MINUTES, up = false).toString()
            }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = shorter)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter(Char::isDigit).take(MINUTES_DIGITS_MAX) },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.timer_custom_label)) },
                supportingText = { Text(stringResource(R.string.timer_custom_hint)) },
                isError = text.isNotEmpty() && minutes == null,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            val longer = stringResource(R.string.timer_longer)
            IconButton(onClick = {
                text = stepMinutes(minutes ?: SleepTimerLimits.DEFAULT_MINUTES, up = true).toString()
            }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Add, contentDescription = longer)
            }
        }
    }
    Button(
        onClick = { minutes?.let(onStart) },
        enabled = minutes != null && hasTv,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text(stringResource(R.string.timer_start)) }
    if (!hasTv) {
        Text(stringResource(R.string.timer_no_tv), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun RunningTimer(timer: SleepTimer, actions: SleepTimerActions) {
    val clock by rememberTimerClock()
    val remaining = timer.remainingMs(clock.first)
    val running = timer.phase == SleepTimer.Phase.RUNNING
    // The bar shrinks as the time runs out.
    val progress = if (timer.durationMs > 0) (remaining.toFloat() / timer.durationMs).coerceIn(0f, 1f) else 0f
    val countdown = formatCountdown(remaining)
    val left = stringResource(R.string.timer_left)

    Text(
        text = if (running) stringResource(R.string.timer_shutting_down) else countdown,
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.SemiBold,
        // Tabular digits: the number does not jitter every second. Read out as a whole, not on every tick.
        modifier = Modifier.semantics { contentDescription = "$left $countdown" },
    )
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    Text(
        stringResource(R.string.timer_ends_at, timeText(clock.second + remaining)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        EXTEND_CHOICES.forEach { minutes ->
            OutlinedButton(
                onClick = { actions.onExtend(minutes) },
                enabled = !running,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.timer_extend, minutes))
            }
        }
    }
    FilledTonalButton(
        onClick = actions.onCancel,
        enabled = !running,
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text(stringResource(R.string.timer_cancel)) }
}

@Composable
private fun LastResult(result: SleepTimerResult, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(SleepTimerNotifier.resultText(result.outcome), result.tvName), style = MaterialTheme.typography.bodyMedium)
            Text(
                timeText(result.atWallMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.timer_dismiss)) }
        }
    }
}

@Composable
private fun Notices(state: SleepTimerUiState, actions: SleepTimerActions) {
    if (!state.exactAllowed) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.timer_exact_needed), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = actions.onOpenExactSettings, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.timer_exact_grant))
                }
            }
        }
    }
    if (!state.notificationsAllowed) {
        Text(
            stringResource(R.string.timer_notifications_off),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        stringResource(R.string.timer_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val MS_PER_MINUTE = 60_000L

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
