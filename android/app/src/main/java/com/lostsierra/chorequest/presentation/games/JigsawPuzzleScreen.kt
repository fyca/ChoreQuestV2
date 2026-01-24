package com.lostsierra.chorequest.presentation.games

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.util.Log
import com.lostsierra.chorequest.presentation.components.ChoreQuestTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JigsawPuzzleScreen(
    onNavigateBack: () -> Unit,
    viewModel: JigsawPuzzleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        // Initialize with first available image if none selected
        val availableImages = JigsawPuzzleViewModel.discoverPuzzleImages(context)
        if (uiState.selectedImageResId == null && availableImages.isNotEmpty()) {
            viewModel.selectImage(availableImages[0], context)
        }
    }
    
    Scaffold(
        topBar = {
            ChoreQuestTopAppBar(
                title = "🧩 Jigsaw Puzzle",
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = { viewModel.showImageSelection() }) {
                        Icon(Icons.Default.Image, contentDescription = "Select Image")
                    }
                    IconButton(onClick = { viewModel.showPieceCountDialog() }) {
                        Icon(Icons.Default.GridOn, contentDescription = "Piece Count")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Stats Card
            StatsCard(
                moves = uiState.moves,
                timeElapsed = uiState.timeElapsed,
                bestTime = uiState.bestTime,
                onNewGame = { viewModel.startNewGame(context) }
            )
            
            // Puzzle Grid
            if (uiState.pieces.isNotEmpty()) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    PuzzleGrid(
                        pieces = uiState.pieces,
                        gridSize = uiState.gridSize,
                        customRows = uiState.customRows,
                        customCols = uiState.customCols,
                        onPieceDrag = { draggedId, targetId, _ ->
                            viewModel.swapPieces(draggedId, targetId)
                        }
                    )
                }
            } else {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading puzzle...")
                    }
                }
            }
        }
    }
    
    // Image Selection Dialog
    if (uiState.showImageSelection) {
        ImageSelectionDialog(
            availableImages = JigsawPuzzleViewModel.discoverPuzzleImages(context),
            onImageSelected = { imageResId ->
                viewModel.selectImage(imageResId, context)
            },
            onDismiss = { viewModel.hideImageSelection() }
        )
    }
    
    // Piece Count Dialog
    if (uiState.showPieceCountDialog) {
        PieceCountDialog(
            currentRows = uiState.customRows ?: uiState.gridSize,
            currentCols = uiState.customCols ?: uiState.gridSize,
            onConfirm = { rows, cols ->
                viewModel.setCustomPieceCount(rows, cols, context)
                viewModel.hidePieceCountDialog()
            },
            onDismiss = { viewModel.hidePieceCountDialog() }
        )
    }
    
    // Win Dialog
    if (uiState.showWinDialog) {
        WinDialog(
            moves = uiState.moves,
            timeElapsed = uiState.timeElapsed,
            bestTime = uiState.bestTime,
            onPlayAgain = {
                viewModel.dismissWinDialog()
                viewModel.startNewGame(context)
            },
            onDismiss = { viewModel.dismissWinDialog() }
        )
    }
}

@Composable
private fun StatsCard(
    moves: Int,
    timeElapsed: Int,
    bestTime: Int?,
    onNewGame: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Moves: $moves", style = MaterialTheme.typography.bodyMedium)
                Text("Time: ${formatTime(timeElapsed)}", style = MaterialTheme.typography.bodyMedium)
                if (bestTime != null) {
                    Text("Best: ${formatTime(bestTime)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = onNewGame) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Puzzle")
            }
        }
    }
}

