package com.lostsierra.chorequest.presentation.games

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Path

object PuzzlePieceGenerator {
    
    /**
     * Generates puzzle pieces from an image resource
     */
    fun generatePuzzlePieces(
        imageResId: Int,
        rows: Int,
        cols: Int,
        context: Context
    ): List<PuzzlePiece> {
        // Load the original image
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
        }
        val originalBitmap = BitmapFactory.decodeResource(context.resources, imageResId, options)
            ?: return emptyList()
        
        // Calculate square piece size based on the smaller dimension to ensure pieces fit
        // This ensures all pieces are square regardless of image aspect ratio
        val imageWidth = originalBitmap.width
        val imageHeight = originalBitmap.height
        
        // Calculate piece size to fit the image in a square grid
        val pieceSize = minOf(imageWidth / cols, imageHeight / rows)
        
        // Calculate the actual grid dimensions that will fit
        val actualGridWidth = pieceSize * cols
        val actualGridHeight = pieceSize * rows
        
        // Center the crop region
        val cropX = (imageWidth - actualGridWidth) / 2
        val cropY = (imageHeight - actualGridHeight) / 2
        
        val pieceWidth = pieceSize
        val pieceHeight = pieceSize
        
        val pieces = mutableListOf<PuzzlePiece>()
        
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val pieceBitmap = createPuzzlePiece(
                    originalBitmap = originalBitmap,
                    row = row,
                    col = col,
                    rows = rows,
                    cols = cols,
                    pieceWidth = pieceWidth,
                    pieceHeight = pieceHeight,
                    cropX = cropX,
                    cropY = cropY
                )
                
                pieces.add(
                    PuzzlePiece(
                        id = row * cols + col,
                        correctRow = row,
                        correctCol = col,
                        currentRow = row,
                        currentCol = col,
                        pieceBitmap = pieceBitmap,
                        isPlaced = false
                    )
                )
            }
        }
        
        return pieces
    }
    
    /**
     * Creates a simple square puzzle piece (no tabs/blanks)
     */
    private fun createPuzzlePiece(
        originalBitmap: Bitmap,
        row: Int,
        col: Int,
        rows: Int,
        cols: Int,
        pieceWidth: Int,
        pieceHeight: Int,
        cropX: Int,
        cropY: Int
    ): Bitmap {
        // Create bitmap for the piece
        val pieceBitmap = Bitmap.createBitmap(
            pieceWidth,
            pieceHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(pieceBitmap)
        
        // Draw the corresponding section of the original image
        // Account for the crop offset to center the square grid
        val srcRect = android.graphics.Rect(
            cropX + col * pieceWidth,
            cropY + row * pieceHeight,
            cropX + (col + 1) * pieceWidth,
            cropY + (row + 1) * pieceHeight
        )
        val dstRect = android.graphics.Rect(0, 0, pieceWidth, pieceHeight)
        
        canvas.drawBitmap(originalBitmap, srcRect, dstRect, null)
        
        return pieceBitmap
    }
    
}
