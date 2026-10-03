package io.github.tomerar.freetvremote.ui.screens

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomerar.freetvremote.BuildConfig
import io.github.tomerar.freetvremote.R
import io.github.tomerar.freetvremote.data.ThemeMode
import io.github.tomerar.freetvremote.ui.LocalAppContainer
import io.github.tomerar.freetvremote.ui.SettingsViewModel
import io.github.tomerar.freetvremote.ui.simpleFactory

private const val SOURCE_URL = "https://github.com/tomerar/free-tv-remote"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onManageTvs: () -> Unit, onEditShortcuts: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel(factory = simpleFactory { SettingsViewModel(container) })
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionTitle(R.string.settings_section_remote)
            SwitchRow(R.string.settings_haptics, R.string.settings_haptics_desc, settings.hapticsEnabled, vm::setHaptics)
            SwitchRow(R.string.settings_keep_screen_on, R.string.settings_keep_screen_on_desc, settings.keepScreenOn, vm::setKeepScreenOn)
            SwitchRow(R.string.settings_volume_keys, R.string.settings_volume_keys_desc, settings.useVolumeKeys, vm::setUseVolumeKeys)

            SectionTitle(R.string.settings_theme)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow(R.string.settings_dynamic_color, R.string.settings_dynamic_color_desc, settings.dynamicColor, vm::setDynamicColor)
            }
            Column(Modifier.selectableGroup()) {
                ThemeOption(R.string.theme_system, ThemeMode.SYSTEM, settings.theme, vm::setTheme)
                ThemeOption(R.string.theme_dark, ThemeMode.DARK, settings.theme, vm::setTheme)
                ThemeOption(R.string.theme_light, ThemeMode.LIGHT, settings.theme, vm::setTheme)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(R.string.settings_section_manage)
            ListItem(headlineContent = { Text(stringResource(R.string.tvs_title)) }, modifier = Modifier.clickable(onClick = onManageTvs))
            ListItem(
                headlineContent = { Text(stringResource(R.string.shortcuts_title)) },
                modifier = Modifier.clickable(onClick = onEditShortcuts),
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(R.string.settings_section_about)
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.about_version, "${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_COMMIT})"),
                    )
                },
                supportingContent = { Text(stringResource(R.string.about_license)) },
            )
            ListItem(headlineContent = { Text(stringResource(R.string.about_privacy)) })
            val reporter = LocalAppContainer.current.crashReporter
            val errorReport = remember { mutableStateOf(reporter.read()) }
            errorReport.value?.let { report ->
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_error_report_share)) },
                    supportingContent = { Text(stringResource(R.string.about_error_report_desc)) },
                    modifier =
                        Modifier.clickable {
                            val send =
                                Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, report)
                            context.startActivity(Intent.createChooser(send, null))
                        },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_error_report_clear)) },
                    modifier =
                        Modifier.clickable {
                            reporter.clear()
                            errorReport.value = null
                        },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_source)) },
                supportingContent = { Text(SOURCE_URL) },
                modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri())) },
            )
        }
    }
}

@Composable
private fun SectionTitle(title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: Int, description: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(description)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun ThemeOption(label: Int, mode: ThemeMode, current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = current == mode, role = Role.RadioButton, onClick = { onSelect(mode) })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = current == mode, onClick = null)
        Text(stringResource(label), modifier = Modifier.padding(start = 16.dp))
    }
}