@Composable
private fun PuzzleGrid(
    pieces: List<PuzzlePiece>,
    gridSize: Int,
    customRows: Int?,
    customCols: Int?,
    onPieceDrag: (Int, Int, Int) -> Unit
) {
    val rows = customRows ?: gridSize
    val cols = customCols ?: gridSize
    
    var draggedPieceId by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var draggedOverPieceId by remember { mutableStateOf<Int?>(null) }
    var gridContainerPosition by remember { mutableStateOf<Offset?>(null) }
    var gridContainerSize by remember { mutableStateOf<androidx.compose.ui.geometry.Size?>(null) }
    var piecePositions by remember { mutableStateOf<Map<Int, androidx.compose.ui.geometry.Rect>>(emptyMap()) }
    var initialDragPosition by remember { mutableStateOf<Offset?>(null) }
    
    // Create a map for quick piece lookup by ID
    // Use a callback to always get fresh pieces, not a remembered value
    val getPiecesById = { pieces.associateBy { it.id } }
    val piecesById = getPiecesById()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                gridContainerPosition = coordinates.positionInRoot()
                gridContainerSize = androidx.compose.ui.geometry.Size(
                    coordinates.size.width.toFloat(),
                    coordinates.size.height.toFloat()
                )
            },
        verticalArrangement = Arrangement.spacedBy(if (pieces.any { it.isPlaced }) 0.dp else 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(rows) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(if (pieces.any { it.isPlaced }) 0.dp else 2.dp)
            ) {
                repeat(cols) { col ->
                    val piece = pieces.find { 
                        it.currentRow == row && it.currentCol == col 
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        if (piece != null) {
                            PuzzlePieceCard(
                                piece = piece,
                                gridRows = rows,
                                gridCols = cols,
                                isDragging = draggedPieceId == piece.id,
                                dragOffset = if (draggedPieceId == piece.id) dragOffset else Offset.Zero,
                                isDragTarget = draggedPieceId != null && draggedPieceId != piece.id && draggedOverPieceId == piece.id,
                                gridContainerPosition = gridContainerPosition,
                                gridContainerSize = gridContainerSize,
                                piecePositions = piecePositions,
                                piecesById = piecesById,
                                getPiecePositions = { piecePositions },
                                getPiecesById = getPiecesById,
                                initialDragPosition = initialDragPosition,
                                onPositioned = { rect ->
                                    piecePositions = piecePositions + (piece.id to rect)
                                },
                                onDragStart = {
                                    if (!piece.isPlaced) {
                                        Log.d("JigsawPuzzle", "onDragStart called for piece ${piece.id}")
                                        draggedPieceId = piece.id
                                        // Capture initial position BEFORE resetting offset
                                        initialDragPosition = piecePositions[piece.id]?.center
                                        dragOffset = Offset.Zero
                                        draggedOverPieceId = null // Reset target on drag start
                                        Log.d("JigsawPuzzle", "Drag started - initialDragPosition=$initialDragPosition, piecePositions size=${piecePositions.size}")
                                    }
                                    // Return the initial position so it can be used immediately
                                    piecePositions[piece.id]?.center
                                },
                                onDrag = { _, newOffset ->
                                    if (draggedPieceId == piece.id) {
                                        // Update drag offset immediately so the piece follows the finger
                                        dragOffset = newOffset
                                    }
                                },
                                onDragOverPiece = { targetPieceId ->
                                    // Update target piece ID - this is only called for the piece being dragged
                                    Log.d("JigsawPuzzle", "onDragOverPiece called: draggedPieceId=$draggedPieceId, piece.id=${piece.id}, targetPieceId=$targetPieceId")
                                    // Simply set the target ID - let ViewModel validate with fresh state
                                    // Don't check isPlaced here as state might be stale during drag
                                    if (targetPieceId != null && targetPieceId != draggedPieceId) {
                                        Log.d("JigsawPuzzle", "Setting draggedOverPieceId to $targetPieceId")
                                        draggedOverPieceId = targetPieceId
                                    } else if (targetPieceId == null) {
                                        // Keep last known target - don't clear on null
                                        Log.d("JigsawPuzzle", "targetPieceId is null, keeping last known target")
                                    }
                                },
                                onDragEnd = {
                                    // Only handle drag end for the piece being dragged
                                    Log.d("JigsawPuzzle", "onDragEnd called: draggedPieceId=$draggedPieceId, piece.id=${piece.id}")
                                    if (draggedPieceId == piece.id) {
                                        // Capture values before resetting state
                                        val draggedId = draggedPieceId
                                        val targetId = draggedOverPieceId
                                        
                                        Log.d("JigsawPuzzle", "Drag end - draggedId=$draggedId, targetId=$targetId")
                                        
                                        // Reset drag state
                                        draggedPieceId = null
                                        draggedOverPieceId = null
                                        dragOffset = Offset.Zero
                                        initialDragPosition = null
                                        
                                        // Always attempt swap - let ViewModel validate with fresh state
                                        // The ViewModel's swapPieces method will check if pieces are placed
                                        // using the current state, which is the source of truth
                                        if (draggedId != null && targetId != null && draggedId != targetId) {
                                            Log.d("JigsawPuzzle", "Calling onPieceDrag with draggedId=$draggedId, targetId=$targetId")
                                            onPieceDrag(draggedId, targetId, 0)
                                        } else {
                                            Log.d("JigsawPuzzle", "NOT swapping - draggedId=$draggedId, targetId=$targetId, draggedId==targetId=${draggedId == targetId}")
                                        }
                                    } else {
                                        Log.d("JigsawPuzzle", "onDragEnd ignored - not the dragged piece")
                                    }
                                }
                            )
                        } else {
                            Card(
                                modifier = Modifier.fillMaxSize(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PuzzlePieceCard(
    piece: PuzzlePiece,
    gridRows: Int,
    gridCols: Int,
    isDragging: Boolean,
    dragOffset: Offset,
    isDragTarget: Boolean,
    gridContainerPosition: Offset?,
    gridContainerSize: androidx.compose.ui.geometry.Size?,
    piecePositions: Map<Int, androidx.compose.ui.geometry.Rect>,
    piecesById: Map<Int, PuzzlePiece>,
    getPiecePositions: () -> Map<Int, androidx.compose.ui.geometry.Rect>,
    getPiecesById: () -> Map<Int, PuzzlePiece>,
    initialDragPosition: Offset?,
    onPositioned: (androidx.compose.ui.geometry.Rect) -> Unit,
    onDragStart: () -> Offset?,
    onDrag: (androidx.compose.ui.input.pointer.PointerInputChange, Offset) -> Unit,
    onDragOverPiece: (Int?) -> Unit,
    onDragEnd: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = when {
            piece.isPlaced -> 1f
            isDragging -> 1.1f
            else -> 0.95f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ), label = "pieceScale"
    )
    
    Card(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (isDragging) 1f else 0f) // Bring dragged piece to front
            .scale(scale)
            .graphicsLayer {
                // Use graphicsLayer for smooth translation without layout recalculation
                translationX = dragOffset.x
                translationY = dragOffset.y
            }
            .onGloballyPositioned { coordinates ->
                // Update position - during drag, calculate from initial position + offset
                // This ensures overlap detection uses current positions
                val rect = if (isDragging && initialDragPosition != null) {
                    // Calculate position accounting for graphicsLayer translation
                    val basePosition = coordinates.positionInRoot()
                    androidx.compose.ui.geometry.Rect(
                        offset = Offset(
                            basePosition.x - dragOffset.x,
                            basePosition.y - dragOffset.y
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            coordinates.size.width.toFloat(),
                            coordinates.size.height.toFloat()
                        )
                    )
                } else {
                    androidx.compose.ui.geometry.Rect(
                        offset = coordinates.positionInRoot(),
                        size = androidx.compose.ui.geometry.Size(
                            coordinates.size.width.toFloat(),
                            coordinates.size.height.toFloat()
                        )
                    )
                }
                onPositioned(rect)
            }
            .pointerInput(piece.id) {
                // Only include stable keys - don't include isDragging, piecePositions, or piecesById
                // as they change during drag and restart the gesture
                if (!piece.isPlaced) {
                    var cumulativeOffset = Offset.Zero
                    var localInitialPosition: Offset? = null
                    
                    detectDragGestures(
                        onDragStart = { offset ->
                            cumulativeOffset = Offset.Zero
                            // Call parent callback first to set draggedPieceId and initialDragPosition
                            // It returns the initial position directly, so we can use it immediately
                            localInitialPosition = onDragStart()
                            Log.d("JigsawPuzzle", "Drag start in pointerInput - localInitialPosition=$localInitialPosition, piece.id=${piece.id}")
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            
                            // Accumulate drag amount - dragAmount is already the delta
                            cumulativeOffset = Offset(
                                cumulativeOffset.x + dragAmount.x,
                                cumulativeOffset.y + dragAmount.y
                            )
                            
                            // Update immediately
                            onDrag(change, cumulativeOffset)
                            
                            // Detect which piece we're dragging over by checking piece positions
                            // Use local initial position - access piecePositions fresh via callback
                            val currentPiecePositions = getPiecePositions()
                            val currentPiecePosition = currentPiecePositions[piece.id]?.center
                            val positionToUse = localInitialPosition ?: currentPiecePosition
                            
                            if (positionToUse != null) {
                                // Calculate the center of the dragged piece in root coordinates
                                // Use initial position + accumulated offset
                                val draggedCenterX = positionToUse.x + cumulativeOffset.x
                                val draggedCenterY = positionToUse.y + cumulativeOffset.y
                                    
                                    // Check which other piece's bounds contain this center point
                                    var foundTarget: Int? = null
                                    Log.d("JigsawPuzzle", "Checking overlap - draggedCenterX=$draggedCenterX, draggedCenterY=$draggedCenterY, piecePositions size=${currentPiecePositions.size}, localInitialPosition=$localInitialPosition, cumulativeOffset=$cumulativeOffset")
                                    for ((pieceId, rect) in currentPiecePositions) {
                                        val isOverlapping = pieceId != piece.id && 
                                            draggedCenterX >= rect.left && 
                                            draggedCenterX <= rect.right &&
                                            draggedCenterY >= rect.top && 
                                            draggedCenterY <= rect.bottom
                                        if (isOverlapping) {
                                            Log.d("JigsawPuzzle", "Found overlap with piece $pieceId: rect.left=${rect.left}, rect.right=${rect.right}, rect.top=${rect.top}, rect.bottom=${rect.bottom}")
                                            // Found a piece - use its ID directly
                                            val targetPiece = getPiecesById()[pieceId]
                                            Log.d("JigsawPuzzle", "Target piece lookup: pieceId=$pieceId, found=${targetPiece != null}, isPlaced=${targetPiece?.isPlaced}")
                                            if (targetPiece != null && !targetPiece.isPlaced) {
                                                foundTarget = pieceId
                                                Log.d("JigsawPuzzle", "Setting foundTarget to $pieceId")
                                                break
                                            }
                                        }
                                    }
                                    // Update target piece ID (or null if not over any valid piece)
                                    // Don't clear if null - keep last known target for swap
                                    Log.d("JigsawPuzzle", "Drag detection complete - foundTarget=$foundTarget, calling onDragOverPiece")
                                    onDragOverPiece(foundTarget)
                                } else {
                                    Log.d("JigsawPuzzle", "Overlap detection skipped - localInitialPosition is null")
                                }
                        },
                        onDragEnd = { 
                            onDragEnd()
                        }
                    )
                }
            },
        shape = if (piece.isPlaced) RoundedCornerShape(0.dp) else RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                piece.isPlaced -> Color.Transparent
                isDragTarget -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                isDragging -> Color.Transparent
                else -> Color.Transparent
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (piece.isPlaced) 0.dp else if (isDragging) 8.dp else 2.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (piece.pieceBitmap != null) {
                Image(
                    bitmap = piece.pieceBitmap.asImageBitmap(),
                    contentDescription = "Puzzle piece ${piece.id + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
        }
    }
}

@Composable
private fun ImageSelectionDialog(
    availableImages: List<Int>,
    onImageSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Puzzle Image") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableImages.size) { index ->
                    val imageResId = availableImages[index]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(9f / 16f) // 9:16 aspect ratio (portrait)
                            .clickable { onImageSelected(imageResId) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = imageResId),
                            contentDescription = "Puzzle image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PieceCountDialog(
    currentRows: Int,
    currentCols: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Calculate current piece count
    val currentPieceCount = currentRows * currentCols
    
    // Difficulty options: (name, rows, cols, total pieces)
    val difficultyOptions = listOf(
        "Easy" to (4 to 4),      // 16 pieces
        "Medium" to (6 to 6),    // 36 pieces
        "Hard" to (12 to 6),     // 72 pieces
        "Super Hard" to (8 to 11) // 88 pieces
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Difficulty") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                difficultyOptions.forEach { (name, rowsCols) ->
                    val (rows, cols) = rowsCols
                    val pieceCount = rows * cols
                    val isSelected = currentRows == rows && currentCols == cols
                    
                    Button(
                        onClick = { onConfirm(rows, cols) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = "$pieceCount pieces ($rows×$cols)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun WinDialog(
    moves: Int,
    timeElapsed: Int,
    bestTime: Int?,
    onPlayAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎉 Puzzle Complete! 🎉") },
        text = {
            Column {
                Text("Moves: $moves")
                Text("Time: ${formatTime(timeElapsed)}")
                if (bestTime != null) {
                    Text("Best Time: ${formatTime(bestTime)}")
                }
            }
        },
        confirmButton = {
            Button(onClick = onPlayAgain) {
                Text("Play Again")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", mins, secs)
}
