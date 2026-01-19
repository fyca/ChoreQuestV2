package com.chorequest.presentation.games

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.chorequest.data.local.GamePreferencesManager
import com.chorequest.utils.SoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*
import javax.inject.Inject

data class ColorFunUiState(
    val selectedColor: Color = Color(0xFFE74C3C), // Red
    val currentTemplateIndex: Int = 0,
    val pixelGrids: Map<Int, Array<Array<Color?>>> = emptyMap(), // templateIndex -> grid[row][col]
    val availableTemplates: List<PixelTemplate> = emptyList(),
    val gridSize: Int = 20, // 20x20 grid
    val soundEnabled: Boolean = true
)

data class PixelTemplate(
    val id: Int,
    val title: String,
    val emoji: String,
    val outline: Array<Array<Boolean>> // true = has outline/pixel should be colored, false = empty
)

@HiltViewModel
class ColorFunViewModel @Inject constructor(
    private val gamePreferencesManager: GamePreferencesManager,
    private val soundManager: SoundManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ColorFunUiState(
            availableTemplates = generatePixelTemplates(),
            soundEnabled = gamePreferencesManager.isSoundEnabled(),
            gridSize = 20
        )
    )
    val uiState: StateFlow<ColorFunUiState> = _uiState.asStateFlow()

    init {
        // Initialize grids for all templates
        initializeGrids()
    }

    private fun initializeGrids() {
        val templates = _uiState.value.availableTemplates
        val gridSize = _uiState.value.gridSize
        val initializedGrids = templates.mapIndexed { index, _ ->
            index to Array(gridSize) { Array<Color?>(gridSize) { null } }
        }.toMap()
        
        _uiState.value = _uiState.value.copy(pixelGrids = initializedGrids)
    }

    fun selectColor(color: Color) {
        _uiState.value = _uiState.value.copy(selectedColor = color)
        if (_uiState.value.soundEnabled) {
            soundManager.playSound(SoundManager.SoundType.CLICK)
        }
    }

    fun colorPixel(row: Int, col: Int) {
        val currentState = _uiState.value
        val templateIndex = currentState.currentTemplateIndex
        val gridSize = currentState.gridSize
        
        // Validate coordinates
        if (row < 0 || row >= gridSize || col < 0 || col >= gridSize) {
            return
        }
        
        // Get current grid or create new one
        val currentGrid = currentState.pixelGrids[templateIndex]?.let { grid ->
            grid.map { it.copyOf() }.toTypedArray()
        } ?: Array(gridSize) { Array<Color?>(gridSize) { null } }
        
        // Color the pixel
        currentGrid[row][col] = currentState.selectedColor
        
        // Update state
        val updatedGrids = currentState.pixelGrids.toMutableMap()
        updatedGrids[templateIndex] = currentGrid
        
        _uiState.value = currentState.copy(pixelGrids = updatedGrids)
        
        if (_uiState.value.soundEnabled) {
            soundManager.playSound(SoundManager.SoundType.CLICK)
        }
    }

    fun clearTemplate() {
        val currentState = _uiState.value
        val templateIndex = currentState.currentTemplateIndex
        val gridSize = currentState.gridSize
        
        // Create empty grid
        val emptyGrid = Array(gridSize) { Array<Color?>(gridSize) { null } }
        
        val updatedGrids = currentState.pixelGrids.toMutableMap()
        updatedGrids[templateIndex] = emptyGrid
        
        _uiState.value = currentState.copy(pixelGrids = updatedGrids)
        
        if (_uiState.value.soundEnabled) {
            soundManager.playSound(SoundManager.SoundType.CLICK)
        }
    }

    fun selectTemplate(templateIndex: Int) {
        if (templateIndex >= 0 && templateIndex < _uiState.value.availableTemplates.size) {
            val currentState = _uiState.value
            val gridSize = currentState.gridSize
            
            // Initialize grid if it doesn't exist
            val updatedGrids = currentState.pixelGrids.toMutableMap()
            if (!updatedGrids.containsKey(templateIndex)) {
                updatedGrids[templateIndex] = Array(gridSize) { Array<Color?>(gridSize) { null } }
            }
            
            _uiState.value = currentState.copy(
                currentTemplateIndex = templateIndex,
                pixelGrids = updatedGrids
            )
            
            if (_uiState.value.soundEnabled) {
                soundManager.playSound(SoundManager.SoundType.CLICK)
            }
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        gamePreferencesManager.setSoundEnabled(enabled)
        _uiState.value = _uiState.value.copy(soundEnabled = enabled)
    }

    private fun generatePixelTemplates(): List<PixelTemplate> {
        val gridSize = 20
        
        return listOf(
            // Empty template (blank canvas)
            PixelTemplate(
                id = 0,
                title = "Blank Canvas",
                emoji = "🎨",
                outline = Array(gridSize) { Array(gridSize) { true } } // All pixels can be colored
            ),
            // House with detailed pixel art
            PixelTemplate(
                id = 1,
                title = "House",
                emoji = "🏠",
                outline = createHousePixelArt(gridSize)
            ),
            // Flower with detailed pixel art
            PixelTemplate(
                id = 2,
                title = "Flower",
                emoji = "🌸",
                outline = createFlowerPixelArt(gridSize)
            ),
            // Star with detailed pixel art
            PixelTemplate(
                id = 3,
                title = "Star",
                emoji = "⭐",
                outline = createStarPixelArt(gridSize)
            ),
            // Heart with detailed pixel art
            PixelTemplate(
                id = 4,
                title = "Heart",
                emoji = "❤️",
                outline = createHeartPixelArt(gridSize)
            ),
            // Butterfly with detailed pixel art
            PixelTemplate(
                id = 5,
                title = "Butterfly",
                emoji = "🦋",
                outline = createButterflyPixelArt(gridSize)
            ),
            // Cat face
            PixelTemplate(
                id = 6,
                title = "Cat",
                emoji = "🐱",
                outline = createCatPixelArt(gridSize)
            ),
            // Car
            PixelTemplate(
                id = 7,
                title = "Car",
                emoji = "🚗",
                outline = createCarPixelArt(gridSize)
            )
        )
    }

    private fun createHousePixelArt(size: Int): Array<Array<Boolean>> {
        // Detailed house with roof, walls, door, windows
        val pattern = """
            ..............##........
            ............#######......
            ..........#########.....
            ........#############...
            ......###############..
            ....#################..
            ...###################.
            ..#####################
            .#######################
            #######################
            #######################
            #######################
            #######################
            #######################
            #######################
            #######################
            #######################
            #######################
            #######################
            #######################
        """.trimIndent()
        
        return createDetailedHouse(size)
    }
    
    private fun createDetailedHouse(size: Int): Array<Array<Boolean>> {
        val grid = Array(size) { Array(size) { false } }
        val center = size / 2
        
        // Roof (triangle)
        for (row in 0 until 7) {
            val width = row * 2 + 1
            val start = center - row
            for (col in start until start + width) {
                if (col in 0 until size && row in 0 until size) {
                    grid[row][col] = true
                }
            }
        }
        
        // Walls (rectangle)
        for (row in 7 until size - 2) {
            for (col in center - 6 until center + 7) {
                if (col in 0 until size && row in 0 until size) {
                    grid[row][col] = true
                }
            }
        }
        
        // Left window
        for (row in 8 until 12) {
            for (col in center - 5 until center - 2) {
                if (col in 0 until size && row in 0 until size) {
                    grid[row][col] = true
                }
            }
        }
        
        // Right window
        for (row in 8 until 12) {
            for (col in center + 2 until center + 5) {
                if (col in 0 until size && row in 0 until size) {
                    grid[row][col] = true
                }
            }
        }
        
        // Door
        for (row in size - 7 until size - 2) {
            for (col in center - 2 until center + 3) {
                if (col in 0 until size && row in 0 until size) {
                    grid[row][col] = true
                }
            }
        }
        
        return grid
    }

    private fun createFlowerPixelArt(size: Int): Array<Array<Boolean>> {
        val grid = Array(size) { Array(size) { false } }
        val center = size / 2
        
        // Center circle
        for (row in center - 2 until center + 3) {
            for (col in center - 2 until center + 3) {
                if (row in 0 until size && col in 0 until size) {
                    val dist = (row - center) * (row - center) + (col - center) * (col - center)
                    if (dist <= 4) {
                        grid[row][col] = true
                    }
                }
            }
        }
        
        // Top petal
        for (row in 0 until 5) {
            for (col in center - 3 until center + 4) {
                if (row in 0 until size && col in 0 until size) {
                    grid[row][col] = true
                }
            }
        }
        
        // Bottom petal
        for (row in size - 5 until size) {
            for (col in center - 3 until center + 4) {
                if (row in 0 until size && col in 0 until size) {
                    grid[row][col] = true
                }
            }
        }
        
        // Left petal
        for (row in center - 3 until center + 4) {
            for (col in 0 until 5) {
                if (row in 0 until size && col in 0 until size) {
                    grid[row][col] = true
                }
            }
        }
        
        // Right petal
        for (row in center - 3 until center + 4) {
            for (col in size - 5 until size) {
                if (row in 0 until size && col in 0 until size) {
                    grid[row][col] = true
                }
            }
        }
        
        // Diagonal petals
        for (row in 1 until 6) {
            for (col in 1 until 6) {
                if (row + col <= 6) {
                    grid[row][col] = true
                    grid[row][size - 1 - col] = true
                    grid[size - 1 - row][col] = true
                    grid[size - 1 - row][size - 1 - col] = true
                }
            }
        }
        
        // Stem
        for (row in size - 4 until size) {
            if (row in 0 until size && center in 0 until size) {
                grid[row][center] = true
            }
        }
        
        return grid
    }

    private fun createStarPixelArt(size: Int): Array<Array<Boolean>> {
        val grid = Array(size) { Array(size) { false } }
        val center = size / 2
        
        // Create a 5-pointed star using a simple algorithm
        for (row in 0 until size) {
            for (col in 0 until size) {
                val dx = col - center
                val dy = row - center
                val dist = sqrt((dx * dx + dy * dy).toDouble())
                val angle = atan2(dy.toDouble(), dx.toDouble()) + PI / 2
                val normalizedAngle = ((angle % (2 * PI) + 2 * PI) % (2 * PI))
                
                // 5-pointed star: outer radius varies by angle
                val pointIndex = (normalizedAngle * 5 / (2 * PI)).toInt() % 5
                val outerRadius = if (pointIndex % 2 == 0) 8.0 else 4.0
                
                if (dist < outerRadius) {
                    grid[row][col] = true
                }
            }
        }
        
        return grid
    }

    private fun createHeartPixelArt(size: Int): Array<Array<Boolean>> {
        val grid = Array(size) { Array(size) { false } }
        val centerX = size / 2
        val centerY = size / 2 - 2
        
        for (row in 0 until size) {
            for (col in 0 until size) {
                val x = (col - centerX).toDouble() / (size / 2.0)
                val y = (row - centerY).toDouble() / (size / 2.0)
                
                // Heart equation: (x^2 + y^2 - 1)^3 - x^2 * y^3 <= 0
                val heart = (x * x + y * y - 1).pow(3.0) - x * x * y.pow(3.0)
                if (heart <= 0.15) {
                    grid[row][col] = true
                }
            }
        }
        
        return grid
    }

    private fun createButterflyPixelArt(size: Int): Array<Array<Boolean>> {
        val grid = Array(size) { Array(size) { false } }
        val centerX = size / 2
        val centerY = size / 2
        
        // Body (vertical line)
        for (row in size / 4 until 3 * size / 4) {
            if (row in 0 until size && centerX in 0 until size) {
                grid[row][centerX] = true
                if (centerX - 1 >= 0) grid[row][centerX - 1] = true
                if (centerX + 1 < size) grid[row][centerX + 1] = true
            }
        }
        
        // Upper left wing
        for (row in 0 until size / 2) {
            for (col in 0 until centerX) {
                val dx = (col - centerX / 2).toDouble()
                val dy = (row - centerY / 2).toDouble()
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < size / 3.5) {
                    grid[row][col] = true
                }
            }
        }
        
        // Upper right wing
        for (row in 0 until size / 2) {
            for (col in centerX until size) {
                val dx = (col - centerX * 1.5).toDouble()
                val dy = (row - centerY / 2).toDouble()
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < size / 3.5) {
                    grid[row][col] = true
                }
            }
        }
        
        // Lower left wing
        for (row in size / 2 until size) {
            for (col in 0 until centerX) {
                val dx = (col - centerX / 2).toDouble()
                val dy = (row - centerY * 1.5).toDouble()
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < size / 4.5) {
                    grid[row][col] = true
                }
            }
        }
        
        // Lower right wing
        for (row in size / 2 until size) {
            for (col in centerX until size) {
                val dx = (col - centerX * 1.5).toDouble()
                val dy = (row - centerY * 1.5).toDouble()
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < size / 4.5) {
                    grid[row][col] = true
                }
            }
        }
        
        // Antennae
        if (size / 4 - 1 in 0 until size) {
            if (centerX - 2 >= 0) grid[size / 4 - 1][centerX - 2] = true
            if (centerX + 2 < size) grid[size / 4 - 1][centerX + 2] = true
        }
        
        return grid
    }
    
    private fun createCatPixelArt(size: Int): Array<Array<Boolean>> {
        val grid = Array(size) { Array(size) { false } }
        val centerX = size / 2
        val centerY = size / 2
        
        // Left ear
        for (row in 0 until 5) {
            for (col in 2 until 7) {
                if (row + col <= 8 && row in 0 until size && col in 0 until size) {
                    grid[row][col] = true
                }
            }
        }
        
        // Right ear
        for (row in 0 until 5) {
            for (col in size - 7 until size - 2) {
                if (row + (size - 1 - col) <= 8 && row in 0 until size && col in 0 until size) {
                    grid[row][col] = true
                }
            }
        }
        
        // Head (circle)
        for (row in 3 until size - 3) {
            for (col in centerX - 6 until centerX + 7) {
                if (row in 0 until size && col in 0 until size) {
                    val dx = col - centerX
                    val dy = row - centerY
                    val dist = sqrt((dx * dx + dy * dy).toDouble())
                    if (dist < 7) {
                        grid[row][col] = true
                    }
                }
            }
        }
        
        // Eyes
        grid[centerY - 2][centerX - 3] = true
        grid[centerY - 2][centerX + 3] = true
        grid[centerY - 1][centerX - 3] = true
        grid[centerY - 1][centerX + 3] = true
        
        // Nose
        grid[centerY][centerX] = true
        grid[centerY + 1][centerX] = true
        
        // Mouth
        grid[centerY + 2][centerX - 2] = true
        grid[centerY + 2][centerX - 1] = true
        grid[centerY + 2][centerX + 1] = true
        grid[centerY + 2][centerX + 2] = true
        
        return grid
    }
    
    private fun createCarPixelArt(size: Int): Array<Array<Boolean>> {
        val grid = Array(size) { Array(size) { false } }
        val centerY = size / 2
        
        // Car body (rectangle)
        for (row in centerY - 2 until centerY + 4) {
            for (col in 2 until size - 2) {
                if (row in 0 until size && col in 0 until size) {
                    grid[row][col] = true
                }
            }
        }
        
        // Car top (rounded)
        for (row in centerY - 4 until centerY - 1) {
            for (col in 4 until size - 4) {
                if (row in 0 until size && col in 0 until size) {
                    grid[row][col] = true
                }
            }
        }
        
        // Left window
        for (row in centerY - 3 until centerY) {
            for (col in 5 until 8) {
                if (row in 0 until size && col in 0 until size) {
                    grid[row][col] = true
                }
            }
        }
        
        // Right window
        for (row in centerY - 3 until centerY) {
            for (col in size - 8 until size - 5) {
                if (row in 0 until size && col in 0 until size) {
                    grid[row][col] = true
                }
            }
        }
        
        // Left wheel
        for (row in centerY + 1 until centerY + 3) {
            for (col in 3 until 6) {
                if (row in 0 until size && col in 0 until size) {
                    grid[row][col] = true
                }
            }
        }
        
        // Right wheel
        for (row in centerY + 1 until centerY + 3) {
            for (col in size - 6 until size - 3) {
                if (row in 0 until size && col in 0 until size) {
                    grid[row][col] = true
                }
            }
        }
        
        return grid
    }
    
    /**
     * Converts a string pattern to a boolean grid
     * '#' = pixel to color, '.' = empty space
     */
    private fun patternToGrid(pattern: String, size: Int): Array<Array<Boolean>> {
        val lines = pattern.lines().filter { it.isNotBlank() }
        val grid = Array(size) { Array(size) { false } }
        
        // Center the pattern in the grid
        val patternHeight = lines.size
        val patternWidth = lines.maxOfOrNull { it.length } ?: size
        val startRow = (size - patternHeight) / 2
        val startCol = (size - patternWidth) / 2
        
        lines.forEachIndexed { rowIndex, line ->
            line.forEachIndexed { colIndex, char ->
                val gridRow = startRow + rowIndex
                val gridCol = startCol + colIndex
                if (gridRow in 0 until size && gridCol in 0 until size) {
                    grid[gridRow][gridCol] = (char == '#')
                }
            }
        }
        
        return grid
    }
}
