package io.github.tomerar.freetvremote.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomerar.freetvremote.R
import io.github.tomerar.freetvremote.protocol.pairing.PairingSecret
import io.github.tomerar.freetvremote.remote.PairingFailure
import io.github.tomerar.freetvremote.remote.PairingState
import io.github.tomerar.freetvremote.ui.LocalAppContainer
import io.github.tomerar.freetvremote.ui.PairViewModel
import io.github.tomerar.freetvremote.ui.simpleFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairScreen(host: String, name: String, serviceName: String? = null, onBack: () -> Unit, onPaired: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: PairViewModel = viewModel(key = "pair-$host", factory = simpleFactory { PairViewModel(container, host, name, serviceName) })
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is PairingState.Success) onPaired()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pair_title, name)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.pair_cancel))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.TopCenter) {
            when (val s = state) {
                PairingState.Idle, PairingState.Connecting -> Progress(R.string.pair_connecting)
                is PairingState.AwaitingCode -> CodeEntry(invalid = s.lastCodeWasInvalid, freshCode = s.freshCode, onSubmit = vm::submit)
                PairingState.Verifying -> Progress(R.string.pair_verifying)
                is PairingState.Success -> Success()
                is PairingState.Failed -> Failure(s.reason, onRetry = vm::retry, onBack = onBack)
            }
        }
    }
}

@Composable
private fun Progress(message: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator()
        Text(stringResource(message), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Success() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.pair_success), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun CodeEntry(invalid: Boolean, freshCode: Boolean, onSubmit: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    // A restarted pairing shows a different code on the TV: the old input is useless.
    LaunchedEffect(freshCode) { if (freshCode) code = "" }
    val ready = code.length == PairingSecret.CODE_LENGTH
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.pair_enter_code), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        OutlinedTextField(
            value = code,
            onValueChange = { input ->
                code = input.uppercase().filter { it in HEX_CHARS }.take(PairingSecret.CODE_LENGTH)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.pair_code_label)) },
            isError = invalid && !freshCode,
            supportingText = {
                when {
                    freshCode -> Text(stringResource(R.string.pair_code_fresh))
                    invalid -> Text(stringResource(R.string.pair_code_invalid))
                }
            },
            singleLine = true,
            textStyle =
                MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center,
                ),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { if (ready) onSubmit(code) }),
        )
        Button(onClick = { onSubmit(code) }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pair_submit))
        }
    }
}

private const val HEX_CHARS = "0123456789ABCDEF"

@Composable
private fun Failure(reason: PairingFailure, onRetry: () -> Unit, onBack: () -> Unit) {
    val message =
        when (reason) {
            PairingFailure.UNREACHABLE -> R.string.pair_failed_unreachable
            PairingFailure.REJECTED -> R.string.pair_failed_rejected
            PairingFailure.UNEXPECTED -> R.string.pair_failed_unexpected
            PairingFailure.INTERNAL -> R.string.pair_failed_internal
        }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
        )
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_retry)) }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_back)) }
    }
}
