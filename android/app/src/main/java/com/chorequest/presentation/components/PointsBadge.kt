package com.chorequest.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Animated points badge component
 */
@Composable
fun PointsBadge(
    points: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    textColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    AnimatedContent(
        targetState = points,
        transitionSpec = {
            (slideInVertically(animationSpec = tween(300)) { height -> height } + fadeIn()).togetherWith(
                slideOutVertically(animationSpec = tween(300)) { height -> -height } + fadeOut()
            )
        },
        label = "points_animation",
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { animatedPoints ->
        Text(
            text = "$animatedPoints pts",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = textColor
        )
    }
}

/**
 * Large points display for dashboards
 */
@Composable
fun LargePointsDisplay(
    points: Int,
    modifier: Modifier = Modifier,
    label: String = "Your Points"
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))

        AnimatedContent(
            targetState = points,
            transitionSpec = {
                (slideInVertically(animationSpec = tween(400)) { height -> height } + fadeIn()).togetherWith(
                    slideOutVertically(animationSpec = tween(400)) { height -> -height } + fadeOut()
                )
            },
            label = "large_points_animation"
        ) { animatedPoints ->
            Text(
                text = animatedPoints.toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
