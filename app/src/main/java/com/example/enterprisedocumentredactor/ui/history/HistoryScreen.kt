package com.example.enterprisedocumentredactor.ui.history

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.enterprisedocumentredactor.domain.model.Document
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val documents by viewModel.documents.collectAsState()
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    val showDeleteAllDialog by viewModel.showDeleteAllDialog.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Trigger biometric on first composition if available
    LaunchedEffect(Unit) {
        if (viewModel.isBiometricAvailable && activity != null) {
            viewModel.authenticate(activity)
        }
    }

    // Snackbar for delete events
    LaunchedEffect(Unit) {
        viewModel.deleteEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Snackbar for auth errors
    LaunchedEffect(Unit) {
        viewModel.authError.collect { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    // Single delete confirmation dialog
    if (showDeleteDialog != null) {
        val docName = documents.find { it.id == showDeleteDialog }?.fileName ?: "this document"
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Delete Document?") },
            text = {
                Text(
                    "This will permanently delete the redacted PDF for \"$docName\" " +
                            "and remove it from history. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDelete() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text("Cancel") }
            }
        )
    }

    // Delete all confirmation dialog
    if (showDeleteAllDialog) {
        val count = documents.size
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteAll() },
            title = { Text("Clear All History?") },
            text = {
                Text(
                    "This will permanently delete all $count redacted documents and their files. " +
                            "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteAll() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteAll() }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Redaction History") },
                actions = {
                    if (documents.isNotEmpty() && isAuthenticated) {
                        IconButton(onClick = { viewModel.requestDeleteAll() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear all history",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            !isAuthenticated -> {
                // Biometric lock screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Authentication Required",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Verify your identity to view redaction history.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    androidx.compose.material3.Button(
                        onClick = { activity?.let { viewModel.authenticate(it) } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔓 Unlock with Biometric")
                    }
                }
            }

            documents.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🗂️", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No documents redacted yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Redacted documents will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                ) {
                    items(documents, key = { it.id }) { doc ->
                        SwipeableHistoryItem(
                            document = doc,
                            onRequestDelete = { viewModel.requestDelete(doc.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeableHistoryItem(
    document: Document,
    onRequestDelete: () -> Unit
) {
    val context = LocalContext.current
    var showContextMenu by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRequestDelete()
            }
            false // Always spring back — actual removal happens via Room Flow after confirm
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            // Red delete background revealed on left-swipe
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 20.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    ) {
        HistoryItemCard(
            document = document,
            showContextMenu = showContextMenu,
            onDismissMenu = { showContextMenu = false },
            onLongClick = { showContextMenu = true },
            onOpenPdf = {
                showContextMenu = false
                openPdf(context, document.redactedPath)
            },
            onDeleteFromMenu = {
                showContextMenu = false
                onRequestDelete()
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryItemCard(
    document: Document,
    showContextMenu: Boolean,
    onDismissMenu: () -> Unit,
    onLongClick: () -> Unit,
    onOpenPdf: () -> Unit,
    onDeleteFromMenu: () -> Unit
) {
    val dateStr = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
        .format(Date(document.createdAt))
    val displayName = document.fileName.take(30).let {
        if (document.fileName.length > 30) "$it…" else it
    }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .combinedClickable(
                    onClick = onOpenPdf,
                    onLongClick = onLongClick
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = Color(0xFF2E7D32),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Redacted",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${document.redactedItemCount} items redacted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = onDismissMenu
        ) {
            DropdownMenuItem(
                text = { Text("📄 Open PDF") },
                onClick = onOpenPdf
            )
            DropdownMenuItem(
                text = { Text("🗑️ Delete", color = MaterialTheme.colorScheme.error) },
                onClick = onDeleteFromMenu
            )
        }
    }
}

private fun openPdf(context: android.content.Context, redactedPath: String) {
    val file = File(redactedPath)
    if (!file.exists()) return
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) { /* no PDF viewer installed */ }
}
