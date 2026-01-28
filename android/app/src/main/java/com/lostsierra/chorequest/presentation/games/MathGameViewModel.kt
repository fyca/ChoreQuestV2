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
import kotlin.random.Random
import javax.inject.Inject

enum class MathOperation {
    ADDITION, SUBTRACTION, MULTIPLICATION, DIVISION
}

data class MathProblem(
    val num1: Int,
    val num2: Int,
    val operation: MathOperation,
    val correctAnswer: Int,
    val answerChoices: List<Int>,
    val correctAnswerIndex: Int
) {
    val questionText: String
        get() = when (operation) {
            MathOperation.ADDITION -> "$num1 + $num2 = ?"
            MathOperation.SUBTRACTION -> "$num1 - $num2 = ?"
            MathOperation.MULTIPLICATION -> "$num1 × $num2 = ?"
            MathOperation.DIVISION -> "$num1 ÷ $num2 = ?"
        }
}

enum class GradeLevel(val displayName: String, val value: Int) {
    KINDERGARTEN("Kindergarten", 0),
    GRADE_1("Grade 1", 1),
    GRADE_2("Grade 2", 2),
    GRADE_3("Grade 3", 3),
    GRADE_4("Grade 4", 4),
    GRADE_5("Grade 5", 5),
    GRADE_6("Grade 6", 6),
    GRADE_7("Grade 7", 7),
    GRADE_8("Grade 8", 8),
    GRADE_9("Grade 9", 9),
    GRADE_10("Grade 10", 10),
    GRADE_11("Grade 11", 11),
    GRADE_12("Grade 12", 12),
    BRAINROT("BrainRot", -1),
    YEEPS("Yeeps", -2);
    
    companion object {
        fun fromValue(value: Int): GradeLevel {
            return values().find { it.value == value } ?: GRADE_3
        }
    }
}

data class MathGameUiState(
    val problems: List<MathProblem> = emptyList(),
    val currentProblemIndex: Int = 0,
    val selectedAnswer: Int? = null,
    val showResult: Boolean = false,
    val score: Int = 0,
    val totalPoints: Int = 0,
    val showCelebration: Boolean = false,
    val difficulty: String = "medium",
    val gradeLevel: GradeLevel = GradeLevel.GRADE_3,
    val problemTypes: Set<MathOperation> = setOf(
        MathOperation.ADDITION,
        MathOperation.SUBTRACTION,
        MathOperation.MULTIPLICATION,
        MathOperation.DIVISION
    ),
    val highScore: Int = 0,
    val soundEnabled: Boolean = true,
    val isGameComplete: Boolean = false
)

