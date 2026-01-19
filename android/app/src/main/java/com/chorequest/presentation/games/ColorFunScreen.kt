package com.chorequest.presentation.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chorequest.presentation.components.ChoreQuestTopAppBar

/**
 * Color Fun - A pixel art coloring game for children
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorFunScreen(
    onNavigateBack: () -> Unit,
    viewModel: ColorFunViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showTemplateSelectionDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ChoreQuestTopAppBar(
                title = "🎨 Color Fun",
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Template selector
            TemplateSelector(
                templates = uiState.availableTemplates,
                currentTemplateIndex = uiState.currentTemplateIndex,
                onTemplateSelected = { index ->
                    viewModel.selectTemplate(index)
                },
                onShowAllTemplates = { showTemplateSelectionDialog = true }
            )

            // Color palette
            ColorPalette(
                selectedColor = uiState.selectedColor,
                onColorSelected = { color ->
                    viewModel.selectColor(color)
                }
            )

            // Pixel grid
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.availableTemplates.isNotEmpty()) {
                        val currentTemplate = uiState.availableTemplates[uiState.currentTemplateIndex]
                        val pixelGrid = uiState.pixelGrids[uiState.currentTemplateIndex]
                        
                        PixelGrid(
                            gridSize = uiState.gridSize,
                            pixelGrid = pixelGrid,
                            templateOutline = currentTemplate.outline,
                            onPixelClick = { row, col ->
                                viewModel.colorPixel(row, col)
                            }
                        )
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.clearTemplate() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear")
                }
            }
        }
    }

    // Template selection dialog
    if (showTemplateSelectionDialog) {
        TemplateSelectionDialog(
            templates = uiState.availableTemplates,
            currentTemplateIndex = uiState.currentTemplateIndex,
            onTemplateSelected = { index ->
                viewModel.selectTemplate(index)
                showTemplateSelectionDialog = false
            },
            onDismiss = { showTemplateSelectionDialog = false }
        )
    }

    // Settings dialog
    if (showSettingsDialog) {
        GameSettingsDialog(
            soundEnabled = uiState.soundEnabled,
            onSoundToggled = { enabled ->
                viewModel.setSoundEnabled(enabled)
            },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun TemplateSelector(
    templates: List<PixelTemplate>,
    currentTemplateIndex: Int,
    onTemplateSelected: (Int) -> Unit,
    onShowAllTemplates: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (templates.isNotEmpty() && currentTemplateIndex < templates.size) {
                val currentTemplate = templates[currentTemplateIndex]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = currentTemplate.emoji,
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = currentTemplate.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Template ${currentTemplateIndex + 1} of ${templates.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (currentTemplateIndex > 0) {
                            onTemplateSelected(currentTemplateIndex - 1)
                        }
                    },
                    enabled = currentTemplateIndex > 0
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Previous template")
                }
                IconButton(
                    onClick = {
                        if (currentTemplateIndex < templates.size - 1) {
                            onTemplateSelected(currentTemplateIndex + 1)
                        }
                    },
                    enabled = currentTemplateIndex < templates.size - 1
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Next template")
                }
                IconButton(onClick = onShowAllTemplates) {
                    Icon(Icons.Default.List, contentDescription = "Show all templates")
                }
            }
        }
    }
}

@Composable
private fun ColorPalette(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    val colors = listOf(
        Color(0xFFE74C3C) to "Red",
        Color(0xFFE67E22) to "Orange",
        Color(0xFFF39C12) to "Yellow",
        Color(0xFF27AE60) to "Green",
        Color(0xFF3498DB) to "Blue",
        Color(0xFF8E44AD) to "Purple",
        Color(0xFFE91E63) to "Pink",
        Color(0xFF1ABC9C) to "Turquoise",
        Color(0xFFF1C40F) to "Gold",
        Color(0xFF34495E) to "Dark Blue",
        Color(0xFF95A5A6) to "Gray",
        Color(0xFF000000) to "Black",
        Color(0xFFFFFFFF) to "White",
        Color(0xFFFFC0CB) to "Light Pink",
        Color(0xFF87CEEB) to "Sky Blue"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Color Palette",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(colors) { (color, name) ->
                    ColorButton(
                        color = color,
                        name = name,
                        isSelected = color == selectedColor,
                        onClick = { onColorSelected(color) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorButton(
    color: Color,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                    } else {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                    }
                )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun PixelGrid(
    gridSize: Int,
    pixelGrid: Array<Array<Color?>>?,
    templateOutline: Array<Array<Boolean>>,
    onPixelClick: (Int, Int) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Calculate pixel size to fit screen
            val availableWidth = androidx.compose.ui.platform.LocalDensity.current.run {
                // Approximate - will be calculated by layout
            }
            
            // Create grid
            repeat(gridSize) { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(gridSize) { col ->
                        val pixelColor = pixelGrid?.getOrNull(row)?.getOrNull(col)
                        val hasOutline = templateOutline.getOrNull(row)?.getOrNull(col) ?: false
                        
                        PixelCell(
                            color = pixelColor,
                            hasOutline = hasOutline,
                            onClick = { onPixelClick(row, col) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PixelCell(
    color: Color?,
    hasOutline: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .then(
                if (hasOutline) {
                    Modifier.border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                } else {
                    Modifier
                }
            )
            .background(
                color = color ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            .clickable(onClick = onClick)
    ) {
        // Show outline hint if not colored and has outline
        if (color == null && hasOutline) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
            )
        }
    }
}

@Composable
private fun TemplateSelectionDialog(
    templates: List<PixelTemplate>,
    currentTemplateIndex: Int,
    onTemplateSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select a Template") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                templates.forEachIndexed { index, template ->
                    TemplateOption(
                        template = template,
                        isSelected = index == currentTemplateIndex,
                        onClick = { onTemplateSelected(index) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun TemplateOption(
    template: PixelTemplate,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = template.emoji,
                fontSize = 32.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = template.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun GameSettingsDialog(
    soundEnabled: Boolean,
    onSoundToggled: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game Settings") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sound Effects")
                Switch(
                    checked = soundEnabled,
                    onCheckedChange = onSoundToggled
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
