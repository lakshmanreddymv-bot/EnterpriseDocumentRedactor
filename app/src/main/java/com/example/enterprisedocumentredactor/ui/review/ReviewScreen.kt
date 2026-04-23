package com.example.enterprisedocumentredactor.ui.review

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.enterprisedocumentredactor.ui.components.CategorySummaryChip
import com.example.enterprisedocumentredactor.ui.components.PiiHighlightOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onNavigateToResult: () -> Unit,
    onBack: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val bitmap by viewModel.currentPageBitmap.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val currentPageIndex by viewModel.currentPageIndex.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is RedactionUiState.Success) {
            onNavigateToResult()
        }
    }

    val countByType by remember {
        derivedStateOf {
            items.filter { it.isSelected }
                .groupingBy { it.piiType }.eachCount()
                .entries.toList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Review PII (${items.count { it.isSelected }} selected)") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Category chips — horizontally scrollable LazyRow (stable API)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                items(countByType) { (type, count) ->
                    CategorySummaryChip(piiType = type, count = count)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Document page",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    PiiHighlightOverlay(
                        items = items.filter { it.pageIndex == currentPageIndex },
                        imageWidth = bitmap!!.width,
                        imageHeight = bitmap!!.height,
                        onToggle = viewModel::toggleItem,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                if (uiState is RedactionUiState.Error) {
                    Text(
                        text = (uiState as RedactionUiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = viewModel::redact,
                    enabled = uiState !is RedactionUiState.Loading && items.any { it.isSelected },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState is RedactionUiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    }
                    Text("🔒 Redact ${items.count { it.isSelected }} Items")
                }
            }
        }
    }
}