@HiltViewModel
class MathGameViewModel @Inject constructor(
    private val gamePreferencesManager: GamePreferencesManager,
    private val soundManager: SoundManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MathGameUiState(
            difficulty = gamePreferencesManager.getMathGameDifficulty(),
            highScore = gamePreferencesManager.getMathGameHighScore(),
            soundEnabled = gamePreferencesManager.isSoundEnabled(),
            problemTypes = gamePreferencesManager.getMathGameProblemTypes(),
            gradeLevel = gamePreferencesManager.getMathGameGradeLevel()
        )
    )
    val uiState: StateFlow<MathGameUiState> = _uiState.asStateFlow()

    init {
        startNewGame()
    }

    fun startNewGame() {
        val difficulty = _uiState.value.difficulty
        val gradeLevel = _uiState.value.gradeLevel
        var problemTypes = _uiState.value.problemTypes
        
        // Ensure at least one problem type is selected
        if (problemTypes.isEmpty()) {
            problemTypes = getDefaultProblemTypesForGrade(gradeLevel)
        }
        
        val problems = generateProblems(difficulty, gradeLevel, problemTypes, 10)
            .map { randomizeAnswers(it) }
        
        _uiState.value = _uiState.value.copy(
            problems = problems,
            currentProblemIndex = 0,
            selectedAnswer = null,
            showResult = false,
            score = 0,
            totalPoints = 0,
            showCelebration = false,
            isGameComplete = false,
            problemTypes = problemTypes
        )
    }
    
    private fun getDefaultProblemTypesForGrade(grade: GradeLevel): Set<MathOperation> {
        return when (grade) {
            GradeLevel.KINDERGARTEN, GradeLevel.GRADE_1 -> setOf(
                MathOperation.ADDITION,
                MathOperation.SUBTRACTION
            )
            GradeLevel.GRADE_2 -> setOf(
                MathOperation.ADDITION,
                MathOperation.SUBTRACTION,
                MathOperation.MULTIPLICATION
            )
            else -> setOf(
                MathOperation.ADDITION,
                MathOperation.SUBTRACTION,
                MathOperation.MULTIPLICATION,
                MathOperation.DIVISION
            )
        }
    }

    private fun generateProblems(
        difficulty: String,
        gradeLevel: GradeLevel,
        problemTypes: Set<MathOperation>,
        count: Int
    ): List<MathProblem> {
        val problems = mutableListOf<MathProblem>()
        val random = Random.Default
        
        // Ensure problemTypes is not empty
        if (problemTypes.isEmpty()) {
            return problems
        }
        
        repeat(count) {
            val operation = problemTypes.random(random)
            val problem = generateProblemForGrade(gradeLevel, operation, difficulty, random)
            problems.add(problem)
        }
        
        return problems
    }
    
    private fun generateProblemForGrade(
        grade: GradeLevel,
        operation: MathOperation,
        difficulty: String,
        random: Random
    ): MathProblem {
        return when (grade) {
            GradeLevel.KINDERGARTEN -> generateKindergartenProblem(operation, random)
            GradeLevel.GRADE_1 -> generateGrade1Problem(operation, random)
            GradeLevel.GRADE_2 -> generateGrade2Problem(operation, random)
            GradeLevel.GRADE_3 -> generateGrade3Problem(operation, random)
            GradeLevel.GRADE_4 -> generateGrade4Problem(operation, random)
            GradeLevel.GRADE_5 -> generateGrade5Problem(operation, random)
            GradeLevel.GRADE_6 -> generateGrade6Problem(operation, random)
            GradeLevel.GRADE_7 -> generateGrade7Problem(operation, random)
            GradeLevel.GRADE_8 -> generateGrade8Problem(operation, random)
            GradeLevel.GRADE_9 -> generateGrade9Problem(operation, random)
            GradeLevel.GRADE_10 -> generateGrade10Problem(operation, random)
            GradeLevel.GRADE_11 -> generateGrade11Problem(operation, random)
            GradeLevel.GRADE_12 -> generateGrade12Problem(operation, random)
            GradeLevel.BRAINROT -> generateGrade3Problem(operation, random) // Default to Grade 3 for BrainRot (not used in Math Game)
            GradeLevel.YEEPS -> generateGrade3Problem(operation, random) // Default to Grade 3 for Yeeps (not used in Math Game)
        }
    }

    // Grade-specific problem generators
    private fun generateKindergartenProblem(operation: MathOperation, random: Random): MathProblem {
        return when (operation) {
            MathOperation.ADDITION -> {
                val num1 = random.nextInt(1, 6)
                val num2 = random.nextInt(1, 6)
                val answer = num1 + num2
                createProblem(num1, num2, operation, answer, random, range = 5)
            }
            MathOperation.SUBTRACTION -> {
                val num1 = random.nextInt(2, 11)
                val num2 = random.nextInt(1, num1)
                val answer = num1 - num2
                createProblem(num1, num2, operation, answer, random, range = 5)
            }
            else -> generateKindergartenProblem(MathOperation.ADDITION, random)
        }
    }

    private fun generateGrade1Problem(operation: MathOperation, random: Random): MathProblem {
        return when (operation) {
            MathOperation.ADDITION -> {
                val num1 = random.nextInt(1, 11)
                val num2 = random.nextInt(1, 11)
                val answer = num1 + num2
                createProblem(num1, num2, operation, answer, random, range = 10)
            }
            MathOperation.SUBTRACTION -> {
                val num1 = random.nextInt(5, 21)
                val num2 = random.nextInt(1, num1)
                val answer = num1 - num2
                createProblem(num1, num2, operation, answer, random, range = 10)
            }
            else -> generateGrade1Problem(MathOperation.ADDITION, random)
        }
    }

    private fun generateGrade2Problem(operation: MathOperation, random: Random): MathProblem {
        return when (operation) {
            MathOperation.ADDITION -> {
                val num1 = random.nextInt(10, 51)
                val num2 = random.nextInt(10, 51)
                val answer = num1 + num2
                createProblem(num1, num2, operation, answer, random, range = 20)
            }
            MathOperation.SUBTRACTION -> {
                val num1 = random.nextInt(20, 101)
                val num2 = random.nextInt(10, num1)
                val answer = num1 - num2
                createProblem(num1, num2, operation, answer, random, range = 20)
            }
            MathOperation.MULTIPLICATION -> {
                val num1 = random.nextInt(2, 6)
                val num2 = random.nextInt(2, 6)
                val answer = num1 * num2
                createProblem(num1, num2, operation, answer, random, range = 10)
            }
            else -> generateGrade2Problem(MathOperation.ADDITION, random)
        }
    }

    private fun generateGrade3Problem(operation: MathOperation, random: Random): MathProblem {
        return when (operation) {
            MathOperation.ADDITION -> {
                val num1 = random.nextInt(10, 101)
                val num2 = random.nextInt(10, 101)
                val answer = num1 + num2
                createProblem(num1, num2, operation, answer, random, range = 30)
            }
            MathOperation.SUBTRACTION -> {
                val num1 = random.nextInt(50, 201)
                val num2 = random.nextInt(10, num1)
                val answer = num1 - num2
                createProblem(num1, num2, operation, answer, random, range = 30)
            }
            MathOperation.MULTIPLICATION -> {
                val num1 = random.nextInt(2, 10)
                val num2 = random.nextInt(2, 10)
                val answer = num1 * num2
                createProblem(num1, num2, operation, answer, random, range = 20)
            }
            MathOperation.DIVISION -> {
                val num2 = random.nextInt(2, 10)
                val answer = random.nextInt(2, 10)
                val num1 = num2 * answer
                createProblem(num1, num2, operation, answer, random, range = 20)
            }
        }
    }

    private fun generateGrade4Problem(operation: MathOperation, random: Random): MathProblem {
        return when (operation) {
            MathOperation.ADDITION -> {
                val num1 = random.nextInt(100, 1001)
                val num2 = random.nextInt(100, 1001)
                val answer = num1 + num2
                createProblem(num1, num2, operation, answer, random, range = 50)
            }
            MathOperation.SUBTRACTION -> {
                val num1 = random.nextInt(200, 2001)
                val num2 = random.nextInt(50, num1)
                val answer = num1 - num2
                createProblem(num1, num2, operation, answer, random, range = 50)
            }
            MathOperation.MULTIPLICATION -> {
                val num1 = random.nextInt(5, 13)
                val num2 = random.nextInt(5, 13)
                val answer = num1 * num2
                createProblem(num1, num2, operation, answer, random, range = 30)
            }
            MathOperation.DIVISION -> {
                val num2 = random.nextInt(3, 13)
                val answer = random.nextInt(3, 13)
                val num1 = num2 * answer
                createProblem(num1, num2, operation, answer, random, range = 30)
            }
        }
    }

    private fun generateGrade5Problem(operation: MathOperation, random: Random): MathProblem {
        return when (operation) {
            MathOperation.ADDITION -> {
                val num1 = random.nextInt(500, 5001)
                val num2 = random.nextInt(500, 5001)
                val answer = num1 + num2
                createProblem(num1, num2, operation, answer, random, range = 100)
            }
            MathOperation.SUBTRACTION -> {
                val num1 = random.nextInt(1000, 10001)
                val num2 = random.nextInt(100, num1)
                val answer = num1 - num2
                createProblem(num1, num2, operation, answer, random, range = 100)
            }
            MathOperation.MULTIPLICATION -> {
                val num1 = random.nextInt(10, 21)
                val num2 = random.nextInt(10, 21)
                val answer = num1 * num2
                createProblem(num1, num2, operation, answer, random, range = 50)
            }
            MathOperation.DIVISION -> {
                val num2 = random.nextInt(5, 16)
                val answer = random.nextInt(5, 16)
                val num1 = num2 * answer
                createProblem(num1, num2, operation, answer, random, range = 50)
            }
        }
    }

    private fun generateGrade6Problem(operation: MathOperation, random: Random): MathProblem {
        return when (operation) {
            MathOperation.ADDITION -> {
                val num1 = random.nextInt(1000, 10001)
                val num2 = random.nextInt(1000, 10001)
                val answer = num1 + num2
                createProblem(num1, num2, operation, answer, random, range = 200)
            }
            MathOperation.SUBTRACTION -> {
                val num1 = random.nextInt(5000, 50001)
                val num2 = random.nextInt(500, num1)
                val answer = num1 - num2
                createProblem(num1, num2, operation, answer, random, range = 200)
            }
            MathOperation.MULTIPLICATION -> {
                val num1 = random.nextInt(15, 26)
                val num2 = random.nextInt(15, 26)
                val answer = num1 * num2
                createProblem(num1, num2, operation, answer, random, range = 100)
            }
            MathOperation.DIVISION -> {
                val num2 = random.nextInt(6, 21)
                val answer = random.nextInt(6, 21)
                val num1 = num2 * answer
                createProblem(num1, num2, operation, answer, random, range = 100)
            }
        }
    }

    private fun generateGrade7Problem(operation: MathOperation, random: Random): MathProblem {
        return when (operation) {
            MathOperation.ADDITION -> {
                val num1 = random.nextInt(1000, 100001)
                val num2 = random.nextInt(1000, 100001)
                val answer = num1 + num2
                createProblem(num1, num2, operation, answer, random, range = 500)
            }
            MathOperation.SUBTRACTION -> {
                val num1 = random.nextInt(10000, 100001)
                val num2 = random.nextInt(1000, num1)
                val answer = num1 - num2
                createProblem(num1, num2, operation, answer, random, range = 500)
            }
            MathOperation.MULTIPLICATION -> {
                val num1 = random.nextInt(20, 51)
                val num2 = random.nextInt(20, 51)
                val answer = num1 * num2
                createProblem(num1, num2, operation, answer, random, range = 200)
            }
            MathOperation.DIVISION -> {
                val num2 = random.nextInt(10, 26)
                val answer = random.nextInt(10, 26)
                val num1 = num2 * answer
                createProblem(num1, num2, operation, answer, random, range = 200)
            }
        }
    }

    private fun generateGrade8Problem(operation: MathOperation, random: Random): MathProblem {
        return when (operation) {
            MathOperation.ADDITION -> {
                val num1 = random.nextInt(10000, 1000001)
                val num2 = random.nextInt(10000, 1000001)
                val answer = num1 + num2
                createProblem(num1, num2, operation, answer, random, range = 1000)
            }
            MathOperation.SUBTRACTION -> {
                val num1 = random.nextInt(100000, 10000001)
                val num2 = random.nextInt(10000, num1)
                val answer = num1 - num2
                createProblem(num1, num2, operation, answer, random, range = 1000)
            }
            MathOperation.MULTIPLICATION -> {
                val num1 = random.nextInt(25, 101)
                val num2 = random.nextInt(25, 101)
                val answer = num1 * num2
                createProblem(num1, num2, operation, answer, random, range = 500)
            }
            MathOperation.DIVISION -> {
                val num2 = random.nextInt(15, 51)
                val answer = random.nextInt(15, 51)
                val num1 = num2 * answer
                createProblem(num1, num2, operation, answer, random, range = 500)
            }
        }
    }

    private fun generateGrade9Problem(operation: MathOperation, random: Random): MathProblem {
        return generateGrade8Problem(operation, random) // Similar difficulty, can be enhanced later
    }

    private fun generateGrade10Problem(operation: MathOperation, random: Random): MathProblem {
        return generateGrade8Problem(operation, random) // Similar difficulty, can be enhanced later
    }

    private fun generateGrade11Problem(operation: MathOperation, random: Random): MathProblem {
        return generateGrade8Problem(operation, random) // Similar difficulty, can be enhanced later
    }

    private fun generateGrade12Problem(operation: MathOperation, random: Random): MathProblem {
        return generateGrade8Problem(operation, random) // Similar difficulty, can be enhanced later
    }

    private fun createProblem(
        num1: Int,
        num2: Int,
        operation: MathOperation,
        correctAnswer: Int,
        random: Random,
        range: Int = 20
    ): MathProblem {
        // Generate 2 wrong answers within a reasonable range (for 3 total choices)
        val wrongAnswers = mutableSetOf<Int>()
        while (wrongAnswers.size < 2) {
            val wrongAnswer = when {
                correctAnswer > 0 -> random.nextInt(
                    maxOf(1, correctAnswer - range),
                    correctAnswer + range + 1
                )
                else -> random.nextInt(-range, range + 1)
            }
            if (wrongAnswer != correctAnswer && wrongAnswer > 0) {
                wrongAnswers.add(wrongAnswer)
            }
        }
        
        // Combine correct and wrong answers, then shuffle
        val allAnswers = (wrongAnswers + correctAnswer).shuffled(random)
        val correctIndex = allAnswers.indexOf(correctAnswer)
        
        return MathProblem(
            num1 = num1,
            num2 = num2,
            operation = operation,
            correctAnswer = correctAnswer,
            answerChoices = allAnswers,
            correctAnswerIndex = correctIndex
        )
    }

    private fun randomizeAnswers(problem: MathProblem): MathProblem {
        val random = Random.Default
        val shuffled = problem.answerChoices.shuffled(random)
        val newCorrectIndex = shuffled.indexOf(problem.correctAnswer)
        
        return problem.copy(
            answerChoices = shuffled,
            correctAnswerIndex = newCorrectIndex
        )
    }

    fun selectAnswer(answerIndex: Int) {
        val currentState = _uiState.value
        if (currentState.showResult || currentState.isGameComplete) return

        val problem = currentState.problems[currentState.currentProblemIndex]
        val isCorrect = answerIndex == problem.correctAnswerIndex

        if (currentState.soundEnabled) {
            soundManager.playSound(
                if (isCorrect) SoundManager.SoundType.WIN else SoundManager.SoundType.LOSE
            )
        }

        val pointsEarned = if (isCorrect) {
            when (currentState.difficulty) {
                "easy" -> 1
                "medium" -> 2
                "hard" -> 3
                else -> 2
            }
        } else {
            0
        }

        val newScore = if (isCorrect) currentState.score + 1 else currentState.score
        val newTotalPoints = currentState.totalPoints + pointsEarned

        _uiState.value = currentState.copy(
            selectedAnswer = answerIndex,
            showResult = true,
            score = newScore,
            totalPoints = newTotalPoints
        )
    }

    fun nextProblem() {
        val currentState = _uiState.value
        val nextIndex = currentState.currentProblemIndex + 1

        if (nextIndex < currentState.problems.size) {
            _uiState.value = currentState.copy(
                currentProblemIndex = nextIndex,
                selectedAnswer = null,
                showResult = false
            )
        } else {
            // Game complete - check for high score
            val currentHighScore = currentState.highScore
            
            var updatedState = currentState.copy(
                isGameComplete = true,
                selectedAnswer = null,
                showResult = false
            )
            
            if (currentState.totalPoints > currentHighScore) {
                gamePreferencesManager.saveMathGameHighScore(currentState.totalPoints)
                updatedState = updatedState.copy(highScore = currentState.totalPoints)
                
                // Show celebration for high score
                val shouldShowCelebration = currentState.score >= currentState.problems.size * 0.8
                if (shouldShowCelebration) {
                    updatedState = updatedState.copy(showCelebration = true)
                }
            }
            
            _uiState.value = updatedState
        }
    }

    fun setDifficulty(difficulty: String) {
        gamePreferencesManager.saveMathGameDifficulty(difficulty)
        _uiState.value = _uiState.value.copy(difficulty = difficulty)
        startNewGame()
    }

    fun setGradeLevel(grade: GradeLevel) {
        gamePreferencesManager.saveMathGameGradeLevel(grade)
        val defaultTypes = getDefaultProblemTypesForGrade(grade)
        gamePreferencesManager.saveMathGameProblemTypes(defaultTypes)
        _uiState.value = _uiState.value.copy(
            gradeLevel = grade,
            problemTypes = defaultTypes
        )
        startNewGame()
    }

    fun setProblemTypes(types: Set<MathOperation>) {
        gamePreferencesManager.saveMathGameProblemTypes(types)
        _uiState.value = _uiState.value.copy(problemTypes = types)
        startNewGame()
    }

    fun setSoundEnabled(enabled: Boolean) {
        gamePreferencesManager.setSoundEnabled(enabled)
        _uiState.value = _uiState.value.copy(soundEnabled = enabled)
    }

    fun onCelebrationComplete() {
        _uiState.value = _uiState.value.copy(showCelebration = false)
    }
}
