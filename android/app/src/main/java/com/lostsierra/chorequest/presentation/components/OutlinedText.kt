package com.lostsierra.chorequest.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Text with an outline/stroke for better visibility on any background.
 * Creates the outline effect by layering multiple Text components with offsets.
 * 
 * @param text The text to display
 * @param modifier Modifier for the text
 * @param fontSize Font size
 * @param fontWeight Font weight
 * @param color Text color
 * @param outlineColor Outline/stroke color (defaults to black for light text, white for dark text)
 * @param outlineWidth Width of the outline in dp
 * @param style Additional text style
 * @param textAlign Text alignment
 */
@Composable
fun OutlinedText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = Color.White,
    outlineColor: Color? = null,
    outlineWidth: Float = 1.5f,
    style: TextStyle = TextStyle.Default,
    textAlign: TextAlign? = null
) {
    // Determine outline color: black for light text, white for dark text
    // Calculate luminance manually: L = 0.299*R + 0.587*G + 0.114*B
    val strokeColor = outlineColor ?: run {
        val luminance = if (color == Color.White) {
            1.0f
        } else {
            // Extract RGB values (0-1 range)
            val red = color.red
            val green = color.green
            val blue = color.blue
            // Calculate relative luminance
            0.299f * red + 0.587f * green + 0.114f * blue
        }
        if (luminance > 0.5f) {
            Color.Black.copy(alpha = 0.9f)
        } else {
            Color.White.copy(alpha = 0.9f)
        }
    }
    
    Box(modifier = modifier) {
        // Draw outline by drawing text multiple times with small offsets
        val offsets = listOf(
            -outlineWidth to -outlineWidth,
            outlineWidth to -outlineWidth,
            -outlineWidth to outlineWidth,
            outlineWidth to outlineWidth,
            -outlineWidth to 0f,
            outlineWidth to 0f,
            0f to -outlineWidth,
            0f to outlineWidth
        )
        
        offsets.forEach { (dx, dy) ->
            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = fontWeight,
                color = strokeColor,
                style = style,
                textAlign = textAlign,
                modifier = Modifier.offset(x = dx.dp, y = dy.dp)
            )
        }
        
        // Draw the main text on top
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            style = style,
            textAlign = textAlign
        )
    }
}
