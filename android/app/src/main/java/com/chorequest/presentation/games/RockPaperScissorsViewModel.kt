package com.chorequest.presentation.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chorequest.data.local.GamePreferencesManager
import com.chorequest.utils.SoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RPSChoice {
    ROCK, PAPER, SCISSORS
}

enum class RPSResult {
    WIN, LOSE, DRAW
}

data class RPSGameState(
    val playerWins: Int = 0,
    val aiWins: Int = 0,
    val draws: Int = 0,
    val playerChoice: RPSChoice? = null,
    val aiChoice: RPSChoice? = null,
    val result: RPSResult? = null,
    val isAnimating: Boolean = false
)

data class RockPaperScissorsUiState(
    val gameState: RPSGameState = RPSGameState(),
    val showResult: Boolean = false,
    val showCelebration: Boolean = false,
    val difficulty: String = "medium",
    val highScore: Int = 0,
    val soundEnabled: Boolean = true
)

@HiltViewModel
class RockPaperScissorsViewModel @Inject constructor(
    private val gamePreferencesManager: GamePreferencesManager,
    private val soundManager: SoundManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RockPaperScissorsUiState(
            difficulty = gamePreferencesManager.getRockPaperScissorsDifficulty(),
            highScore = gamePreferencesManager.getRockPaperScissorsHighScore(),
            soundEnabled = gamePreferencesManager.isSoundEnabled()
        )
    )
    val uiState: StateFlow<RockPaperScissorsUiState> = _uiState.asStateFlow()

    fun makeChoice(choice: RPSChoice) {
        val currentState = _uiState.value
        if (currentState.gameState.isAnimating || currentState.showResult) {
            return
        }

        // Start animation
        _uiState.value = currentState.copy(
            gameState = currentState.gameState.copy(
                isAnimating = true,
                playerChoice = choice,
                aiChoice = null,
                result = null
            )
        )

        soundManager.playSound(SoundManager.SoundType.CLICK)

        // Animate AI choice
        viewModelScope.launch {
            delay(1000) // Animation delay

            val aiChoice = getAIChoice(currentState.difficulty, choice)
            val result = determineWinner(choice, aiChoice)

            val newGameState = currentState.gameState.copy(
                playerChoice = choice,
                aiChoice = aiChoice,
                result = result,
                isAnimating = false,
                playerWins = if (result == RPSResult.WIN) currentState.gameState.playerWins + 1 else currentState.gameState.playerWins,
                aiWins = if (result == RPSResult.LOSE) currentState.gameState.aiWins + 1 else currentState.gameState.aiWins,
                draws = if (result == RPSResult.DRAW) currentState.gameState.draws + 1 else currentState.gameState.draws
            )

            // Play sound based on result
            when (result) {
                RPSResult.WIN -> {
                    soundManager.playSound(SoundManager.SoundType.WIN)
                    _uiState.value = currentState.copy(
                        gameState = newGameState,
                        showResult = true,
                        showCelebration = true,
                        highScore = maxOf(currentState.highScore, newGameState.playerWins)
                    )
                    updateHighScore(newGameState.playerWins)
                }
                RPSResult.LOSE -> {
                    soundManager.playSound(SoundManager.SoundType.LOSE)
                    _uiState.value = currentState.copy(
                        gameState = newGameState,
                        showResult = true
                    )
                }
                RPSResult.DRAW -> {
                    soundManager.playSound(SoundManager.SoundType.DRAW)
                    _uiState.value = currentState.copy(
                        gameState = newGameState,
                        showResult = true
                    )
                }
            }
        }
    }

    private fun getAIChoice(difficulty: String, playerChoice: RPSChoice): RPSChoice {
        return when (difficulty) {
            "easy" -> {
                // Easy: Random choice
                RPSChoice.values().random()
            }
            "medium" -> {
                // Medium: 50% chance to counter player, 50% random
                if (kotlin.random.Random.nextFloat() < 0.5f) {
                    getCounterChoice(playerChoice)
                } else {
                    RPSChoice.values().random()
                }
            }
            "hard" -> {
                // Hard: Always tries to counter player (but sometimes random to keep it interesting)
                if (kotlin.random.Random.nextFloat() < 0.8f) {
                    getCounterChoice(playerChoice)
                } else {
                    RPSChoice.values().random()
                }
            }
            else -> RPSChoice.values().random()
        }
    }

    private fun getCounterChoice(playerChoice: RPSChoice): RPSChoice {
        return when (playerChoice) {
            RPSChoice.ROCK -> RPSChoice.PAPER
            RPSChoice.PAPER -> RPSChoice.SCISSORS
            RPSChoice.SCISSORS -> RPSChoice.ROCK
        }
    }

    private fun determineWinner(player: RPSChoice, ai: RPSChoice): RPSResult {
        return when {
            player == ai -> RPSResult.DRAW
            (player == RPSChoice.ROCK && ai == RPSChoice.SCISSORS) ||
            (player == RPSChoice.PAPER && ai == RPSChoice.ROCK) ||
            (player == RPSChoice.SCISSORS && ai == RPSChoice.PAPER) -> RPSResult.WIN
            else -> RPSResult.LOSE
        }
    }

    fun playAgain() {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(
            gameState = currentState.gameState.copy(
                playerChoice = null,
                aiChoice = null,
                result = null
            ),
            showResult = false
        )
    }

    fun resetScore() {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(
            gameState = RPSGameState(),
            showResult = false,
            highScore = 0
        )
        gamePreferencesManager.saveRockPaperScissorsHighScore(0)
    }

    fun onCelebrationComplete() {
        _uiState.value = _uiState.value.copy(showCelebration = false)
    }

    fun setDifficulty(difficulty: String) {
        gamePreferencesManager.saveRockPaperScissorsDifficulty(difficulty)
        _uiState.value = _uiState.value.copy(difficulty = difficulty)
    }

    fun setSoundEnabled(enabled: Boolean) {
        gamePreferencesManager.setSoundEnabled(enabled)
        _uiState.value = _uiState.value.copy(soundEnabled = enabled)
    }

    private fun updateHighScore(newScore: Int) {
        val currentHighScore = _uiState.value.highScore
        if (newScore > currentHighScore) {
            gamePreferencesManager.saveRockPaperScissorsHighScore(newScore)
            _uiState.value = _uiState.value.copy(highScore = newScore)
        }
    }
}
