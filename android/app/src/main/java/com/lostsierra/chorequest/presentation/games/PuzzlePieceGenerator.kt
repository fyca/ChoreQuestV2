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
        
        val pieceWidth = originalBitmap.width / cols
        val pieceHeight = originalBitmap.height / rows
        
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
                    pieceHeight = pieceHeight
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
     * Creates a simple rectangular puzzle piece (no tabs/blanks)
     */
    private fun createPuzzlePiece(
        originalBitmap: Bitmap,
        row: Int,
        col: Int,
        rows: Int,
        cols: Int,
        pieceWidth: Int,
        pieceHeight: Int
    ): Bitmap {
        // Create bitmap for the piece
        val pieceBitmap = Bitmap.createBitmap(
            pieceWidth,
            pieceHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(pieceBitmap)
        
        // Draw the corresponding section of the original image
        val srcRect = android.graphics.Rect(
            col * pieceWidth,
            row * pieceHeight,
            (col + 1) * pieceWidth,
            (row + 1) * pieceHeight
        )
        val dstRect = android.graphics.Rect(0, 0, pieceWidth, pieceHeight)
        
        canvas.drawBitmap(originalBitmap, srcRect, dstRect, null)
        
        return pieceBitmap
    }
    
}
