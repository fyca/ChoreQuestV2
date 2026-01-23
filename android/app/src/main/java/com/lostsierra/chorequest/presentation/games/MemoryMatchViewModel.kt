package com.lostsierra.chorequest.presentation.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostsierra.chorequest.data.local.GamePreferencesManager
import com.lostsierra.chorequest.utils.SoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemoryMatchUiState(
    val cards: List<MemoryCard> = emptyList(),
    val flippedIndices: Set<Int> = emptySet(),
    val matchedPairs: Set<Int> = emptySet(),
    val moves: Int = 0,
    val timeElapsed: Long = 0L,
    val isGameStarted: Boolean = false,
    val isGameComplete: Boolean = false,
    val showWinDialog: Boolean = false,
    val showCelebration: Boolean = false,
    val difficulty: String = "medium",
    val bestTime: Long = Long.MAX_VALUE,
    val bestMoves: Int = Int.MAX_VALUE,
    val soundEnabled: Boolean = true
)

data class MemoryCard(
    val id: Int,
    val emoji: String,
    val isFlipped: Boolean = false,
    val isMatched: Boolean = false
)

@HiltViewModel
class MemoryMatchViewModel @Inject constructor(
    private val gamePreferencesManager: GamePreferencesManager,
    private val soundManager: SoundManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MemoryMatchUiState(
            difficulty = gamePreferencesManager.getMemoryMatchDifficulty(),
            bestTime = gamePreferencesManager.getMemoryMatchBestTime().toLong(),
            bestMoves = gamePreferencesManager.getMemoryMatchBestMoves(),
            soundEnabled = gamePreferencesManager.isSoundEnabled()
        )
    )
    val uiState: StateFlow<MemoryMatchUiState> = _uiState.asStateFlow()

    private var startTime: Long = 0L
    private var timerJob: kotlinx.coroutines.Job? = null

    init {
        startNewGame()
    }

    fun startNewGame() {
        timerJob?.cancel()
        startTime = 0L
        val difficulty = _uiState.value.difficulty
        val cards = generateCards(difficulty)
        
        _uiState.value = MemoryMatchUiState(
            cards = cards,
            difficulty = difficulty,
            bestTime = gamePreferencesManager.getMemoryMatchBestTime().toLong(),
            bestMoves = gamePreferencesManager.getMemoryMatchBestMoves(),
            soundEnabled = _uiState.value.soundEnabled,
            timeElapsed = 0L,
            isGameStarted = false,
            isGameComplete = false
        )
    }

    fun onCardClick(index: Int) {
        val currentState = _uiState.value
        
        // Don't allow clicks if:
        // - Card is already matched
        // - Card is already flipped
        // - Two cards are already flipped (waiting for match check)
        // - Game is complete
        if (currentState.cards[index].isMatched ||
            currentState.cards[index].isFlipped ||
            currentState.flippedIndices.size >= 2 ||
            currentState.isGameComplete) {
            return
        }

        // Start timer on first card flip
        if (!currentState.isGameStarted) {
            startTime = System.currentTimeMillis()
            _uiState.value = _uiState.value.copy(isGameStarted = true)
            startTimer()
        }

        val newFlippedIndices = currentState.flippedIndices + index
        val updatedCards = currentState.cards.toMutableList()
        updatedCards[index] = updatedCards[index].copy(isFlipped = true)

        soundManager.playSound(SoundManager.SoundType.CLICK)

        if (newFlippedIndices.size == 2) {
            // Two cards flipped - check for match
            val firstCard = currentState.cards[newFlippedIndices.first()]
            val secondCard = currentState.cards[index]
            
            if (firstCard.emoji == secondCard.emoji) {
                // Match found!
                soundManager.playSound(SoundManager.SoundType.WIN)
                updatedCards[newFlippedIndices.first()] = updatedCards[newFlippedIndices.first()].copy(isMatched = true)
                updatedCards[index] = updatedCards[index].copy(isMatched = true)
                
                val newMatchedPairs = currentState.matchedPairs + firstCard.id
                val newMoves = currentState.moves + 1
                
                // Use current state to preserve timer updates
                _uiState.value = _uiState.value.copy(
                    cards = updatedCards,
                    flippedIndices = emptySet(),
                    matchedPairs = newMatchedPairs,
                    moves = newMoves
                )
                
                // Check if game is complete
                checkGameComplete(newMatchedPairs, updatedCards.size)
            } else {
                // No match - flip cards back after delay
                soundManager.playSound(SoundManager.SoundType.LOSE)
                val newMoves = currentState.moves + 1
                
                // Use current state to preserve timer updates
                _uiState.value = _uiState.value.copy(
                    cards = updatedCards,
                    flippedIndices = newFlippedIndices,
                    moves = newMoves
                )
                
                viewModelScope.launch {
                    delay(1000) // Show cards for 1 second
                    // Use current state to preserve timer updates
                    val stateAfterDelay = _uiState.value
                    val cardsAfterDelay = stateAfterDelay.cards.toMutableList()
                    cardsAfterDelay[newFlippedIndices.first()] = cardsAfterDelay[newFlippedIndices.first()].copy(isFlipped = false)
                    cardsAfterDelay[index] = cardsAfterDelay[index].copy(isFlipped = false)
                    
                    _uiState.value = _uiState.value.copy(
                        cards = cardsAfterDelay,
                        flippedIndices = emptySet()
                    )
                }
            }
        } else {
            // First card flipped
            // Use current state to preserve timer updates
            _uiState.value = _uiState.value.copy(
                cards = updatedCards,
                flippedIndices = newFlippedIndices
            )
        }
    }

    private fun checkGameComplete(matchedPairs: Set<Int>, totalCards: Int) {
        val pairsNeeded = totalCards / 2
        if (matchedPairs.size == pairsNeeded) {
            timerJob?.cancel()
            val finalTime = System.currentTimeMillis() - startTime
            val currentState = _uiState.value
            
            var updatedState = currentState.copy(
                isGameComplete = true,
                timeElapsed = finalTime,
                showWinDialog = true
            )
            
            // Check for best time
            val currentBestTime = currentState.bestTime
            if (currentBestTime == Long.MAX_VALUE || finalTime < currentBestTime) {
                gamePreferencesManager.saveMemoryMatchBestTime(finalTime.toInt())
                updatedState = updatedState.copy(bestTime = finalTime)
            }
            
            // Check for best moves
            val currentBestMoves = currentState.bestMoves
            if (currentBestMoves == Int.MAX_VALUE || currentState.moves < currentBestMoves) {
                gamePreferencesManager.saveMemoryMatchBestMoves(currentState.moves)
                updatedState = updatedState.copy(bestMoves = currentState.moves)
            }
            
            // Show celebration
            viewModelScope.launch {
                soundManager.playSound(SoundManager.SoundType.WIN)
                updatedState = updatedState.copy(showCelebration = true)
                _uiState.value = updatedState
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel() // Cancel any existing timer
        timerJob = viewModelScope.launch {
            while (true) {
                val currentState = _uiState.value
                if (currentState.isGameComplete || !currentState.isGameStarted) {
                    break
                }
                delay(100) // Update every 100ms
                val currentTime = System.currentTimeMillis() - startTime
                // Only update if game is still active
                val state = _uiState.value
                if (state.isGameStarted && !state.isGameComplete) {
                    _uiState.value = state.copy(timeElapsed = currentTime)
                }
            }
        }
    }

    fun dismissWinDialog() {
        _uiState.value = _uiState.value.copy(showWinDialog = false)
    }

    fun onCelebrationComplete() {
        _uiState.value = _uiState.value.copy(showCelebration = false)
    }

    fun setDifficulty(difficulty: String) {
        gamePreferencesManager.saveMemoryMatchDifficulty(difficulty)
        _uiState.value = _uiState.value.copy(difficulty = difficulty)
        startNewGame()
    }

    fun setSoundEnabled(enabled: Boolean) {
        gamePreferencesManager.setSoundEnabled(enabled)
        _uiState.value = _uiState.value.copy(soundEnabled = enabled)
    }

    private fun generateCards(difficulty: String): List<MemoryCard> {
        val emojis = listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔",
            "🦄", "🐝", "🦋", "🐢", "🐍", "🐙", "🦑", "🦐"
        )
        
        val (rows, cols) = when (difficulty) {
            "easy" -> Pair(4, 4)   // 8 pairs - square
            "medium" -> Pair(4, 4) // 8 pairs - square
            "hard" -> Pair(6, 4)   // 12 pairs - height > width
            else -> Pair(4, 4)
        }
        
        val totalPairs = (rows * cols) / 2
        val selectedEmojis = emojis.shuffled().take(totalPairs)
        val cardPairs = selectedEmojis.flatMap { emoji ->
            listOf(emoji, emoji)
        }.shuffled()
        
        return cardPairs.mapIndexed { index, emoji ->
            MemoryCard(
                id = selectedEmojis.indexOf(emoji) + 1,
                emoji = emoji
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
