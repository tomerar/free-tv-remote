package io.github.tomerar.freetvremote.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomerar.freetvremote.R
import io.github.tomerar.freetvremote.data.AppShortcut
import io.github.tomerar.freetvremote.ui.LocalAppContainer
import io.github.tomerar.freetvremote.ui.ShortcutsViewModel
import io.github.tomerar.freetvremote.ui.simpleFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: ShortcutsViewModel = viewModel(factory = simpleFactory { ShortcutsViewModel(container) })
    val shortcuts by vm.shortcuts.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shortcuts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.shortcuts_add)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text(stringResource(R.string.shortcuts_intro), style = MaterialTheme.typography.bodyMedium) }
            itemsIndexed(shortcuts, key = { _, s -> s.id }) { index, shortcut ->
                ShortcutRow(
                    shortcut = shortcut,
                    canMoveUp = index > 0,
                    canMoveDown = index < shortcuts.lastIndex,
                    onToggle = { vm.setEnabled(shortcut.id, it) },
                    onMove = { vm.move(shortcut.id, it) },
                    onDelete = { vm.remove(shortcut.id) },
                )
            }
            item { Column(Modifier.padding(bottom = 88.dp)) {} }
        }
    }

    if (adding) {
        AddShortcutDialog(onDismiss = { adding = false }, onAdd = { name, link -> vm.add(name, link) })
    }
}

@Composable
private fun ShortcutRow(
    shortcut: AppShortcut,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val toggleLabel = stringResource(R.string.shortcuts_enabled)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(shortcut.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    shortcut.link,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.shortcuts_move_up))
            }
            IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.shortcuts_move_down))
            }
            if (!shortcut.builtIn) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.shortcuts_delete))
                }
            }
            Switch(
                checked = shortcut.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(horizontal = 8.dp).semantics { contentDescription = "$toggleLabel: ${shortcut.name}" },
            )
        }
    }
}

@Composable
private fun AddShortcutDialog(onDismiss: () -> Unit, onAdd: suspend (String, String) -> Boolean) {
    var name by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val submit = {
        scope.launch {
            if (onAdd(name, link)) onDismiss() else invalid = true
        }
        Unit
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shortcuts_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        invalid = false
                    },
                    label = { Text(stringResource(R.string.shortcuts_name_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = {
                        link = it
                        invalid = false
                    },
                    label = { Text(stringResource(R.string.shortcuts_link_label)) },
                    placeholder = { Text(stringResource(R.string.shortcuts_link_hint)) },
                    isError = invalid,
                    supportingText = { if (invalid) Text(stringResource(R.string.shortcuts_invalid)) },
                    singleLine = true,
                    keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                )
            }
        },
        confirmButton = { TextButton(onClick = { submit() }) { Text(stringResource(R.string.action_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
