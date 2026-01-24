# Jigsaw Puzzle Images Guide

## Adding Puzzle Images

To add puzzle images to the jigsaw puzzle game:

1. **Place images in the drawable folder:**
   - Location: `android/app/src/main/res/drawable/`
   - Naming convention: `puzzle_image_1.png`, `puzzle_image_2.png`, `puzzle_image_3.png`, etc.

2. **Update the AVAILABLE_PUZZLE_IMAGES list:**
   - File: `android/app/src/main/java/com/lostsierra/chorequest/presentation/games/JigsawPuzzleViewModel.kt`
   - Add the resource ID to the list:
   ```kotlin
   val AVAILABLE_PUZZLE_IMAGES: List<Int> = listOf(
       com.lostsierra.chorequest.R.drawable.puzzle_image_1,
       com.lostsierra.chorequest.R.drawable.puzzle_image_2,
       com.lostsierra.chorequest.R.drawable.puzzle_image_3,
       // Add more as needed
   )
   ```

## Features

- **Custom Piece Count**: Users can set custom rows and columns (2-10)
- **Drag and Drop**: Drag a piece over another piece to swap them
- **Image Selection**: Choose from available puzzle images
- **Timer**: Tracks completion time
- **Best Time**: Saves and displays best completion time
- **Move Counter**: Tracks number of moves

## How It Works

1. Select an image from the available puzzle images
2. Set the number of pieces (rows × columns)
3. The image is automatically cut into jigsaw puzzle pieces with tabs and blanks
4. Pieces are shuffled and displayed in a grid
5. Drag and drop pieces to swap them
6. When a piece is in its correct position, it locks in place
7. Complete the puzzle by placing all pieces correctly
