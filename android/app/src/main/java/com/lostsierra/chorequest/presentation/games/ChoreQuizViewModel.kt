package com.lostsierra.chorequest.presentation.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostsierra.chorequest.data.local.GamePreferencesManager
import com.lostsierra.chorequest.utils.SoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChoreQuizUiState(
    val questions: List<QuizQuestion> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val selectedAnswer: Int? = null,
    val showResult: Boolean = false,
    val correctAnswer: Int? = null,
    val explanation: String? = null,
    val score: Int = 0,
    val showCelebration: Boolean = false,
    val difficulty: String = "medium",
    val highScore: Int = 0,
    val soundEnabled: Boolean = true
)

@HiltViewModel
class ChoreQuizViewModel @Inject constructor(
    private val gamePreferencesManager: GamePreferencesManager,
    private val soundManager: SoundManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChoreQuizUiState(
            difficulty = gamePreferencesManager.getChoreQuizDifficulty(),
            highScore = gamePreferencesManager.getChoreQuizHighScore(),
            soundEnabled = gamePreferencesManager.isSoundEnabled()
        )
    )
    val uiState: StateFlow<ChoreQuizUiState> = _uiState.asStateFlow()

    init {
        startNewGame()
    }

    fun startNewGame() {
        val difficulty = _uiState.value.difficulty
        val questions = generateQuestions(difficulty)
            .shuffled()
            .take(10)
            .map { randomizeAnswers(it) } // Randomize answer order for each question
        
        _uiState.value = _uiState.value.copy(
            questions = questions,
            currentQuestionIndex = 0,
            selectedAnswer = null,
            showResult = false,
            correctAnswer = null,
            explanation = null,
            score = 0,
            showCelebration = false
        )
    }
    
    /**
     * Randomizes the order of answers in a question and updates the correctAnswerIndex
     * to match the new position of the correct answer.
     */
    private fun randomizeAnswers(question: QuizQuestion): QuizQuestion {
        val originalAnswers = question.answers
        val correctAnswer = originalAnswers[question.correctAnswerIndex]
        
        // Create a shuffled list of answer indices
        val indices = originalAnswers.indices.toList().shuffled()
        
        // Shuffle the answers using the shuffled indices
        val shuffledAnswers = indices.map { originalAnswers[it] }
        
        // Find the new index of the correct answer
        val newCorrectIndex = shuffledAnswers.indexOf(correctAnswer)
        
        return question.copy(
            answers = shuffledAnswers,
            correctAnswerIndex = newCorrectIndex
        )
    }

    fun selectAnswer(answerIndex: Int) {
        val currentState = _uiState.value
        if (currentState.showResult) return

        val question = currentState.questions[currentState.currentQuestionIndex]
        val isCorrect = answerIndex == question.correctAnswerIndex

        soundManager.playSound(
            if (isCorrect) SoundManager.SoundType.WIN else SoundManager.SoundType.LOSE
        )

        val newScore = if (isCorrect) currentState.score + 1 else currentState.score

        // Always show explanation for educational purposes
        val explanation = question.explanation

        _uiState.value = currentState.copy(
            selectedAnswer = answerIndex,
            showResult = true,
            correctAnswer = question.correctAnswerIndex,
            explanation = explanation,
            score = newScore
        )
    }

    fun nextQuestion() {
        val currentState = _uiState.value
        val nextIndex = currentState.currentQuestionIndex + 1

        if (nextIndex < currentState.questions.size) {
            _uiState.value = currentState.copy(
                currentQuestionIndex = nextIndex,
                selectedAnswer = null,
                showResult = false,
                correctAnswer = null,
                explanation = null
            )
        } else {
            // Quiz complete - check for high score
            val percentage = (currentState.score * 100 / currentState.questions.size)
            val currentHighScore = currentState.highScore
            
            var updatedState = currentState.copy(
                currentQuestionIndex = nextIndex, // Advance to show results screen
                selectedAnswer = null,
                showResult = false,
                correctAnswer = null,
                explanation = null
            )
            
            if (percentage > currentHighScore) {
                gamePreferencesManager.saveChoreQuizHighScore(percentage)
                updatedState = updatedState.copy(highScore = percentage)
                
                // Show celebration for high score
                if (percentage >= 90) {
                    viewModelScope.launch {
                        _uiState.value = updatedState.copy(showCelebration = true)
                    }
                }
            }
            
            _uiState.value = updatedState
        }
    }

    fun setDifficulty(difficulty: String) {
        gamePreferencesManager.saveChoreQuizDifficulty(difficulty)
        _uiState.value = _uiState.value.copy(difficulty = difficulty)
        startNewGame()
    }

    fun setSoundEnabled(enabled: Boolean) {
        gamePreferencesManager.setSoundEnabled(enabled)
        _uiState.value = _uiState.value.copy(soundEnabled = enabled)
    }

    fun onCelebrationComplete() {
        _uiState.value = _uiState.value.copy(showCelebration = false)
    }

    private fun generateQuestions(difficulty: String): List<QuizQuestion> {
        val allQuestions = when (difficulty) {
            "easy" -> com.lostsierra.chorequest.presentation.games.easyQuestions
            "medium" -> com.lostsierra.chorequest.presentation.games.mediumQuestions
            "hard" -> com.lostsierra.chorequest.presentation.games.hardQuestions
            else -> com.lostsierra.chorequest.presentation.games.mediumQuestions
        }
        return allQuestions
    }
}
