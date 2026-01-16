package dev.labs910.voicecode.presentation.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.labs910.voicecode.domain.model.Command
import dev.labs910.voicecode.domain.model.CommandExecution
import dev.labs910.voicecode.domain.model.CommandExecutionStatus
import dev.labs910.voicecode.domain.model.CommandType

/**
 * Command menu screen for executing Makefile targets and git commands.
 * Equivalent to iOS CommandMenuView.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandMenuScreen(
    projectCommands: List<Command>,
    generalCommands: List<Command>,
    onBack: () -> Unit,
    onExecuteCommand: (Command) -> Unit,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Commands") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onHistoryClick) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Project commands section
            if (projectCommands.isNotEmpty()) {
                item {
                    Text(
                        text = "Project Commands",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(projectCommands, key = { it.id }) { command ->
                    CommandItem(
                        command = command,
                        onExecute = { onExecuteCommand(command) }
                    )
                }
            }

            // General commands section
            if (generalCommands.isNotEmpty()) {
                item {
                    Text(
                        text = "General Commands",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }

                items(generalCommands, key = { it.id }) { command ->
                    CommandItem(
                        command = command,
                        onExecute = { onExecuteCommand(command) }
                    )
                }
            }

            // Empty state
            if (projectCommands.isEmpty() && generalCommands.isEmpty()) {
                item {
                    EmptyCommandsView()
                }
            }
        }
    }
}

@Composable
fun CommandItem(
    command: Command,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (command.isGroup) {
                            expanded = !expanded
                        } else {
                            onExecute()
                        }
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon based on command type
                Icon(
                    imageVector = if (command.isGroup) {
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore
                    } else {
                        Icons.Default.Terminal
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = command.label,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    if (command.description != null) {
                        Text(
                            text = command.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!command.isGroup) {
                    IconButton(onClick = onExecute) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Execute",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Child commands for groups
            AnimatedVisibility(visible = expanded && command.isGroup) {
                Column(
                    modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
                ) {
                    command.children?.forEach { child ->
                        CommandItem(
                            command = child,
                            onExecute = { /* Handle child execution */ }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyCommandsView(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Terminal,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No Commands Available",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Set a working directory to discover Makefile targets",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Command history screen.
 * Equivalent to iOS CommandHistoryView.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandHistoryScreen(
    executions: List<CommandExecution>,
    onBack: () -> Unit,
    onExecutionClick: (CommandExecution) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Command History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (executions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No command history",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(executions, key = { it.commandSessionId }) { execution ->
                    CommandExecutionCard(
                        execution = execution,
                        onClick = { onExecutionClick(execution) }
                    )
                }
            }
        }
    }
}

@Composable
fun CommandExecutionCard(
    execution: CommandExecution,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator
                val (statusIcon, statusColor) = when (execution.status) {
                    CommandExecutionStatus.RUNNING -> Icons.Default.HourglassTop to MaterialTheme.colorScheme.tertiary
                    CommandExecutionStatus.COMPLETED -> {
                        if (execution.isSuccess) {
                            Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
                        } else {
                            Icons.Default.Error to MaterialTheme.colorScheme.error
                        }
                    }
                    CommandExecutionStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
                }

                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = execution.shellCommand,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )

                // Duration
                execution.durationMs?.let { duration ->
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Output preview
            if (execution.outputPreview.isNotEmpty()) {
                Text(
                    text = execution.outputPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Timestamp
            Text(
                text = formatExecutionTime(execution.startTime),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    return when {
        ms < 1000 -> "${ms}ms"
        ms < 60000 -> "${ms / 1000}s"
        else -> "${ms / 60000}m ${(ms % 60000) / 1000}s"
    }
}

private fun formatExecutionTime(instant: java.time.Instant): String {
    val formatter = java.time.format.DateTimeFormatter.ofLocalizedDateTime(
        java.time.format.FormatStyle.SHORT
    ).withZone(java.time.ZoneId.systemDefault())
    return formatter.format(instant)
}
