package com.lostsierra.chorequest.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Celebration animation styles
 */
enum class CelebrationStyle {
    FIREWORKS,
    CONFETTI,
    STARS
}

/**
 * Main celebration animation composable
 */
@Composable
fun CelebrationAnimation(
    style: CelebrationStyle = CelebrationStyle.FIREWORKS,
    pointsEarned: Int,
    onAnimationComplete: () -> Unit = {}
) {
    var animationStarted by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        animationStarted = true
        kotlinx.coroutines.delay(3000) // Animation duration
        onAnimationComplete()
    }

    if (animationStarted) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Background overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = Color.Black.copy(alpha = 0.3f))
            }

            // Animation layer
            when (style) {
                CelebrationStyle.FIREWORKS -> FireworksAnimation()
                CelebrationStyle.CONFETTI -> ConfettiAnimation()
                CelebrationStyle.STARS -> StarsAnimation()
            }

            // Points display
            PointsEarnedDisplay(points = pointsEarned)
        }
    }
}

/**
 * Fireworks animation
 */
@Composable
private fun FireworksAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "fireworks")
    
    val particles = remember {
        List(50) {
            FireworkParticle(
                startX = Random.nextFloat(),
                startY = Random.nextFloat() * 0.5f,
                angle = Random.nextFloat() * 360f,
                speed = Random.nextFloat() * 5f + 2f,
                color = listOf(
                    Color(0xFFFF6B6B),
                    Color(0xFF4ECDC4),
                    Color(0xFFFFE66D),
                    Color(0xFF95E1D3),
                    Color(0xFFF38181)
                ).random(),
                delay = Random.nextLong(0, 1000)
            )
        }
    }

    val animationProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            val adjustedProgress = ((animationProgress * 2000 - particle.delay) / 2000f).coerceIn(0f, 1f)
            if (adjustedProgress > 0f) {
                drawFireworkParticle(particle, adjustedProgress)
            }
        }
    }
}

/**
 * Confetti animation
 */
@Composable
private fun ConfettiAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    
    val confettiPieces = remember {
        List(100) {
            ConfettiPiece(
                startX = Random.nextFloat(),
                startY = -0.1f,
                rotation = Random.nextFloat() * 360f,
                color = listOf(
                    Color(0xFFFF6B6B),
                    Color(0xFF4ECDC4),
                    Color(0xFFFFE66D),
                    Color(0xFF95E1D3),
                    Color(0xFFF38181),
                    Color(0xFFB8E6E6),
                    Color(0xFFFFC75F)
                ).random(),
                size = Random.nextFloat() * 20f + 10f,
                swingAmplitude = Random.nextFloat() * 50f + 20f,
                fallSpeed = Random.nextFloat() * 0.5f + 0.5f,
                delay = Random.nextLong(0, 1500)
            )
        }
    }

    val animationProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fall"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        confettiPieces.forEach { piece ->
            val adjustedProgress = ((animationProgress * 3000 - piece.delay) / 3000f).coerceIn(0f, 1f)
            if (adjustedProgress > 0f) {
                drawConfettiPiece(piece, adjustedProgress)
            }
        }
    }
}

/**
 * Stars animation
 */
@Composable
private fun StarsAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    
    val stars = remember {
        List(30) {
            Star(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 30f + 20f,
                color = listOf(
                    Color(0xFFFFD700),
                    Color(0xFFFFA500),
                    Color(0xFFFFE66D),
                    Color(0xFFFFFF00)
                ).random(),
                delay = Random.nextLong(0, 1000),
                duration = Random.nextInt(800, 1500)
            )
        }
    }

    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        stars.forEach { star ->
            val adjustedTime = ((time * 2000 - star.delay) / star.duration.toFloat()).coerceIn(0f, 1f)
            if (adjustedTime > 0f) {
                drawStar(star, adjustedTime)
            }
        }
    }
}

/**
 * Points earned display
 */
@Composable
private fun PointsEarnedDisplay(points: Int) {
    val scale by rememberInfiniteTransition(label = "points").animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🎉",
            fontSize = (60 * scale).sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "+$points Points!",
            fontSize = (48 * scale).sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD700)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedText(
            text = "Great Job!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

// Data classes
private data class FireworkParticle(
    val startX: Float,
    val startY: Float,
    val angle: Float,
    val speed: Float,
    val color: Color,
    val delay: Long
)

private data class ConfettiPiece(
    val startX: Float,
    val startY: Float,
    val rotation: Float,
    val color: Color,
    val size: Float,
    val swingAmplitude: Float,
    val fallSpeed: Float,
    val delay: Long
)

private data class Star(
    val x: Float,
    val y: Float,
    val size: Float,
    val color: Color,
    val delay: Long,
    val duration: Int
)

// Draw functions
private fun DrawScope.drawFireworkParticle(particle: FireworkParticle, progress: Float) {
    val angleRad = Math.toRadians(particle.angle.toDouble())
    val distance = particle.speed * progress * 200
    val x = particle.startX * size.width + (cos(angleRad) * distance).toFloat()
    val y = particle.startY * size.height + (sin(angleRad) * distance).toFloat()
    
    val alpha = (1f - progress).coerceIn(0f, 1f)
    val radius = 8f * (1f - progress * 0.5f)
    
    drawCircle(
        color = particle.color.copy(alpha = alpha),
        radius = radius,
        center = Offset(x, y)
    )
}

private fun DrawScope.drawConfettiPiece(piece: ConfettiPiece, progress: Float) {
    val y = piece.startY * size.height + (progress * size.height * piece.fallSpeed * 1.5f)
    val swingOffset = sin(progress * Math.PI * 4) * piece.swingAmplitude
    val x = piece.startX * size.width + swingOffset.toFloat()
    
    val rotationProgress = progress * 720f + piece.rotation
    
    drawRect(
        color = piece.color,
        topLeft = Offset(x - piece.size / 2, y - piece.size / 2),
        size = androidx.compose.ui.geometry.Size(piece.size, piece.size)
    )
}

private fun DrawScope.drawStar(star: Star, progress: Float) {
    val scale = if (progress < 0.5f) {
        progress * 2f
    } else {
        (1f - progress) * 2f
    }
    
    val alpha = if (progress < 0.5f) {
        progress * 2f
    } else {
        (1f - progress) * 2f
    }
    
    val x = star.x * size.width
    val y = star.y * size.height
    val radius = star.size * scale
    
    // Draw star shape (simplified as circle for now)
    drawCircle(
        color = star.color.copy(alpha = alpha),
        radius = radius,
        center = Offset(x, y)
    )
}
