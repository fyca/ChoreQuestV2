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

data class WordWithTheme(
    val word: String,
    val theme: String
)

data class HangmanUiState(
    val currentWord: String = "",
    val displayWord: String = "",
    val theme: String = "",
    val guessedLetters: Set<Char> = emptySet(),
    val wrongGuesses: Int = 0,
    val maxWrongGuesses: Int = 6,
    val hasExtraGuesses: Boolean = false, // For easy mode - tracks if extra guesses were granted
    val hintsUsed: Int = 0, // Track how many hints have been used
    val isGameWon: Boolean = false,
    val isGameLost: Boolean = false,
    val showWinDialog: Boolean = false,
    val showLoseDialog: Boolean = false,
    val showExtraGuessesDialog: Boolean = false, // Dialog to ask for more guesses in easy mode
    val difficulty: String = "medium",
    val highScore: Int = 0,
    val currentScore: Int = 0,
    val soundEnabled: Boolean = true
)

@HiltViewModel
class HangmanViewModel @Inject constructor(
    private val gamePreferencesManager: GamePreferencesManager,
    private val soundManager: SoundManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HangmanUiState(
            difficulty = gamePreferencesManager.getHangmanDifficulty(),
            highScore = gamePreferencesManager.getHangmanHighScore(),
            soundEnabled = gamePreferencesManager.isSoundEnabled()
        )
    )
    val uiState: StateFlow<HangmanUiState> = _uiState.asStateFlow()

    private val wordLists = mapOf(
        "easy" to listOf(
            // Animals
            WordWithTheme("CAT", "Animals"),
            WordWithTheme("DOG", "Animals"),
            WordWithTheme("FISH", "Animals"),
            WordWithTheme("BIRD", "Animals"),
            WordWithTheme("MOUSE", "Animals"),
            WordWithTheme("BEAR", "Animals"),
            WordWithTheme("LION", "Animals"),
            WordWithTheme("TIGER", "Animals"),
            WordWithTheme("DUCK", "Animals"),
            WordWithTheme("FROG", "Animals"),
            WordWithTheme("PIG", "Animals"),
            WordWithTheme("COW", "Animals"),
            WordWithTheme("HORSE", "Animals"),
            WordWithTheme("SHEEP", "Animals"),
            WordWithTheme("GOAT", "Animals"),
            WordWithTheme("DEER", "Animals"),
            WordWithTheme("WOLF", "Animals"),
            WordWithTheme("FOX", "Animals"),
            WordWithTheme("RABBIT", "Animals"),
            WordWithTheme("SNAKE", "Animals"),
            // Nature
            WordWithTheme("SUN", "Nature"),
            WordWithTheme("TREE", "Nature"),
            WordWithTheme("FLOWER", "Nature"),
            WordWithTheme("GRASS", "Nature"),
            WordWithTheme("LEAF", "Nature"),
            WordWithTheme("ROCK", "Nature"),
            WordWithTheme("WATER", "Nature"),
            WordWithTheme("RAIN", "Nature"),
            WordWithTheme("SNOW", "Nature"),
            WordWithTheme("CLOUD", "Nature"),
            WordWithTheme("WIND", "Nature"),
            WordWithTheme("FIRE", "Nature"),
            // Space
            WordWithTheme("MOON", "Space"),
            WordWithTheme("STAR", "Space"),
            WordWithTheme("EARTH", "Space"),
            WordWithTheme("SUN", "Space"),
            WordWithTheme("PLANET", "Space"),
            // Body Parts
            WordWithTheme("HAND", "Body Parts"),
            WordWithTheme("FOOT", "Body Parts"),
            WordWithTheme("EYE", "Body Parts"),
            WordWithTheme("EAR", "Body Parts"),
            WordWithTheme("NOSE", "Body Parts"),
            WordWithTheme("MOUTH", "Body Parts"),
            WordWithTheme("HEAD", "Body Parts"),
            WordWithTheme("ARM", "Body Parts"),
            WordWithTheme("LEG", "Body Parts"),
            WordWithTheme("TOE", "Body Parts"),
            WordWithTheme("FINGER", "Body Parts"),
            WordWithTheme("HAIR", "Body Parts"),
            // Food
            WordWithTheme("CAKE", "Food"),
            WordWithTheme("APPLE", "Food"),
            WordWithTheme("BREAD", "Food"),
            WordWithTheme("MILK", "Food"),
            WordWithTheme("EGG", "Food"),
            WordWithTheme("RICE", "Food"),
            WordWithTheme("SUGAR", "Food"),
            WordWithTheme("SALT", "Food"),
            WordWithTheme("MEAT", "Food"),
            WordWithTheme("FISH", "Food"),
            WordWithTheme("CHICKEN", "Food"),
            WordWithTheme("PIZZA", "Food"),
            // Toys
            WordWithTheme("BALL", "Toys"),
            WordWithTheme("TOY", "Toys"),
            WordWithTheme("DOLL", "Toys"),
            WordWithTheme("BLOCK", "Toys"),
            WordWithTheme("PUZZLE", "Toys"),
            WordWithTheme("CAR", "Toys"),
            WordWithTheme("TRUCK", "Toys"),
            WordWithTheme("TEDDY", "Toys"),
            // School
            WordWithTheme("BOOK", "School"),
            WordWithTheme("PEN", "School"),
            WordWithTheme("PAPER", "School"),
            WordWithTheme("DESK", "School"),
            WordWithTheme("CHAIR", "School"),
            WordWithTheme("TEACHER", "School"),
            WordWithTheme("STUDENT", "School"),
            WordWithTheme("CLASS", "School"),
            // Places
            WordWithTheme("HOUSE", "Places"),
            WordWithTheme("PARK", "Places"),
            WordWithTheme("STORE", "Places"),
            WordWithTheme("SCHOOL", "Places"),
            WordWithTheme("HOME", "Places"),
            WordWithTheme("ROOM", "Places"),
            WordWithTheme("YARD", "Places"),
            // Transportation
            WordWithTheme("CAR", "Transportation"),
            WordWithTheme("BIKE", "Transportation"),
            WordWithTheme("BUS", "Transportation"),
            WordWithTheme("TRAIN", "Transportation"),
            WordWithTheme("BOAT", "Transportation"),
            WordWithTheme("PLANE", "Transportation"),
            // Feelings
            WordWithTheme("HAPPY", "Feelings"),
            WordWithTheme("SAD", "Feelings"),
            WordWithTheme("ANGRY", "Feelings"),
            WordWithTheme("EXCITED", "Feelings"),
            WordWithTheme("SCARED", "Feelings"),
            WordWithTheme("PROUD", "Feelings"),
            // Colors
            WordWithTheme("RED", "Colors"),
            WordWithTheme("BLUE", "Colors"),
            WordWithTheme("GREEN", "Colors"),
            WordWithTheme("YELLOW", "Colors"),
            WordWithTheme("ORANGE", "Colors"),
            WordWithTheme("PURPLE", "Colors"),
            WordWithTheme("PINK", "Colors"),
            WordWithTheme("BLACK", "Colors"),
            WordWithTheme("WHITE", "Colors"),
            WordWithTheme("BROWN", "Colors"),
            // Fun
            WordWithTheme("GAME", "Fun"),
            WordWithTheme("FUN", "Fun"),
            WordWithTheme("PLAY", "Fun"),
            WordWithTheme("PARTY", "Fun"),
            WordWithTheme("DANCE", "Fun"),
            WordWithTheme("SING", "Fun"),
            WordWithTheme("LAUGH", "Fun")
        ),
        "medium" to listOf(
            // Animals
            WordWithTheme("ELEPHANT", "Animals"),
            WordWithTheme("BUTTERFLY", "Animals"),
            WordWithTheme("DOLPHIN", "Animals"),
            WordWithTheme("WHALE", "Animals"),
            WordWithTheme("SHARK", "Animals"),
            WordWithTheme("EAGLE", "Animals"),
            WordWithTheme("OWL", "Animals"),
            WordWithTheme("PANDA", "Animals"),
            WordWithTheme("KANGAROO", "Animals"),
            WordWithTheme("GIRAFFE", "Animals"),
            WordWithTheme("ZEBRA", "Animals"),
            WordWithTheme("MONKEY", "Animals"),
            WordWithTheme("TURTLE", "Animals"),
            WordWithTheme("CROCODILE", "Animals"),
            WordWithTheme("PENGUIN", "Animals"),
            WordWithTheme("POLAR", "Animals"),
            WordWithTheme("BEAR", "Animals"),
            WordWithTheme("WOLF", "Animals"),
            WordWithTheme("FOX", "Animals"),
            WordWithTheme("RABBIT", "Animals"),
            // Nature
            WordWithTheme("MOUNTAIN", "Nature"),
            WordWithTheme("FOREST", "Nature"),
            WordWithTheme("RIVER", "Nature"),
            WordWithTheme("OCEAN", "Nature"),
            WordWithTheme("WATERFALL", "Nature"),
            WordWithTheme("VOLCANO", "Nature"),
            WordWithTheme("DESERT", "Nature"),
            WordWithTheme("ISLAND", "Nature"),
            WordWithTheme("VALLEY", "Nature"),
            WordWithTheme("CAVE", "Nature"),
            WordWithTheme("LAKE", "Nature"),
            WordWithTheme("STREAM", "Nature"),
            WordWithTheme("MEADOW", "Nature"),
            WordWithTheme("JUNGLE", "Nature"),
            // Space
            WordWithTheme("PLANET", "Space"),
            WordWithTheme("SPACE", "Space"),
            WordWithTheme("ROCKET", "Space"),
            WordWithTheme("ASTRONAUT", "Space"),
            WordWithTheme("COMET", "Space"),
            WordWithTheme("GALAXY", "Space"),
            WordWithTheme("TELESCOPE", "Space"),
            WordWithTheme("SATELLITE", "Space"),
            // Technology
            WordWithTheme("COMPUTER", "Technology"),
            WordWithTheme("TABLET", "Technology"),
            WordWithTheme("PHONE", "Technology"),
            WordWithTheme("CAMERA", "Technology"),
            WordWithTheme("ROBOT", "Technology"),
            WordWithTheme("INTERNET", "Technology"),
            WordWithTheme("EMAIL", "Technology"),
            WordWithTheme("VIDEO", "Technology"),
            // People
            WordWithTheme("TEACHER", "People"),
            WordWithTheme("STUDENT", "People"),
            WordWithTheme("FRIEND", "People"),
            WordWithTheme("FAMILY", "People"),
            WordWithTheme("DOCTOR", "People"),
            WordWithTheme("NURSE", "People"),
            WordWithTheme("COOK", "People"),
            WordWithTheme("ARTIST", "People"),
            WordWithTheme("MUSICIAN", "People"),
            WordWithTheme("ATHLETE", "People"),
            WordWithTheme("HERO", "People"),
            WordWithTheme("LEADER", "People"),
            // Places
            WordWithTheme("LIBRARY", "Places"),
            WordWithTheme("SCHOOL", "Places"),
            WordWithTheme("GARDEN", "Places"),
            WordWithTheme("PARK", "Places"),
            WordWithTheme("BEACH", "Places"),
            WordWithTheme("KITCHEN", "Places"),
            WordWithTheme("BEDROOM", "Places"),
            WordWithTheme("BATHROOM", "Places"),
            WordWithTheme("HOSPITAL", "Places"),
            WordWithTheme("MUSEUM", "Places"),
            WordWithTheme("THEATER", "Places"),
            WordWithTheme("STADIUM", "Places"),
            WordWithTheme("AIRPORT", "Places"),
            WordWithTheme("HARBOR", "Places"),
            WordWithTheme("BRIDGE", "Places"),
            WordWithTheme("TOWER", "Places"),
            // Transportation
            WordWithTheme("TRAIN", "Transportation"),
            WordWithTheme("AIRPLANE", "Transportation"),
            WordWithTheme("BICYCLE", "Transportation"),
            WordWithTheme("HELICOPTER", "Transportation"),
            WordWithTheme("SUBWAY", "Transportation"),
            WordWithTheme("FERRY", "Transportation"),
            WordWithTheme("TRUCK", "Transportation"),
            WordWithTheme("MOTORCYCLE", "Transportation"),
            WordWithTheme("SCOOTER", "Transportation"),
            // Home
            WordWithTheme("WINDOW", "Home"),
            WordWithTheme("DOOR", "Home"),
            WordWithTheme("ROOF", "Home"),
            WordWithTheme("FLOOR", "Home"),
            WordWithTheme("WALL", "Home"),
            WordWithTheme("CEILING", "Home"),
            WordWithTheme("GARAGE", "Home"),
            WordWithTheme("ATTIC", "Home"),
            // Furniture
            WordWithTheme("TABLE", "Furniture"),
            WordWithTheme("CHAIR", "Furniture"),
            WordWithTheme("SOFA", "Furniture"),
            WordWithTheme("BED", "Furniture"),
            WordWithTheme("DESK", "Furniture"),
            WordWithTheme("CABINET", "Furniture"),
            WordWithTheme("SHELF", "Furniture"),
            // School
            WordWithTheme("PENCIL", "School"),
            WordWithTheme("PAPER", "School"),
            WordWithTheme("ERASER", "School"),
            WordWithTheme("RULER", "School"),
            WordWithTheme("BACKPACK", "School"),
            WordWithTheme("NOTEBOOK", "School"),
            WordWithTheme("CALCULATOR", "School"),
            WordWithTheme("GLOBE", "School"),
            WordWithTheme("MAP", "School"),
            // Art
            WordWithTheme("CRAYON", "Art"),
            WordWithTheme("COLOR", "Art"),
            WordWithTheme("PAINT", "Art"),
            WordWithTheme("BRUSH", "Art"),
            WordWithTheme("CANVAS", "Art"),
            WordWithTheme("SCULPTURE", "Art"),
            WordWithTheme("DRAWING", "Art"),
            // Arts
            WordWithTheme("MUSIC", "Arts"),
            WordWithTheme("DANCE", "Arts"),
            WordWithTheme("SONG", "Arts"),
            WordWithTheme("INSTRUMENT", "Arts"),
            WordWithTheme("PIANO", "Arts"),
            WordWithTheme("GUITAR", "Arts"),
            WordWithTheme("DRUM", "Arts"),
            WordWithTheme("VIOLIN", "Arts"),
            // Sports
            WordWithTheme("SOCCER", "Sports"),
            WordWithTheme("BASKETBALL", "Sports"),
            WordWithTheme("BASEBALL", "Sports"),
            WordWithTheme("FOOTBALL", "Sports"),
            WordWithTheme("TENNIS", "Sports"),
            WordWithTheme("SWIMMING", "Sports"),
            WordWithTheme("RUNNING", "Sports"),
            WordWithTheme("JUMPING", "Sports"),
            // Food
            WordWithTheme("SANDWICH", "Food"),
            WordWithTheme("COOKIE", "Food"),
            WordWithTheme("CANDY", "Food"),
            WordWithTheme("FRUIT", "Food"),
            WordWithTheme("VEGETABLE", "Food"),
            WordWithTheme("CEREAL", "Food"),
            WordWithTheme("JUICE", "Food"),
            WordWithTheme("SOUP", "Food"),
            WordWithTheme("SALAD", "Food"),
            WordWithTheme("PASTA", "Food"),
            // Weather
            WordWithTheme("SUNNY", "Weather"),
            WordWithTheme("RAINY", "Weather"),
            WordWithTheme("CLOUDY", "Weather"),
            WordWithTheme("WINDY", "Weather"),
            WordWithTheme("STORM", "Weather"),
            WordWithTheme("THUNDER", "Weather"),
            WordWithTheme("LIGHTNING", "Weather"),
            WordWithTheme("RAINBOW", "Weather")
        ),
        "hard" to listOf(
            // Activities
            WordWithTheme("ADVENTURE", "Activities"),
            WordWithTheme("DISCOVERY", "Activities"),
            WordWithTheme("EXPLORATION", "Activities"),
            WordWithTheme("CHALLENGE", "Activities"),
            WordWithTheme("JOURNEY", "Activities"),
            WordWithTheme("EXPEDITION", "Activities"),
            WordWithTheme("MISSION", "Activities"),
            WordWithTheme("QUEST", "Activities"),
            // Education
            WordWithTheme("KNOWLEDGE", "Education"),
            WordWithTheme("EDUCATION", "Education"),
            WordWithTheme("LEARNING", "Education"),
            WordWithTheme("STUDYING", "Education"),
            WordWithTheme("RESEARCH", "Education"),
            WordWithTheme("SCHOLARSHIP", "Education"),
            WordWithTheme("ACADEMIC", "Education"),
            WordWithTheme("UNIVERSITY", "Education"),
            WordWithTheme("COLLEGE", "Education"),
            WordWithTheme("GRADUATE", "Education"),
            // Creativity
            WordWithTheme("IMAGINATION", "Creativity"),
            WordWithTheme("CREATIVITY", "Creativity"),
            WordWithTheme("INSPIRATION", "Creativity"),
            WordWithTheme("INNOVATION", "Creativity"),
            WordWithTheme("ORIGINALITY", "Creativity"),
            WordWithTheme("ARTISTIC", "Creativity"),
            // Personality
            WordWithTheme("CURIOSITY", "Personality"),
            WordWithTheme("CONFIDENCE", "Personality"),
            WordWithTheme("COURAGE", "Personality"),
            WordWithTheme("DETERMINATION", "Personality"),
            WordWithTheme("PERSISTENCE", "Personality"),
            WordWithTheme("OPTIMISM", "Personality"),
            WordWithTheme("ENTHUSIASM", "Personality"),
            // Feelings
            WordWithTheme("WONDERFUL", "Feelings"),
            WordWithTheme("EXCITEMENT", "Feelings"),
            WordWithTheme("JOYFUL", "Feelings"),
            WordWithTheme("GRATEFUL", "Feelings"),
            WordWithTheme("PEACEFUL", "Feelings"),
            WordWithTheme("CONTENT", "Feelings"),
            // Descriptions
            WordWithTheme("BEAUTIFUL", "Descriptions"),
            WordWithTheme("FANTASTIC", "Descriptions"),
            WordWithTheme("AMAZING", "Descriptions"),
            WordWithTheme("INCREDIBLE", "Descriptions"),
            WordWithTheme("SPECTACULAR", "Descriptions"),
            WordWithTheme("MAGNIFICENT", "Descriptions"),
            WordWithTheme("EXTRAORDINARY", "Descriptions"),
            WordWithTheme("REMARKABLE", "Descriptions"),
            WordWithTheme("BRILLIANT", "Descriptions"),
            WordWithTheme("EXCEPTIONAL", "Descriptions"),
            // Jobs
            WordWithTheme("ASTRONAUT", "Jobs"),
            WordWithTheme("SCIENTIST", "Jobs"),
            WordWithTheme("EXPLORER", "Jobs"),
            WordWithTheme("INVENTOR", "Jobs"),
            WordWithTheme("ARTIST", "Jobs"),
            WordWithTheme("MUSICIAN", "Jobs"),
            WordWithTheme("ATHLETE", "Jobs"),
            WordWithTheme("ENGINEER", "Jobs"),
            WordWithTheme("ARCHITECT", "Jobs"),
            WordWithTheme("CHEF", "Jobs"),
            WordWithTheme("WRITER", "Jobs"),
            WordWithTheme("JOURNALIST", "Jobs"),
            WordWithTheme("PHOTOGRAPHER", "Jobs"),
            WordWithTheme("DIRECTOR", "Jobs"),
            WordWithTheme("PROFESSOR", "Jobs"),
            WordWithTheme("LAWYER", "Jobs"),
            WordWithTheme("DOCTOR", "Jobs"),
            WordWithTheme("NURSE", "Jobs"),
            WordWithTheme("PILOT", "Jobs"),
            WordWithTheme("CAPTAIN", "Jobs"),
            // Achievements
            WordWithTheme("CHAMPION", "Achievements"),
            WordWithTheme("VICTORY", "Achievements"),
            WordWithTheme("SUCCESS", "Achievements"),
            WordWithTheme("ACHIEVEMENT", "Achievements"),
            WordWithTheme("ACCOMPLISHMENT", "Achievements"),
            WordWithTheme("TRIUMPH", "Achievements"),
            WordWithTheme("EXCELLENCE", "Achievements"),
            WordWithTheme("MASTERY", "Achievements"),
            // Concepts
            WordWithTheme("OPPORTUNITY", "Concepts"),
            WordWithTheme("POSSIBILITY", "Concepts"),
            WordWithTheme("POTENTIAL", "Concepts"),
            WordWithTheme("FREEDOM", "Concepts"),
            WordWithTheme("JUSTICE", "Concepts"),
            WordWithTheme("EQUALITY", "Concepts"),
            WordWithTheme("HARMONY", "Concepts"),
            WordWithTheme("BALANCE", "Concepts"),
            WordWithTheme("WISDOM", "Concepts"),
            WordWithTheme("UNDERSTANDING", "Concepts"),
            // Science
            WordWithTheme("EXPERIMENT", "Science"),
            WordWithTheme("HYPOTHESIS", "Science"),
            WordWithTheme("THEORY", "Science"),
            WordWithTheme("DISCOVERY", "Science"),
            WordWithTheme("INVENTION", "Science"),
            WordWithTheme("TECHNOLOGY", "Science"),
            WordWithTheme("CHEMISTRY", "Science"),
            WordWithTheme("PHYSICS", "Science"),
            WordWithTheme("BIOLOGY", "Science"),
            WordWithTheme("ASTRONOMY", "Science"),
            // Geography
            WordWithTheme("CONTINENT", "Geography"),
            WordWithTheme("COUNTRY", "Geography"),
            WordWithTheme("CITY", "Geography"),
            WordWithTheme("VILLAGE", "Geography"),
            WordWithTheme("CULTURE", "Geography"),
            WordWithTheme("LANGUAGE", "Geography"),
            WordWithTheme("TRADITION", "Geography"),
            // History
            WordWithTheme("ANCIENT", "History"),
            WordWithTheme("CIVILIZATION", "History"),
            WordWithTheme("EMPIRE", "History"),
            WordWithTheme("KINGDOM", "History"),
            WordWithTheme("REVOLUTION", "History"),
            WordWithTheme("INDEPENDENCE", "History"),
            // Literature
            WordWithTheme("STORY", "Literature"),
            WordWithTheme("NOVEL", "Literature"),
            WordWithTheme("POETRY", "Literature"),
            WordWithTheme("CHARACTER", "Literature"),
            WordWithTheme("PLOT", "Literature"),
            WordWithTheme("AUTHOR", "Literature"),
            WordWithTheme("LIBRARY", "Literature"),
            // Music
            WordWithTheme("MELODY", "Music"),
            WordWithTheme("RHYTHM", "Music"),
            WordWithTheme("HARMONY", "Music"),
            WordWithTheme("SYMPHONY", "Music"),
            WordWithTheme("ORCHESTRA", "Music"),
            WordWithTheme("CONCERT", "Music"),
            WordWithTheme("PERFORMANCE", "Music"),
            // Nature Advanced
            WordWithTheme("ECOSYSTEM", "Nature"),
            WordWithTheme("ENVIRONMENT", "Nature"),
            WordWithTheme("CONSERVATION", "Nature"),
            WordWithTheme("WILDLIFE", "Nature"),
            WordWithTheme("HABITAT", "Nature"),
            WordWithTheme("BIODIVERSITY", "Nature")
        )
    )

    init {
        startNewGame()
    }

    fun startNewGame() {
        val difficulty = _uiState.value.difficulty
        val wordList = wordLists[difficulty] ?: wordLists["medium"]!!
        val selectedWordWithTheme = wordList.random()
        val selectedWord = selectedWordWithTheme.word.uppercase()
        
        _uiState.value = _uiState.value.copy(
            currentWord = selectedWord,
            displayWord = "_".repeat(selectedWord.length),
            theme = selectedWordWithTheme.theme,
            guessedLetters = emptySet(),
            wrongGuesses = 0,
            maxWrongGuesses = 6,
            hasExtraGuesses = false,
            hintsUsed = 0,
            isGameWon = false,
            isGameLost = false,
            showWinDialog = false,
            showLoseDialog = false,
            showExtraGuessesDialog = false,
            currentScore = 0
        )
    }

    fun guessLetter(letter: Char) {
        val currentState = _uiState.value
        val upperLetter = letter.uppercaseChar()

        // Check if game is already over
        if (currentState.isGameWon || currentState.isGameLost) {
            return
        }

        // Check if letter was already guessed
        if (upperLetter in currentState.guessedLetters) {
            return
        }

        val newGuessedLetters = currentState.guessedLetters + upperLetter
        val isCorrectGuess = upperLetter in currentState.currentWord

        val newWrongGuesses = if (isCorrectGuess) {
            currentState.wrongGuesses
        } else {
            currentState.wrongGuesses + 1
        }

        // Update display word
        val newDisplayWord = currentState.currentWord.map { char ->
            if (char in newGuessedLetters) char else '_'
        }.joinToString("")

        // Check win condition
        val isWon = !newDisplayWord.contains('_')
        
        // In easy mode, if we reach 6 wrong guesses and haven't used extra guesses yet, show dialog
        val shouldShowExtraGuessesDialog = !isCorrectGuess && 
            currentState.difficulty == "easy" && 
            newWrongGuesses == 6 && 
            !currentState.hasExtraGuesses &&
            !isWon
        
        val isLost = if (shouldShowExtraGuessesDialog) {
            false // Don't lose yet, show dialog instead
        } else {
            newWrongGuesses >= currentState.maxWrongGuesses
        }

        // Play sound
        if (currentState.soundEnabled) {
            if (isCorrectGuess) {
                soundManager.playSound(SoundManager.SoundType.CLICK)
            } else {
                soundManager.playSound(SoundManager.SoundType.LOSE)
            }
        }

        // Calculate score (points for correct guesses, bonus for winning)
        val newScore = if (isCorrectGuess) {
            currentState.currentScore + 10
        } else {
            currentState.currentScore
        }

        val finalScore = if (isWon) {
            newScore + 100 // Bonus for winning
        } else {
            newScore
        }

        _uiState.value = currentState.copy(
            displayWord = newDisplayWord,
            guessedLetters = newGuessedLetters,
            wrongGuesses = newWrongGuesses,
            isGameWon = isWon,
            isGameLost = isLost,
            showWinDialog = isWon,
            showLoseDialog = isLost,
            showExtraGuessesDialog = shouldShowExtraGuessesDialog,
            currentScore = finalScore
        )

        // Update high score if won
        if (isWon && finalScore > currentState.highScore) {
            viewModelScope.launch {
                gamePreferencesManager.saveHangmanHighScore(finalScore)
            }
            _uiState.value = _uiState.value.copy(highScore = finalScore)
        }

        if (isWon && currentState.soundEnabled) {
            soundManager.playSound(SoundManager.SoundType.WIN)
        }
    }

    fun dismissWinDialog() {
        _uiState.value = _uiState.value.copy(showWinDialog = false)
    }

    fun dismissLoseDialog() {
        _uiState.value = _uiState.value.copy(showLoseDialog = false)
    }

    fun grantExtraGuesses() {
        _uiState.value = _uiState.value.copy(
            maxWrongGuesses = 9, // 6 original + 3 extra
            hasExtraGuesses = true,
            showExtraGuessesDialog = false
        )
    }

    fun declineExtraGuesses() {
        // Game ends normally
        _uiState.value = _uiState.value.copy(
            isGameLost = true,
            showLoseDialog = true,
            showExtraGuessesDialog = false
        )
    }

    fun dismissExtraGuessesDialog() {
        _uiState.value = _uiState.value.copy(showExtraGuessesDialog = false)
    }

    fun setDifficulty(difficulty: String) {
        gamePreferencesManager.saveHangmanDifficulty(difficulty)
        _uiState.value = _uiState.value.copy(difficulty = difficulty)
        startNewGame()
    }

    fun setSoundEnabled(enabled: Boolean) {
        gamePreferencesManager.setSoundEnabled(enabled)
        _uiState.value = _uiState.value.copy(soundEnabled = enabled)
    }

    fun useHint() {
        val currentState = _uiState.value
        
        // Can't use hint if game is over or word is already solved
        if (currentState.isGameWon || 
            currentState.isGameLost ||
            !currentState.displayWord.contains('_')) {
            return
        }

        // Find a letter that hasn't been guessed yet and is in the word
        val unguessedLetters = currentState.currentWord
            .filter { it !in currentState.guessedLetters }
            .toSet()
            .toList()
        
        if (unguessedLetters.isEmpty()) {
            return // All letters are already guessed
        }

        // Pick a random unguessed letter from the word
        val hintLetter = unguessedLetters.random()
        
        // Add it to guessed letters (but don't count as wrong guess)
        val newGuessedLetters = currentState.guessedLetters + hintLetter
        
        // Update display word
        val newDisplayWord = currentState.currentWord.map { char ->
            if (char in newGuessedLetters) char else '_'
        }.joinToString("")

        // Check if this hint solved the word
        val isWon = !newDisplayWord.contains('_')

        _uiState.value = currentState.copy(
            displayWord = newDisplayWord,
            guessedLetters = newGuessedLetters,
            hintsUsed = currentState.hintsUsed + 1,
            isGameWon = isWon,
            showWinDialog = isWon
            // Note: No point deduction - hints are free!
        )

        // Update high score if won
        if (isWon && currentState.currentScore > currentState.highScore) {
            viewModelScope.launch {
                gamePreferencesManager.saveHangmanHighScore(currentState.currentScore)
            }
            _uiState.value = _uiState.value.copy(highScore = currentState.currentScore)
        }

        if (isWon && currentState.soundEnabled) {
            soundManager.playSound(SoundManager.SoundType.WIN)
        } else if (currentState.soundEnabled) {
            soundManager.playSound(SoundManager.SoundType.CLICK)
        }
    }
}
