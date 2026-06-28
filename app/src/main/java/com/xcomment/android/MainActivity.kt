package com.xcomment.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xcomment.android.data.*
import com.xcomment.android.ui.theme.XCommentTheme
import kotlinx.coroutines.delay

private interface HasLabel {
    val labelRes: Int
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XCommentTheme {
                MainScreen()
            }
        }
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    var showSettings by remember { mutableStateOf(!settings.hasApiKey) }

    // Live permission state
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var overlayGranted by remember { mutableStateOf(Permissions.canDrawOverlays(context)) }
    var accessibilityGranted by remember { mutableStateOf(Permissions.isAccessibilityEnabled(context)) }

    // Refresh on resume
    LaunchedEffect(lifecycle) {
        while (true) {
            val state = lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.RESUMED)) {
                overlayGranted = Permissions.canDrawOverlays(context)
                accessibilityGranted = Permissions.isAccessibilityEnabled(context)
            }
            delay(800)
        }
    }

    val setupSteps = 3
    val completedSteps = listOfNotNull(
        overlayGranted.takeIf { it },
        accessibilityGranted.takeIf { it },
        settings.hasApiKey.takeIf { it },
    ).size
    val setupComplete = completedSteps == setupSteps

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            // Header
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            // Setup card
            StatusCard(
                complete = setupComplete,
                completedSteps = completedSteps,
                totalSteps = setupSteps,
            )

            // Permission steps
            PermissionRow(
                icon = Icons.Outlined.BubbleChart,
                title = stringResource(R.string.perm_overlay_title),
                description = stringResource(R.string.perm_overlay_desc),
                granted = overlayGranted,
                actionLabel = stringResource(R.string.action_grant),
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                    context.startActivity(intent)
                },
            )

            PermissionRow(
                icon = Icons.Outlined.Accessible,
                title = stringResource(R.string.perm_accessibility_title),
                description = stringResource(R.string.perm_accessibility_desc),
                granted = accessibilityGranted,
                actionLabel = stringResource(R.string.action_enable),
                onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            )

            PermissionRow(
                icon = Icons.Outlined.Key,
                title = stringResource(R.string.perm_apikey_title),
                description = stringResource(R.string.perm_apikey_desc),
                granted = settings.hasApiKey,
                actionLabel = stringResource(R.string.action_open),
                onClick = { showSettings = !showSettings },
            )

            // Settings panel
            AnimatedVisibility(visible = showSettings) {
                SettingsPanel(
                    settings = settings,
                    onSave = { newSettings ->
                        settings = newSettings
                        store.save(newSettings)
                    },
                )
            }

            Spacer(Modifier.height(4.dp))

            // How to use
            HowToSection()

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusCard(complete: Boolean, completedSteps: Int, totalSteps: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (complete) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (complete) Icons.Outlined.CheckCircle else Icons.Outlined.Settings,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (complete) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Column {
                Text(
                    text = stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (complete) {
                        stringResource(R.string.setup_complete)
                    } else {
                        stringResource(R.string.setup_progress, completedSteps, totalSteps)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
) {
    OutlinedCard {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = stringResource(if (granted) R.string.status_granted else R.string.status_missing),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (granted) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                    )
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!granted) {
                FilledTonalButton(onClick = onClick) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(settings: Settings, onSave: (Settings) -> Unit) {
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var apiUrl by remember { mutableStateOf(settings.apiUrl) }
    var model by remember { mutableStateOf(settings.model) }
    var tone by remember { mutableStateOf(settings.tone) }
    var language by remember { mutableStateOf(settings.language) }
    var count by remember { mutableStateOf(settings.count) }
    var keyVisible by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved) {
            delay(2000)
            saved = false
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.label_api_key)) },
                placeholder = { Text(stringResource(R.string.hint_api_key)) },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            imageVector = if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = stringResource(if (keyVisible) R.string.hide else R.string.show),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                label = { Text(stringResource(R.string.label_api_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text(stringResource(R.string.label_model)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            EnumDropdown(
                label = stringResource(R.string.label_tone),
                value = tone,
                values = Tone.entries,
                onSelect = { tone = it },
            )

            EnumDropdown(
                label = stringResource(R.string.label_language),
                value = language,
                values = ReplyLanguage.entries,
                onSelect = { language = it },
            )

            OutlinedTextField(
                value = count.toString(),
                onValueChange = { count = it.toIntOrNull()?.coerceIn(SettingsStore.MIN_COUNT, SettingsStore.MAX_COUNT) ?: count },
                label = { Text(stringResource(R.string.label_count)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val new = Settings(apiKey, apiUrl, model, tone, language, count)
                    onSave(new)
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (saved) stringResource(R.string.saved) else stringResource(R.string.action_save),
                )
            }
        }
    }
}

@Composable
private fun <T> EnumDropdown(
    label: String,
    value: T,
    values: List<T>,
    onSelect: (T) -> Unit,
) where T : Enum<T>, T : HasLabel {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = context.getString(value.labelRes),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { item ->
                DropdownMenuItem(
                    text = { Text(context.getString(item.labelRes)) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun HowToSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.howto_title),
            style = MaterialTheme.typography.titleMedium,
        )
        HowToStep(1, stringResource(R.string.howto_1))
        HowToStep(2, stringResource(R.string.howto_2))
        HowToStep(3, stringResource(R.string.howto_3))
        HowToStep(4, stringResource(R.string.howto_4))

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(R.string.safety_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun HowToStep(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
