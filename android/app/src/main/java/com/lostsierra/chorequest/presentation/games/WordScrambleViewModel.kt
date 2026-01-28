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

data class LetterTile(
    val id: Int,
    val letter: Char,
    val position: Int,
    val isLocked: Boolean = false
)

data class WordScrambleUiState(
    val currentWord: String = "",
    val theme: String = "",
    val letterTiles: List<LetterTile> = emptyList(),
    val words: List<String> = emptyList(),
    val currentWordIndex: Int = 0,
    val score: Int = 0,
    val correctAnswers: Int = 0,
    val totalWords: Int = 10,
    val showResult: Boolean = false,
    val isCorrect: Boolean = false,
    val showCelebration: Boolean = false,
    val isGameComplete: Boolean = false,
    val gradeLevel: GradeLevel = GradeLevel.GRADE_3,
    val highScore: Int = 0,
    val soundEnabled: Boolean = true,
    val startTime: Long = 0,
    val currentTime: Long = 0,
    val streakCount: Int = 0,
    val isDragging: Boolean = false,
    val draggedTileId: Int? = null
)

@HiltViewModel
class WordScrambleViewModel @Inject constructor(
    private val gamePreferencesManager: GamePreferencesManager,
    private val soundManager: SoundManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        WordScrambleUiState(
            gradeLevel = gamePreferencesManager.getWordScrambleGradeLevel(),
            highScore = gamePreferencesManager.getWordScrambleHighScore(),
            soundEnabled = gamePreferencesManager.isSoundEnabled()
        )
    )
    val uiState: StateFlow<WordScrambleUiState> = _uiState.asStateFlow()

    private val wordLists = mapOf(
        GradeLevel.KINDERGARTEN to listOf(
            WordWithTheme("CAT", "Animals"), WordWithTheme("DOG", "Animals"), WordWithTheme("FISH", "Animals"), WordWithTheme("BIRD", "Animals"),
            WordWithTheme("COW", "Animals"), WordWithTheme("PIG", "Animals"), WordWithTheme("DUCK", "Animals"), WordWithTheme("BEAR", "Animals"),
            WordWithTheme("LION", "Animals"), WordWithTheme("FROG", "Animals"), WordWithTheme("BUG", "Animals"), WordWithTheme("ANT", "Animals"),
            WordWithTheme("BEE", "Animals"),
            WordWithTheme("SUN", "Nature"), WordWithTheme("MOON", "Nature"), WordWithTheme("STAR", "Nature"), WordWithTheme("TREE", "Nature"),
            WordWithTheme("RED", "Colors"), WordWithTheme("BLUE", "Colors"), WordWithTheme("GREEN", "Colors"), WordWithTheme("YELLOW", "Colors"),
            WordWithTheme("BROWN", "Colors"), WordWithTheme("BLACK", "Colors"), WordWithTheme("WHITE", "Colors"), WordWithTheme("PINK", "Colors"),
            WordWithTheme("ORANGE", "Colors"), WordWithTheme("PURPLE", "Colors"),
            WordWithTheme("BOOK", "Objects"), WordWithTheme("BALL", "Objects"), WordWithTheme("TOY", "Objects"), WordWithTheme("CAR", "Objects"),
            WordWithTheme("HAT", "Objects"), WordWithTheme("CUP", "Objects"), WordWithTheme("BOX", "Objects"), WordWithTheme("BED", "Objects"),
            WordWithTheme("PEN", "Objects"), WordWithTheme("MAP", "Objects"), WordWithTheme("BAG", "Objects"), WordWithTheme("EGG", "Food"),
            WordWithTheme("MOM", "Family"), WordWithTheme("DAD", "Family"), WordWithTheme("BOY", "Family"), WordWithTheme("GIRL", "Family"),
            WordWithTheme("BABY", "Family"), WordWithTheme("KID", "Family"), WordWithTheme("FRIEND", "Family"),
            WordWithTheme("LOVE", "Feelings"), WordWithTheme("HAPPY", "Feelings"), WordWithTheme("SAD", "Feelings")
        ),
        GradeLevel.GRADE_1 to listOf(
            WordWithTheme("HOUSE", "Places"), WordWithTheme("SCHOOL", "Places"), WordWithTheme("PARK", "Places"), WordWithTheme("STORE", "Places"),
            WordWithTheme("ZOO", "Places"), WordWithTheme("FARM", "Places"), WordWithTheme("BEACH", "Places"), WordWithTheme("MOUNTAIN", "Places"),
            WordWithTheme("RIVER", "Places"), WordWithTheme("LAKE", "Places"), WordWithTheme("OCEAN", "Places"), WordWithTheme("FOREST", "Places"),
            WordWithTheme("WATER", "Nature"), WordWithTheme("FIRE", "Nature"), WordWithTheme("EARTH", "Nature"), WordWithTheme("WIND", "Nature"),
            WordWithTheme("RAIN", "Nature"), WordWithTheme("SNOW", "Nature"), WordWithTheme("CLOUD", "Nature"), WordWithTheme("FLOWER", "Nature"),
            WordWithTheme("GRASS", "Nature"), WordWithTheme("LEAF", "Nature"),
            WordWithTheme("APPLE", "Food"), WordWithTheme("BANANA", "Food"), WordWithTheme("ORANGE", "Food"), WordWithTheme("GRAPE", "Food"),
            WordWithTheme("BREAD", "Food"), WordWithTheme("MILK", "Food"), WordWithTheme("CAKE", "Food"), WordWithTheme("COOKIE", "Food"),
            WordWithTheme("CANDY", "Food"), WordWithTheme("ICE", "Food"),
            WordWithTheme("TABLE", "Furniture"), WordWithTheme("CHAIR", "Furniture"), WordWithTheme("DESK", "Furniture"), WordWithTheme("BED", "Furniture"),
            WordWithTheme("DOOR", "Furniture"), WordWithTheme("WINDOW", "Furniture"), WordWithTheme("ROOF", "Furniture"), WordWithTheme("FLOOR", "Furniture"),
            WordWithTheme("WALL", "Furniture"), WordWithTheme("ROOM", "Furniture"),
            WordWithTheme("TEACHER", "School"), WordWithTheme("STUDENT", "School"), WordWithTheme("FRIEND", "School"), WordWithTheme("FAMILY", "School")
        ),
        GradeLevel.GRADE_2 to listOf(
            WordWithTheme("PENCIL", "School Supplies"), WordWithTheme("PAPER", "School Supplies"), WordWithTheme("ERASER", "School Supplies"),
            WordWithTheme("RULER", "School Supplies"), WordWithTheme("SCISSORS", "School Supplies"), WordWithTheme("GLUE", "School Supplies"),
            WordWithTheme("CRAYON", "School Supplies"), WordWithTheme("MARKER", "School Supplies"), WordWithTheme("BACKPACK", "School Supplies"),
            WordWithTheme("NOTEBOOK", "School Supplies"), WordWithTheme("CALCULATOR", "School Supplies"), WordWithTheme("GLOBE", "School Supplies"),
            WordWithTheme("MAP", "School Supplies"), WordWithTheme("CLOCK", "School Supplies"), WordWithTheme("CALENDAR", "School Supplies"),
            WordWithTheme("COMPUTER", "Technology"),
            WordWithTheme("BICYCLE", "Transportation"), WordWithTheme("AUTOMOBILE", "Transportation"), WordWithTheme("AIRPLANE", "Transportation"),
            WordWithTheme("TRAIN", "Transportation"), WordWithTheme("BOAT", "Transportation"), WordWithTheme("SHIP", "Transportation"),
            WordWithTheme("HELICOPTER", "Transportation"), WordWithTheme("ROCKET", "Transportation"),
            WordWithTheme("ELEPHANT", "Animals"), WordWithTheme("GIRAFFE", "Animals"), WordWithTheme("MONKEY", "Animals"), WordWithTheme("TIGER", "Animals"),
            WordWithTheme("PANDA", "Animals"), WordWithTheme("KANGAROO", "Animals"), WordWithTheme("DOLPHIN", "Animals"), WordWithTheme("WHALE", "Animals"),
            WordWithTheme("BUTTERFLY", "Animals"), WordWithTheme("DRAGONFLY", "Animals"), WordWithTheme("LADYBUG", "Animals"), WordWithTheme("SPIDER", "Animals"),
            WordWithTheme("SNAKE", "Animals"), WordWithTheme("TURTLE", "Animals"), WordWithTheme("FROG", "Animals"), WordWithTheme("LIZARD", "Animals")
        ),
        GradeLevel.GRADE_3 to listOf(
            WordWithTheme("ADVENTURE", "Activities"), WordWithTheme("DISCOVERY", "Activities"), WordWithTheme("EXPLORATION", "Activities"),
            WordWithTheme("LIBRARY", "Places"), WordWithTheme("MUSEUM", "Places"), WordWithTheme("THEATER", "Places"), WordWithTheme("HOSPITAL", "Places"),
            WordWithTheme("GARDEN", "Rooms"), WordWithTheme("KITCHEN", "Rooms"), WordWithTheme("BATHROOM", "Rooms"), WordWithTheme("BEDROOM", "Rooms"),
            WordWithTheme("GARAGE", "Rooms"), WordWithTheme("ATTIC", "Rooms"), WordWithTheme("BASEMENT", "Rooms"), WordWithTheme("YARD", "Rooms"),
            WordWithTheme("TEACHER", "Professions"), WordWithTheme("STUDENT", "Professions"), WordWithTheme("DOCTOR", "Professions"), WordWithTheme("NURSE", "Professions"),
            WordWithTheme("COOK", "Professions"), WordWithTheme("ARTIST", "Professions"), WordWithTheme("MUSICIAN", "Professions"), WordWithTheme("ATHLETE", "Professions"),
            WordWithTheme("SOCCER", "Sports"), WordWithTheme("BASKETBALL", "Sports"), WordWithTheme("BASEBALL", "Sports"), WordWithTheme("FOOTBALL", "Sports"),
            WordWithTheme("TENNIS", "Sports"), WordWithTheme("SWIMMING", "Sports"), WordWithTheme("RUNNING", "Sports"), WordWithTheme("JUMPING", "Sports"),
            WordWithTheme("SANDWICH", "Food"), WordWithTheme("COOKIE", "Food"), WordWithTheme("CANDY", "Food"), WordWithTheme("FRUIT", "Food"),
            WordWithTheme("VEGETABLE", "Food"), WordWithTheme("CEREAL", "Food"), WordWithTheme("JUICE", "Food"), WordWithTheme("SOUP", "Food")
        ),
        GradeLevel.GRADE_4 to listOf(
            WordWithTheme("MOUNTAIN", "Geography"), WordWithTheme("FOREST", "Geography"), WordWithTheme("RIVER", "Geography"), WordWithTheme("OCEAN", "Geography"),
            WordWithTheme("WATERFALL", "Geography"), WordWithTheme("VOLCANO", "Geography"), WordWithTheme("DESERT", "Geography"), WordWithTheme("ISLAND", "Geography"),
            WordWithTheme("VALLEY", "Geography"), WordWithTheme("CAVE", "Geography"), WordWithTheme("LAKE", "Geography"), WordWithTheme("STREAM", "Geography"),
            WordWithTheme("MEADOW", "Geography"), WordWithTheme("JUNGLE", "Geography"), WordWithTheme("GLACIER", "Geography"), WordWithTheme("CANYON", "Geography"),
            WordWithTheme("PLANET", "Space"), WordWithTheme("SPACE", "Space"), WordWithTheme("ROCKET", "Space"), WordWithTheme("ASTRONAUT", "Space"),
            WordWithTheme("COMET", "Space"), WordWithTheme("GALAXY", "Space"), WordWithTheme("TELESCOPE", "Space"), WordWithTheme("SATELLITE", "Space"),
            WordWithTheme("COMPUTER", "Technology"), WordWithTheme("TABLET", "Technology"), WordWithTheme("PHONE", "Technology"), WordWithTheme("CAMERA", "Technology"),
            WordWithTheme("ROBOT", "Technology"), WordWithTheme("INTERNET", "Technology"), WordWithTheme("EMAIL", "Technology"), WordWithTheme("VIDEO", "Technology"),
            WordWithTheme("LIBRARY", "Places"), WordWithTheme("SCHOOL", "Places"), WordWithTheme("GARDEN", "Places"), WordWithTheme("PARK", "Places"),
            WordWithTheme("BEACH", "Places"), WordWithTheme("KITCHEN", "Places"), WordWithTheme("BEDROOM", "Places"), WordWithTheme("BATHROOM", "Places")
        ),
        GradeLevel.GRADE_5 to listOf(
            WordWithTheme("KNOWLEDGE", "Education"), WordWithTheme("EDUCATION", "Education"), WordWithTheme("LEARNING", "Education"), WordWithTheme("STUDYING", "Education"),
            WordWithTheme("RESEARCH", "Education"), WordWithTheme("SCHOLARSHIP", "Education"), WordWithTheme("ACADEMIC", "Education"), WordWithTheme("UNIVERSITY", "Education"),
            WordWithTheme("COLLEGE", "Education"), WordWithTheme("GRADUATE", "Education"), WordWithTheme("TEACHER", "Education"), WordWithTheme("STUDENT", "Education"),
            WordWithTheme("FRIEND", "People"), WordWithTheme("FAMILY", "People"), WordWithTheme("DOCTOR", "People"), WordWithTheme("NURSE", "People"),
            WordWithTheme("COOK", "People"), WordWithTheme("ARTIST", "People"), WordWithTheme("MUSICIAN", "People"), WordWithTheme("ATHLETE", "People"),
            WordWithTheme("HERO", "People"), WordWithTheme("LEADER", "People"), WordWithTheme("CHAMPION", "People"),
            WordWithTheme("VICTORY", "Achievements"), WordWithTheme("SUCCESS", "Achievements"), WordWithTheme("ACHIEVEMENT", "Achievements"),
            WordWithTheme("ACCOMPLISHMENT", "Achievements"), WordWithTheme("TRIUMPH", "Achievements"), WordWithTheme("EXCELLENCE", "Achievements"),
            WordWithTheme("MASTERY", "Achievements"),
            WordWithTheme("OPPORTUNITY", "Concepts"), WordWithTheme("POSSIBILITY", "Concepts"), WordWithTheme("POTENTIAL", "Concepts"),
            WordWithTheme("FREEDOM", "Concepts"), WordWithTheme("JUSTICE", "Concepts"), WordWithTheme("EQUALITY", "Concepts"), WordWithTheme("HARMONY", "Concepts")
        ),
        GradeLevel.GRADE_6 to listOf(
            WordWithTheme("IMAGINATION", "Creativity"), WordWithTheme("CREATIVITY", "Creativity"), WordWithTheme("INSPIRATION", "Creativity"),
            WordWithTheme("INNOVATION", "Creativity"), WordWithTheme("ORIGINALITY", "Creativity"), WordWithTheme("ARTISTIC", "Creativity"),
            WordWithTheme("CURIOSITY", "Personality"), WordWithTheme("CONFIDENCE", "Personality"), WordWithTheme("COURAGE", "Personality"),
            WordWithTheme("DETERMINATION", "Personality"), WordWithTheme("PERSISTENCE", "Personality"), WordWithTheme("OPTIMISM", "Personality"),
            WordWithTheme("ENTHUSIASM", "Personality"),
            WordWithTheme("WONDERFUL", "Feelings"), WordWithTheme("EXCITEMENT", "Feelings"), WordWithTheme("JOYFUL", "Feelings"),
            WordWithTheme("GRATEFUL", "Feelings"), WordWithTheme("PEACEFUL", "Feelings"), WordWithTheme("CONTENT", "Feelings"),
            WordWithTheme("BEAUTIFUL", "Descriptions"), WordWithTheme("FANTASTIC", "Descriptions"), WordWithTheme("AMAZING", "Descriptions"),
            WordWithTheme("INCREDIBLE", "Descriptions"), WordWithTheme("SPECTACULAR", "Descriptions"), WordWithTheme("MAGNIFICENT", "Descriptions"),
            WordWithTheme("EXTRAORDINARY", "Descriptions"), WordWithTheme("REMARKABLE", "Descriptions"), WordWithTheme("BRILLIANT", "Descriptions"),
            WordWithTheme("EXCEPTIONAL", "Descriptions"),
            WordWithTheme("ASTRONAUT", "Professions"), WordWithTheme("SCIENTIST", "Professions")
        ),
        GradeLevel.GRADE_7 to listOf(
            WordWithTheme("EXPLORER", "Professions"), WordWithTheme("INVENTOR", "Professions"), WordWithTheme("ARTIST", "Professions"),
            WordWithTheme("MUSICIAN", "Professions"), WordWithTheme("ATHLETE", "Professions"), WordWithTheme("ENGINEER", "Professions"),
            WordWithTheme("ARCHITECT", "Professions"), WordWithTheme("CHEF", "Professions"), WordWithTheme("WRITER", "Professions"),
            WordWithTheme("JOURNALIST", "Professions"), WordWithTheme("PHOTOGRAPHER", "Professions"), WordWithTheme("DIRECTOR", "Professions"),
            WordWithTheme("PROFESSOR", "Professions"), WordWithTheme("LAWYER", "Professions"), WordWithTheme("DOCTOR", "Professions"),
            WordWithTheme("NURSE", "Professions"), WordWithTheme("PILOT", "Professions"), WordWithTheme("CAPTAIN", "Professions"),
            WordWithTheme("CHAMPION", "Achievements"), WordWithTheme("VICTORY", "Achievements"), WordWithTheme("SUCCESS", "Achievements"),
            WordWithTheme("ACHIEVEMENT", "Achievements"), WordWithTheme("ACCOMPLISHMENT", "Achievements"), WordWithTheme("TRIUMPH", "Achievements"),
            WordWithTheme("EXCELLENCE", "Achievements"), WordWithTheme("MASTERY", "Achievements"),
            WordWithTheme("OPPORTUNITY", "Concepts"), WordWithTheme("POSSIBILITY", "Concepts"), WordWithTheme("POTENTIAL", "Concepts"),
            WordWithTheme("FREEDOM", "Concepts"), WordWithTheme("JUSTICE", "Concepts"), WordWithTheme("EQUALITY", "Concepts"),
            WordWithTheme("HARMONY", "Concepts"), WordWithTheme("BALANCE", "Concepts"), WordWithTheme("WISDOM", "Concepts")
        ),
        GradeLevel.GRADE_8 to listOf(
            WordWithTheme("UNDERSTANDING", "Science"), WordWithTheme("EXPERIMENT", "Science"), WordWithTheme("HYPOTHESIS", "Science"),
            WordWithTheme("THEORY", "Science"), WordWithTheme("DISCOVERY", "Science"), WordWithTheme("INVENTION", "Science"),
            WordWithTheme("TECHNOLOGY", "Science"), WordWithTheme("CHEMISTRY", "Science"), WordWithTheme("PHYSICS", "Science"),
            WordWithTheme("BIOLOGY", "Science"), WordWithTheme("ASTRONOMY", "Science"),
            WordWithTheme("CONTINENT", "Geography"), WordWithTheme("COUNTRY", "Geography"), WordWithTheme("CITY", "Geography"),
            WordWithTheme("VILLAGE", "Geography"), WordWithTheme("CULTURE", "Geography"), WordWithTheme("LANGUAGE", "Geography"),
            WordWithTheme("TRADITION", "History"), WordWithTheme("ANCIENT", "History"), WordWithTheme("CIVILIZATION", "History"),
            WordWithTheme("EMPIRE", "History"), WordWithTheme("KINGDOM", "History"), WordWithTheme("REVOLUTION", "History"),
            WordWithTheme("INDEPENDENCE", "History"),
            WordWithTheme("STORY", "Literature"), WordWithTheme("NOVEL", "Literature"), WordWithTheme("POETRY", "Literature"),
            WordWithTheme("CHARACTER", "Literature"), WordWithTheme("PLOT", "Literature"), WordWithTheme("AUTHOR", "Literature"),
            WordWithTheme("LIBRARY", "Literature"),
            WordWithTheme("MELODY", "Music"), WordWithTheme("RHYTHM", "Music"), WordWithTheme("HARMONY", "Music"), WordWithTheme("SYMPHONY", "Music")
        ),
        GradeLevel.GRADE_9 to listOf(
            WordWithTheme("ORCHESTRA", "Music"), WordWithTheme("CONCERT", "Music"), WordWithTheme("PERFORMANCE", "Music"),
            WordWithTheme("ECOSYSTEM", "Nature"), WordWithTheme("ENVIRONMENT", "Nature"), WordWithTheme("CONSERVATION", "Nature"),
            WordWithTheme("WILDLIFE", "Nature"), WordWithTheme("HABITAT", "Nature"), WordWithTheme("BIODIVERSITY", "Nature"),
            WordWithTheme("KNOWLEDGE", "Education"), WordWithTheme("EDUCATION", "Education"), WordWithTheme("LEARNING", "Education"),
            WordWithTheme("STUDYING", "Education"), WordWithTheme("RESEARCH", "Education"), WordWithTheme("SCHOLARSHIP", "Education"),
            WordWithTheme("ACADEMIC", "Education"), WordWithTheme("UNIVERSITY", "Education"), WordWithTheme("COLLEGE", "Education"),
            WordWithTheme("GRADUATE", "Education"),
            WordWithTheme("IMAGINATION", "Creativity"), WordWithTheme("CREATIVITY", "Creativity"), WordWithTheme("INSPIRATION", "Creativity"),
            WordWithTheme("INNOVATION", "Creativity"), WordWithTheme("ORIGINALITY", "Creativity"), WordWithTheme("ARTISTIC", "Creativity"),
            WordWithTheme("CURIOSITY", "Personality"), WordWithTheme("CONFIDENCE", "Personality"), WordWithTheme("COURAGE", "Personality"),
            WordWithTheme("DETERMINATION", "Personality"), WordWithTheme("PERSISTENCE", "Personality"), WordWithTheme("OPTIMISM", "Personality"),
            WordWithTheme("ENTHUSIASM", "Personality")
        ),
        GradeLevel.GRADE_10 to listOf(
            WordWithTheme("WONDERFUL", "Feelings"), WordWithTheme("EXCITEMENT", "Feelings"), WordWithTheme("JOYFUL", "Feelings"),
            WordWithTheme("GRATEFUL", "Feelings"), WordWithTheme("PEACEFUL", "Feelings"), WordWithTheme("CONTENT", "Feelings"),
            WordWithTheme("BEAUTIFUL", "Descriptions"), WordWithTheme("FANTASTIC", "Descriptions"), WordWithTheme("AMAZING", "Descriptions"),
            WordWithTheme("INCREDIBLE", "Descriptions"), WordWithTheme("SPECTACULAR", "Descriptions"), WordWithTheme("MAGNIFICENT", "Descriptions"),
            WordWithTheme("EXTRAORDINARY", "Descriptions"), WordWithTheme("REMARKABLE", "Descriptions"), WordWithTheme("BRILLIANT", "Descriptions"),
            WordWithTheme("EXCEPTIONAL", "Descriptions"),
            WordWithTheme("ASTRONAUT", "Professions"), WordWithTheme("SCIENTIST", "Professions"), WordWithTheme("EXPLORER", "Professions"),
            WordWithTheme("INVENTOR", "Professions"), WordWithTheme("ARTIST", "Professions"), WordWithTheme("MUSICIAN", "Professions"),
            WordWithTheme("ATHLETE", "Professions"), WordWithTheme("ENGINEER", "Professions"), WordWithTheme("ARCHITECT", "Professions"),
            WordWithTheme("CHEF", "Professions"), WordWithTheme("WRITER", "Professions"), WordWithTheme("JOURNALIST", "Professions"),
            WordWithTheme("PHOTOGRAPHER", "Professions"), WordWithTheme("DIRECTOR", "Professions"), WordWithTheme("PROFESSOR", "Professions"),
            WordWithTheme("LAWYER", "Professions"), WordWithTheme("DOCTOR", "Professions")
        ),
        GradeLevel.GRADE_11 to listOf(
            WordWithTheme("NURSE", "Professions"), WordWithTheme("PILOT", "Professions"), WordWithTheme("CAPTAIN", "Professions"),
            WordWithTheme("CHAMPION", "Achievements"), WordWithTheme("VICTORY", "Achievements"), WordWithTheme("SUCCESS", "Achievements"),
            WordWithTheme("ACHIEVEMENT", "Achievements"), WordWithTheme("ACCOMPLISHMENT", "Achievements"), WordWithTheme("TRIUMPH", "Achievements"),
            WordWithTheme("EXCELLENCE", "Achievements"), WordWithTheme("MASTERY", "Achievements"),
            WordWithTheme("OPPORTUNITY", "Concepts"), WordWithTheme("POSSIBILITY", "Concepts"), WordWithTheme("POTENTIAL", "Concepts"),
            WordWithTheme("FREEDOM", "Concepts"), WordWithTheme("JUSTICE", "Concepts"), WordWithTheme("EQUALITY", "Concepts"),
            WordWithTheme("HARMONY", "Concepts"), WordWithTheme("BALANCE", "Concepts"), WordWithTheme("WISDOM", "Concepts"),
            WordWithTheme("UNDERSTANDING", "Science"), WordWithTheme("EXPERIMENT", "Science"), WordWithTheme("HYPOTHESIS", "Science"),
            WordWithTheme("THEORY", "Science"), WordWithTheme("DISCOVERY", "Science"), WordWithTheme("INVENTION", "Science"),
            WordWithTheme("TECHNOLOGY", "Science"), WordWithTheme("CHEMISTRY", "Science"), WordWithTheme("PHYSICS", "Science"),
            WordWithTheme("BIOLOGY", "Science"), WordWithTheme("ASTRONOMY", "Science"),
            WordWithTheme("CONTINENT", "Geography"), WordWithTheme("COUNTRY", "Geography")
        ),
        GradeLevel.GRADE_12 to listOf(
            WordWithTheme("CITY", "Geography"), WordWithTheme("VILLAGE", "Geography"), WordWithTheme("CULTURE", "Geography"),
            WordWithTheme("LANGUAGE", "Geography"), WordWithTheme("TRADITION", "History"), WordWithTheme("ANCIENT", "History"),
            WordWithTheme("CIVILIZATION", "History"), WordWithTheme("EMPIRE", "History"), WordWithTheme("KINGDOM", "History"),
            WordWithTheme("REVOLUTION", "History"), WordWithTheme("INDEPENDENCE", "History"),
            WordWithTheme("STORY", "Literature"), WordWithTheme("NOVEL", "Literature"), WordWithTheme("POETRY", "Literature"),
            WordWithTheme("CHARACTER", "Literature"), WordWithTheme("PLOT", "Literature"), WordWithTheme("AUTHOR", "Literature"),
            WordWithTheme("LIBRARY", "Literature"),
            WordWithTheme("MELODY", "Music"), WordWithTheme("RHYTHM", "Music"), WordWithTheme("HARMONY", "Music"), WordWithTheme("SYMPHONY", "Music"),
            WordWithTheme("ORCHESTRA", "Music"), WordWithTheme("CONCERT", "Music"), WordWithTheme("PERFORMANCE", "Music"),
            WordWithTheme("ECOSYSTEM", "Nature"), WordWithTheme("ENVIRONMENT", "Nature"), WordWithTheme("CONSERVATION", "Nature"),
            WordWithTheme("WILDLIFE", "Nature"), WordWithTheme("HABITAT", "Nature"), WordWithTheme("BIODIVERSITY", "Nature"),
            WordWithTheme("KNOWLEDGE", "Education"), WordWithTheme("EDUCATION", "Education"), WordWithTheme("LEARNING", "Education"),
            WordWithTheme("STUDYING", "Education")
        ),
        GradeLevel.BRAINROT to listOf(
            WordWithTheme("RIZZ", "Internet Slang"), WordWithTheme("SIGMA", "Internet Slang"), WordWithTheme("BASED", "Internet Slang"),
            WordWithTheme("CAP", "Internet Slang"), WordWithTheme("SUS", "Internet Slang"), WordWithTheme("MID", "Internet Slang"),
            WordWithTheme("W", "Internet Slang"), WordWithTheme("L", "Internet Slang"), WordWithTheme("FR", "Internet Slang"),
            WordWithTheme("ONG", "Internet Slang"), WordWithTheme("NGL", "Internet Slang"), WordWithTheme("TBH", "Internet Slang"),
            WordWithTheme("IMO", "Internet Slang"), WordWithTheme("FYI", "Internet Slang"), WordWithTheme("POG", "Internet Slang"),
            WordWithTheme("BUSSIN", "Internet Slang"), WordWithTheme("GOATED", "Internet Slang"), WordWithTheme("FIRE", "Internet Slang"),
            WordWithTheme("LIT", "Internet Slang"), WordWithTheme("SLAPS", "Internet Slang"), WordWithTheme("MOOD", "Internet Slang"),
            WordWithTheme("VIBE", "Internet Slang"), WordWithTheme("DRIP", "Internet Slang"), WordWithTheme("FLEX", "Internet Slang"),
            WordWithTheme("CLOUT", "Internet Slang"), WordWithTheme("NPC", "Internet Slang"), WordWithTheme("GRASS", "Internet Slang"),
            WordWithTheme("CRINGE", "Internet Slang"), WordWithTheme("SHEESH", "Internet Slang"), WordWithTheme("BET", "Internet Slang"),
            WordWithTheme("PERIOD", "Internet Slang"), WordWithTheme("SLAY", "Internet Slang"), WordWithTheme("QUEEN", "Internet Slang"),
            WordWithTheme("KING", "Internet Slang"), WordWithTheme("CHECK", "Internet Slang"), WordWithTheme("FACTS", "Internet Slang"),
            WordWithTheme("RATIO", "Internet Slang"), WordWithTheme("DEADASS", "Internet Slang"), WordWithTheme("FRFR", "Internet Slang"),
            WordWithTheme("LOWKEY", "Internet Slang"), WordWithTheme("HIGHKEY", "Internet Slang"), WordWithTheme("TOUCH", "Internet Slang"),
            WordWithTheme("MAIN", "Internet Slang"), WordWithTheme("SIDE", "Internet Slang"), WordWithTheme("GYATT", "Internet Slang"),
            WordWithTheme("FANUM", "Internet Slang"), WordWithTheme("RIZZLER", "Internet Slang"), WordWithTheme("OHIO", "Internet Slang"),
            WordWithTheme("ALPHA", "Internet Slang"), WordWithTheme("CHAD", "Internet Slang"), WordWithTheme("SKIBIDI", "Internet Slang"),
            WordWithTheme("NOCAP", "Internet Slang"), WordWithTheme("PERIODT", "Internet Slang"), WordWithTheme("VIBECHECK", "Internet Slang"),
            WordWithTheme("TOUCHGRASS", "Internet Slang"), WordWithTheme("MAINCHARACTER", "Internet Slang"), WordWithTheme("SIDECHARACTER", "Internet Slang"),
            WordWithTheme("YEET", "Internet Slang"), WordWithTheme("POGGERS", "Internet Slang"), WordWithTheme("NO", "Internet Slang"),
            WordWithTheme("YES", "Internet Slang")
        ),
        GradeLevel.YEEPS to listOf(
            // Core Game Terms
            WordWithTheme("YEEP", "Yeeps Game"), WordWithTheme("STUFFING", "Yeeps Game"),
            WordWithTheme("SEEKER", "Yeeps Game"), WordWithTheme("HIDER", "Yeeps Game"),
            WordWithTheme("BUTTCOIN", "Yeeps Game"), WordWithTheme("TECHWEB", "Yeeps Game"),
            
            // Building Items
            WordWithTheme("PILLOW", "Yeeps Game"), WordWithTheme("BLOCK", "Yeeps Game"),
            WordWithTheme("STRUCTURE", "Yeeps Game"), WordWithTheme("BUILD", "Yeeps Game"),
            
            // Movement Gadgets
            WordWithTheme("GRAPPLER", "Yeeps Game"), WordWithTheme("GRAPPLING", "Yeeps Game"),
            WordWithTheme("HOOK", "Yeeps Game"), WordWithTheme("PROPELLER", "Yeeps Game"),
            WordWithTheme("FIREWORK", "Yeeps Game"), WordWithTheme("FLIPPER", "Yeeps Game"),
            WordWithTheme("BROOM", "Yeeps Game"), WordWithTheme("WITCH", "Yeeps Game"),
            WordWithTheme("ZIPLINE", "Yeeps Game"), WordWithTheme("ANCHOR", "Yeeps Game"),
            WordWithTheme("SLED", "Yeeps Game"), WordWithTheme("SURFBOARD", "Yeeps Game"),
            WordWithTheme("UMBRELLA", "Yeeps Game"), WordWithTheme("PORTAL", "Yeeps Game"),
            WordWithTheme("BALL", "Yeeps Game"),
            
            // Combat & Annoying Items
            WordWithTheme("GRENADE", "Yeeps Game"), WordWithTheme("SNOWBALL", "Yeeps Game"),
            WordWithTheme("BAT", "Yeeps Game"), WordWithTheme("BOMB", "Yeeps Game"),
            WordWithTheme("EXPLOSIVE", "Yeeps Game"), WordWithTheme("RADIATION", "Yeeps Game"),
            WordWithTheme("PIN", "Yeeps Game"), WordWithTheme("THROW", "Yeeps Game"),
            
            // Utility & Creative Items
            WordWithTheme("DECOY", "Yeeps Game"), WordWithTheme("BODY", "Yeeps Game"),
            WordWithTheme("GADGET", "Yeeps Game"), WordWithTheme("ITEM", "Yeeps Game"),
            WordWithTheme("BUTTJO", "Yeeps Game"), WordWithTheme("PAINTBRUSH", "Yeeps Game"),
            WordWithTheme("WIRING", "Yeeps Game"), WordWithTheme("WIRE", "Yeeps Game"),
            WordWithTheme("SHORT", "Yeeps Game"), WordWithTheme("LONG", "Yeeps Game"),
            WordWithTheme("BUTTON", "Yeeps Game"), WordWithTheme("SWITCH", "Yeeps Game"),
            
            // Potion Base Ingredients
            WordWithTheme("BREWSHROOM", "Yeeps Game"), WordWithTheme("MAGIC", "Yeeps Game"),
            WordWithTheme("TALL", "Yeeps Game"),
            
            // Potion Effect Ingredients
            WordWithTheme("GOODIE", "Yeeps Game"), WordWithTheme("FLOWER", "Yeeps Game"),
            WordWithTheme("CORRUPTED", "Yeeps Game"), WordWithTheme("SPEED", "Yeeps Game"),
            WordWithTheme("BERRIES", "Yeeps Game"), WordWithTheme("BUFF", "Yeeps Game"),
            WordWithTheme("TENTACLE", "Yeeps Game"), WordWithTheme("HEALTHY", "Yeeps Game"),
            WordWithTheme("LEAF", "Yeeps Game"), WordWithTheme("FLOATY", "Yeeps Game"),
            WordWithTheme("FEATHER", "Yeeps Game"), WordWithTheme("BIGGIE", "Yeeps Game"),
            WordWithTheme("BONE", "Yeeps Game"), WordWithTheme("SPOOKY", "Yeeps Game"),
            WordWithTheme("EYEBALL", "Yeeps Game"), WordWithTheme("CHAMELEON", "Yeeps Game"),
            WordWithTheme("TAIL", "Yeeps Game"),
            
            // Potion Types
            WordWithTheme("GIANT", "Yeeps Game"), WordWithTheme("TINY", "Yeeps Game"),
            WordWithTheme("HEALING", "Yeeps Game"), WordWithTheme("POISON", "Yeeps Game"),
            WordWithTheme("INVISIBILITY", "Yeeps Game"), WordWithTheme("VISIBILITY", "Yeeps Game"),
            WordWithTheme("NIGHT", "Yeeps Game"), WordWithTheme("VISION", "Yeeps Game"),
            WordWithTheme("BLINDNESS", "Yeeps Game"), WordWithTheme("LEVITATION", "Yeeps Game"),
            WordWithTheme("SLOW", "Yeeps Game"), WordWithTheme("FALLING", "Yeeps Game"),
            WordWithTheme("SLOWNESS", "Yeeps Game"), WordWithTheme("STRENGTH", "Yeeps Game"),
            WordWithTheme("WEAKNESS", "Yeeps Game"), WordWithTheme("HASTE", "Yeeps Game"),
            WordWithTheme("HOLLOW", "Yeeps Game"), WordWithTheme("LOVE", "Yeeps Game"),
            WordWithTheme("NEUTRALIZER", "Yeeps Game"), WordWithTheme("FAILED", "Yeeps Game"),
            WordWithTheme("POTION", "Yeeps Game"),
            
            // Brewing Terms
            WordWithTheme("BREWING", "Yeeps Game"), WordWithTheme("CAULDRON", "Yeeps Game"),
            WordWithTheme("BOTTLE", "Yeeps Game"), WordWithTheme("DRINK", "Yeeps Game"),
            WordWithTheme("INGREDIENT", "Yeeps Game"), WordWithTheme("RECIPE", "Yeeps Game"),
            WordWithTheme("COMBINE", "Yeeps Game"), WordWithTheme("EFFECT", "Yeeps Game"),
            WordWithTheme("DURATION", "Yeeps Game"),
            
            // Game Modes
            WordWithTheme("TAG", "Yeeps Game"), WordWithTheme("HUNT", "Yeeps Game"),
            WordWithTheme("PROP", "Yeeps Game"), WordWithTheme("HANGOUT", "Yeeps Game"),
            WordWithTheme("MONSTER", "Yeeps Game"), WordWithTheme("GOBLIN", "Yeeps Game"),
            WordWithTheme("SURVIVAL", "Yeeps Game"), WordWithTheme("BATTLE", "Yeeps Game"),
            WordWithTheme("HIDE", "Yeeps Game"), WordWithTheme("SEEK", "Yeeps Game"),
            
            // Game World & Customization
            WordWithTheme("WORLD", "Yeeps Game"), WordWithTheme("MAP", "Yeeps Game"),
            WordWithTheme("SHOP", "Yeeps Game"), WordWithTheme("COSMETIC", "Yeeps Game"),
            WordWithTheme("CRAFT", "Yeeps Game"), WordWithTheme("RESEARCH", "Yeeps Game"),
            WordWithTheme("TECH", "Yeeps Game"), WordWithTheme("WEB", "Yeeps Game"),
            WordWithTheme("CREATIVE", "Yeeps Game"), WordWithTheme("PRIVATE", "Yeeps Game"),
            WordWithTheme("COMMUNITY", "Yeeps Game"), WordWithTheme("CUSTOM", "Yeeps Game"),
            WordWithTheme("SIGN", "Yeeps Game"), WordWithTheme("NOPIN", "Yeeps Game"),
            WordWithTheme("DECORATION", "Yeeps Game"), WordWithTheme("PLUSH", "Yeeps Game")
        )
    )

    init {
        startNewGame()
    }

    fun startNewGame() {
        val gradeLevel = _uiState.value.gradeLevel
        val wordList = wordLists[gradeLevel] ?: wordLists[GradeLevel.GRADE_3]!!
        
        // Select 10 random words from the grade's word list
        val selectedWordsWithTheme = wordList.shuffled().take(10)
        val selectedWords = selectedWordsWithTheme.map { it.word }
        
        // Scramble the first word and create letter tiles
        val firstWordWithTheme = selectedWordsWithTheme[0]
        val firstWord = firstWordWithTheme.word
        val scrambled = scrambleWord(firstWord)
        val letterTiles = createLetterTiles(scrambled)
        
        _uiState.value = _uiState.value.copy(
            words = selectedWords,
            currentWordIndex = 0,
            currentWord = firstWord,
            theme = firstWordWithTheme.theme,
            letterTiles = letterTiles,
            score = 0,
            correctAnswers = 0,
            showResult = false,
            isCorrect = false,
            showCelebration = false,
            isGameComplete = false,
            startTime = System.currentTimeMillis(),
            currentTime = System.currentTimeMillis(),
            streakCount = 0,
            isDragging = false,
            draggedTileId = null
        )
    }

    private fun createLetterTiles(scrambledWord: String): List<LetterTile> {
        return scrambledWord.mapIndexed { index, char ->
            LetterTile(
                id = index,
                letter = char,
                position = index
            )
        }
    }

    private fun scrambleWord(word: String): String {
        val upperWord = word.uppercase()
        var scrambled = upperWord.toList().shuffled().joinToString("")
        
        // Ensure scrambled is different from original (max 10 attempts)
        var attempts = 0
        while (scrambled == upperWord && attempts < 10) {
            scrambled = upperWord.toList().shuffled().joinToString("")
            attempts++
        }
        
        // If still same, use swap strategy
        if (scrambled == upperWord && upperWord.length > 1) {
            val chars = upperWord.toMutableList()
            // Swap first and last, then shuffle middle
            val first = chars[0]
            val last = chars[chars.size - 1]
            chars[0] = last
            chars[chars.size - 1] = first
            if (chars.size > 2) {
                val middle = chars.subList(1, chars.size - 1)
                middle.shuffle()
            }
            scrambled = chars.joinToString("")
        }
        
        return scrambled
    }

    private fun checkIfCorrect(letterTiles: List<LetterTile>, currentWord: String): Boolean {
        val userAnswer = letterTiles
            .sortedBy { it.position }
            .joinToString("") { it.letter.toString() }
        return userAnswer.uppercase() == currentWord.uppercase()
    }

    fun swapLetterPositions(fromPosition: Int, toPosition: Int) {
        val currentState = _uiState.value
        if (currentState.showResult || currentState.isGameComplete || fromPosition == toPosition) {
            return
        }

        // Check if either position contains a locked tile
        val tileAtFrom = currentState.letterTiles.find { it.position == fromPosition }
        val tileAtTo = currentState.letterTiles.find { it.position == toPosition }
        
        if (tileAtFrom?.isLocked == true || tileAtTo?.isLocked == true) {
            // Prevent swap if either tile is locked
            return
        }

        val updatedTiles = currentState.letterTiles.map { tile ->
            when {
                tile.position == fromPosition -> tile.copy(position = toPosition)
                tile.position == toPosition -> tile.copy(position = fromPosition)
                else -> tile
            }
        }

        val newState = currentState.copy(
            letterTiles = updatedTiles,
            currentTime = System.currentTimeMillis()
        )
        
        _uiState.value = newState
        
        // Auto-detect correct answer after swap (only if not already showing result)
        // Note: isDragging check not needed here since swap happens after drag ends
        if (!newState.showResult && checkIfCorrect(updatedTiles, newState.currentWord)) {
            processCorrectAnswer(newState)
        }
    }

    fun startDrag(tileId: Int) {
        val currentState = _uiState.value
        // Check if the tile is locked
        val tile = currentState.letterTiles.find { it.id == tileId }
        if (tile?.isLocked == true) {
            // Prevent dragging locked tiles
            return
        }
        _uiState.value = currentState.copy(
            isDragging = true,
            draggedTileId = tileId
        )
    }

    fun endDrag() {
        _uiState.value = _uiState.value.copy(
            isDragging = false,
            draggedTileId = null
        )
    }

    fun useHint() {
        val currentState = _uiState.value
        if (currentState.showResult || currentState.isGameComplete) {
            return
        }
        
        val correctWord = currentState.currentWord.uppercase()
        
        // Find all positions where letters are incorrect
        val wrongPositions = mutableListOf<Pair<Int, Int>>() // (tilePosition, correctPosition)
        
        for (correctPos in correctWord.indices) {
            val correctLetter = correctWord[correctPos]
            // Find the tile currently at this position
            val tileAtPosition = currentState.letterTiles.find { it.position == correctPos }
            
            if (tileAtPosition?.letter != correctLetter) {
                // This position has wrong letter, find the tile with the correct letter
                val correctTile = currentState.letterTiles.find { 
                    it.letter == correctLetter && it.position != correctPos
                }
                if (correctTile != null) {
                    wrongPositions.add(Pair(correctTile.position, correctPos))
                }
            }
        }
        
        if (wrongPositions.isNotEmpty()) {
            // Randomly select one wrong position to fix
            val (wrongTilePos, correctPos) = wrongPositions.random()
            // Perform the swap and lock the tile at correct position
            val updatedTiles = currentState.letterTiles.map { tile ->
                when {
                    tile.position == wrongTilePos -> tile.copy(position = correctPos, isLocked = true)
                    tile.position == correctPos -> tile.copy(position = wrongTilePos)
                    else -> tile
                }
            }
            
            val newState = currentState.copy(
                letterTiles = updatedTiles,
                currentTime = System.currentTimeMillis()
            )
            
            _uiState.value = newState
            
            // Auto-detect correct answer after swap (only if not already showing result)
            if (!newState.showResult && checkIfCorrect(updatedTiles, newState.currentWord)) {
                processCorrectAnswer(newState)
            }
        }
    }

    private fun processCorrectAnswer(currentState: WordScrambleUiState) {
        // Calculate score
        val timeElapsed = (currentState.currentTime - currentState.startTime) / 1000 // seconds
        val basePoints = currentState.currentWord.length * 10
        val timePenalty = minOf(timeElapsed.toInt(), 30) // Max 30 second penalty
        val streakBonus = minOf(currentState.streakCount * 10, 50) // Max 50 bonus
        val pointsEarned = maxOf(0, basePoints - timePenalty) + streakBonus

        val newScore = currentState.score + pointsEarned
        val newCorrectAnswers = currentState.correctAnswers + 1
        val newStreakCount = currentState.streakCount + 1

        // Play sound
        if (currentState.soundEnabled) {
            soundManager.playSound(SoundManager.SoundType.WIN)
        }

        _uiState.value = currentState.copy(
            showResult = true,
            isCorrect = true,
            score = newScore,
            correctAnswers = newCorrectAnswers,
            streakCount = newStreakCount,
            showCelebration = true
        )
    }

    fun submitAnswer() {
        val currentState = _uiState.value
        if (currentState.letterTiles.isEmpty() || currentState.showResult || currentState.isGameComplete) {
            return
        }

        val isCorrect = checkIfCorrect(currentState.letterTiles, currentState.currentWord)

        if (isCorrect) {
            processCorrectAnswer(currentState)
        } else {
            // Handle incorrect answer (manual submission only)
            // Calculate score (0 points for incorrect)
            val newStreakCount = 0

            // Play sound
            if (currentState.soundEnabled) {
                soundManager.playSound(SoundManager.SoundType.LOSE)
            }

            _uiState.value = currentState.copy(
                showResult = true,
                isCorrect = false,
                streakCount = newStreakCount,
                showCelebration = false
            )
        }
    }

    fun nextWord() {
        val currentState = _uiState.value
        val nextIndex = currentState.currentWordIndex + 1

        if (nextIndex < currentState.words.size) {
            val nextWord = currentState.words[nextIndex]
            // Find theme for the next word
            val gradeLevel = currentState.gradeLevel
            val wordList = wordLists[gradeLevel] ?: wordLists[GradeLevel.GRADE_3]!!
            val wordWithTheme = wordList.find { it.word == nextWord } ?: WordWithTheme(nextWord, "")
            val scrambled = scrambleWord(nextWord)
            val letterTiles = createLetterTiles(scrambled)
            
            _uiState.value = currentState.copy(
                currentWordIndex = nextIndex,
                currentWord = nextWord,
                theme = wordWithTheme.theme,
                letterTiles = letterTiles,
                showResult = false,
                isCorrect = false,
                showCelebration = false,
                startTime = System.currentTimeMillis(),
                currentTime = System.currentTimeMillis(),
                isDragging = false,
                draggedTileId = null
            )
        } else {
            // Game complete
            val finalScore = currentState.score
            val newHighScore = if (finalScore > currentState.highScore) {
                viewModelScope.launch {
                    gamePreferencesManager.saveWordScrambleHighScore(finalScore)
                }
                finalScore
            } else {
                currentState.highScore
            }

            _uiState.value = currentState.copy(
                isGameComplete = true,
                showResult = false,
                showCelebration = finalScore > currentState.highScore,
                highScore = newHighScore
            )
        }
    }

    fun onCelebrationComplete() {
        _uiState.value = _uiState.value.copy(showCelebration = false)
    }

    fun setGradeLevel(grade: GradeLevel) {
        gamePreferencesManager.saveWordScrambleGradeLevel(grade)
        _uiState.value = _uiState.value.copy(gradeLevel = grade)
        startNewGame()
    }

    fun setSoundEnabled(enabled: Boolean) {
        gamePreferencesManager.setSoundEnabled(enabled)
        _uiState.value = _uiState.value.copy(soundEnabled = enabled)
    }
}
