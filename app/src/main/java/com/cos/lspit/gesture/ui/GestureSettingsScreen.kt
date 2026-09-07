package com.cos.lspit.gesture.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cos.lspit.gesture.config.ConfigStore
import com.cos.lspit.gesture.config.GestureConfig
import com.cos.lspit.gesture.R
import com.cos.lspit.gesture.restart.Capability
import com.cos.lspit.gesture.restart.RestartResult
import com.cos.lspit.gesture.restart.ScopeRestarter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun GestureSettingsScreen() {
    val context = LocalContext.current
    var config by remember {
        mutableStateOf(ConfigStore.load(context))
    }
    var statusLine by remember {
        mutableStateOf(readStatusLine(context))
    }
    var showConfirm by remember {
        mutableStateOf(false)
    }
    var snackbarMessage by remember {
        mutableStateOf<String?>(null)
    }

    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = stringResource(R.string.settings_title))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                SmallTitle(stringResource(R.string.section_scope))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        MiuixText(
                            text = stringResource(R.string.status_label),
                            style = androidx.compose.material3.MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        MiuixText(
                            text = statusLine,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { showConfirm = true }) {
                            MiuixText(stringResource(R.string.btn_restart))
                        }
                    }
                }
            }
            item {
                SmallTitle(stringResource(R.string.settings_title))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SwitchRow(
                            label = stringResource(R.string.switch_master),
                            checked = config.masterEnabled,
                            enabled = true,
                            onCheckedChange = { on ->
                                config = config.copy(masterEnabled = on)
                                ConfigStore.save(context, config)
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        SwitchRow(
                            label = stringResource(R.string.switch_left),
                            checked = config.masterEnabled && config.leftEnabled,
                            enabled = config.masterEnabled,
                            onCheckedChange = { on ->
                                config = config.copy(leftEnabled = on)
                                ConfigStore.save(context, config)
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        SwitchRow(
                            label = stringResource(R.string.switch_right),
                            checked = config.masterEnabled && config.rightEnabled,
                            enabled = config.masterEnabled,
                            onCheckedChange = { on ->
                                config = config.copy(rightEnabled = on)
                                ConfigStore.save(context, config)
                            }
                        )
                    }
                }
            }
        }
    }

    snackbarMessage?.let { message ->
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        snackbarMessage = null
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = {
                Text(stringResource(R.string.dialog_restart_title))
            },
            text = {
                Text(stringResource(R.string.dialog_restart_message))
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    scope.launch {
                        val capability = ScopeRestarter.detect(context)
                        val result = ScopeRestarter.restart(context, capability)
                        snackbarMessage = when (result) {
                            RestartResult.Success -> context.getString(R.string.restart_result_ok)
                            RestartResult.Manual -> context.getString(R.string.restart_result_manual)
                            is RestartResult.Failure -> context.getString(
                                R.string.restart_result_manual,
                                result.reason
                            )
                        }
                    }
                }) {
                    Text(stringResource(R.string.dialog_restart_title))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        MiuixText(
            text = label,
            modifier = Modifier.weight(1f),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
        )
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

private fun readStatusLine(context: Context): String {
    val prefs = context.getSharedPreferences("gesture_status", Context.MODE_PRIVATE)
    val outcome = prefs.getString("outcome", null) ?: return context.getString(R.string.status_unknown)
    val atMillis = prefs.getLong("at_millis", 0L)
    val time = if (atMillis > 0) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(atMillis))
    } else {
        ""
    }
    val detail = prefs.getString("detail", "")
    return "$outcome $detail $time".trim()
}