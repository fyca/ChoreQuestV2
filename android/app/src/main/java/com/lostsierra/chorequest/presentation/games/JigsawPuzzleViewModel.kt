package com.lostsierra.chorequest.presentation.games

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostsierra.chorequest.data.local.GamePreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JigsawPuzzleViewModel @Inject constructor(
    private val preferencesManager: GamePreferencesManager
) : ViewModel() {

    companion object {
        /**
         * Automatically discovers puzzle images in the drawable folder
         * that follow the naming convention: puzzle_image_1.png, puzzle_image_2.png, etc.
         * 
         * @param context Android context to access resources
         * @return List of resource IDs for discovered puzzle images
         */
        fun discoverPuzzleImages(context: android.content.Context): List<Int> {
            val images = mutableListOf<Int>()
            var index = 1
            
            // Keep looking for images until we can't find any more
            while (true) {
                val resourceName = "puzzle_image_$index"
                val resourceId = context.resources.getIdentifier(
                    resourceName,
                    "drawable",
                    context.packageName
                )
                
                // If resource ID is 0, the resource doesn't exist
                if (resourceId == 0) {
                    break
                }
                
                images.add(resourceId)
                index++
            }
            
            return images
        }
    }

    private val _uiState = MutableStateFlow(JigsawPuzzleUiState())
    val uiState: StateFlow<JigsawPuzzleUiState> = _uiState.asStateFlow()

    private var startTime: Long = 0
    private var timerJob: kotlinx.coroutines.Job? = null

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            val difficulty = preferencesManager.getJigsawPuzzleDifficulty()
            val bestTime = preferencesManager.getJigsawPuzzleBestTime()
            _uiState.value = _uiState.value.copy(
                difficulty = difficulty,
                bestTime = if (bestTime == Int.MAX_VALUE) null else bestTime
            )
        }
    }

    fun startNewGame(context: android.content.Context) {
        timerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isGameStarted = false,
            isGameComplete = false,
            moves = 0,
            timeElapsed = 0,
            showWinDialog = false
        )
        generatePuzzle(context)
    }

    fun generatePuzzle(context: android.content.Context) {
        val rows = _uiState.value.customRows ?: _uiState.value.gridSize
        val cols = _uiState.value.customCols ?: _uiState.value.gridSize
        
        viewModelScope.launch {
            val imageResId = _uiState.value.selectedImageResId
            if (imageResId != null) {
                val pieces = PuzzlePieceGenerator.generatePuzzlePieces(
                    imageResId = imageResId,
                    rows = rows,
                    cols = cols,
                    context = context
                )
                
                // Shuffle pieces (keep original IDs for correct position tracking)
                val shuffledPieces = pieces.shuffled().mapIndexed { index, piece ->
                    val newRow = index / cols
                    val newCol = index % cols
                    val isCorrect = piece.correctRow == newRow && piece.correctCol == newCol
                    piece.copy(
                        currentRow = newRow,
                        currentCol = newCol,
                        isPlaced = isCorrect
                    )
                }
                
                _uiState.value = _uiState.value.copy(
                    pieces = shuffledPieces,
                    isGameStarted = false,
                    isGameComplete = false,
                    moves = 0,
                    timeElapsed = 0
                )
            }
        }
    }

    fun swapPieces(pieceId1: Int, pieceId2: Int) {
        Log.d("JigsawPuzzle", "swapPieces called: pieceId1=$pieceId1, pieceId2=$pieceId2")
        val currentState = _uiState.value
        
        if (currentState.isGameComplete) {
            Log.d("JigsawPuzzle", "Game is complete, not swapping")
            return
        }
        
        val piece1 = currentState.pieces.find { it.id == pieceId1 }
        val piece2 = currentState.pieces.find { it.id == pieceId2 }
        
        if (piece1 == null) {
            Log.d("JigsawPuzzle", "Piece1 not found: $pieceId1")
            return
        }
        if (piece2 == null) {
            Log.d("JigsawPuzzle", "Piece2 not found: $pieceId2")
            return
        }
        
        // Can't swap if either piece is placed
        if (piece1.isPlaced || piece2.isPlaced) {
            Log.d("JigsawPuzzle", "Cannot swap - piece1.isPlaced=${piece1.isPlaced}, piece2.isPlaced=${piece2.isPlaced}")
            return
        }
        
        Log.d("JigsawPuzzle", "Swapping pieces: piece1=${piece1.id} at (${piece1.currentRow}, ${piece1.currentCol}), piece2=${piece2.id} at (${piece2.currentRow}, ${piece2.currentCol})")
        
        // Start timer on first move
        if (!currentState.isGameStarted) {
            startTime = System.currentTimeMillis()
            _uiState.value = currentState.copy(isGameStarted = true)
            startTimer()
        }
        
        // Swap pieces
        val updatedPieces = currentState.pieces.map { p ->
            when {
                p.id == pieceId1 -> {
                    val newRow = piece2.currentRow
                    val newCol = piece2.currentCol
                    val isCorrect = p.correctRow == newRow && p.correctCol == newCol
                    p.copy(
                        currentRow = newRow,
                        currentCol = newCol,
                        isPlaced = isCorrect
                    )
                }
                p.id == pieceId2 -> {
                    val newRow = piece1.currentRow
                    val newCol = piece1.currentCol
                    val isCorrect = p.correctRow == newRow && p.correctCol == newCol
                    p.copy(
                        currentRow = newRow,
                        currentCol = newCol,
                        isPlaced = isCorrect
                    )
                }
                else -> p
            }
        }
        
        _uiState.value = currentState.copy(
            pieces = updatedPieces,
            moves = currentState.moves + 1
        )
        
        checkPuzzleComplete()
    }

    private fun checkPuzzleComplete() {
        val allPlaced = _uiState.value.pieces.all { it.isPlaced }
        if (allPlaced) {
            timerJob?.cancel()
            val currentTime = _uiState.value.timeElapsed
            val bestTime = _uiState.value.bestTime
            
            val newBestTime = if (bestTime == null || currentTime < bestTime) {
                viewModelScope.launch {
                    preferencesManager.saveJigsawPuzzleBestTime(currentTime)
                }
                currentTime
            } else {
                bestTime
            }
            
            _uiState.value = _uiState.value.copy(
                isGameComplete = true,
                showWinDialog = true,
                bestTime = newBestTime
            )
        }
    }

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            while (!_uiState.value.isGameComplete) {
                kotlinx.coroutines.delay(1000)
                val elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                _uiState.value = _uiState.value.copy(timeElapsed = elapsed)
            }
        }
    }

    fun dismissWinDialog() {
        _uiState.value = _uiState.value.copy(showWinDialog = false)
    }

    fun setDifficulty(difficulty: String) {
        viewModelScope.launch {
            preferencesManager.saveJigsawPuzzleDifficulty(difficulty)
        }
        val gridSize = when (difficulty) {
            "easy" -> 3
            "medium" -> 4
            "hard" -> 5
            else -> 4
        }
        _uiState.value = _uiState.value.copy(
            difficulty = difficulty,
            gridSize = gridSize,
            customRows = null,
            customCols = null
        )
    }

    fun setCustomPieceCount(rows: Int, cols: Int, context: android.content.Context) {
        _uiState.value = _uiState.value.copy(
            customRows = rows,
            customCols = cols
        )
        generatePuzzle(context)
    }

    fun selectImage(imageResId: Int, context: android.content.Context) {
        _uiState.value = _uiState.value.copy(
            selectedImageResId = imageResId,
            showImageSelection = false
        )
        generatePuzzle(context)
    }

    fun showImageSelection() {
        _uiState.value = _uiState.value.copy(showImageSelection = true)
    }

    fun hideImageSelection() {
        _uiState.value = _uiState.value.copy(showImageSelection = false)
    }

    fun showPieceCountDialog() {
        _uiState.value = _uiState.value.copy(showPieceCountDialog = true)
    }

    fun hidePieceCountDialog() {
        _uiState.value = _uiState.value.copy(showPieceCountDialog = false)
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

data class JigsawPuzzleUiState(
    val pieces: List<PuzzlePiece> = emptyList(),
    val gridSize: Int = 4,
    val customRows: Int? = null,
    val customCols: Int? = null,
    val isGameStarted: Boolean = false,
    val isGameComplete: Boolean = false,
    val moves: Int = 0,
    val timeElapsed: Int = 0,
    val bestTime: Int? = null,
    val difficulty: String = "medium",
    val showWinDialog: Boolean = false,
    val selectedImageResId: Int? = null,
    val showImageSelection: Boolean = false,
    val showPieceCountDialog: Boolean = false
)

data class PuzzlePiece(
    val id: Int,
    val correctRow: Int,
    val correctCol: Int,
    val currentRow: Int,
    val currentCol: Int,
    val pieceBitmap: Bitmap? = null,
    val isPlaced: Boolean = false
)
