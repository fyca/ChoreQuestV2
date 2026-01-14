package com.chorequest.presentation.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chorequest.domain.models.Reward
import com.chorequest.presentation.components.*

/**
 * Rewards marketplace screen for children to browse and redeem rewards
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsMarketplaceScreen(
    onNavigateBack: () -> Unit,
    userPointsBalance: Int = 0, // TODO: Get from user profile
    viewModel: RewardViewModel = hiltViewModel()
) {
    val allRewards by viewModel.allRewards.collectAsState()
    val redeemState by viewModel.redeemState.collectAsState()
    var selectedReward by remember { mutableStateOf<Reward?>(null) }

    // Show confirmation dialog when reward selected
    selectedReward?.let { reward ->
        RedeemConfirmationDialog(
            reward = reward,
            userPoints = userPointsBalance,
            onDismiss = { selectedReward = null },
            onConfirm = {
                viewModel.redeemReward(reward.id)
                selectedReward = null
            }
        )
    }

    // Show success/error messages
    LaunchedEffect(redeemState) {
        when (redeemState) {
            is RedeemState.Success -> {
                // TODO: Show celebration animation
                viewModel.resetRedeemState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            ChoreQuestTopAppBar(
                title = "🏪 Rewards Store",
                onNavigateBack = onNavigateBack,
                actions = {
                    // Points balance chip
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFFD700),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$userPointsBalance",
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (allRewards.isEmpty()) {
            EmptyState(
                icon = Icons.Default.CardGiftcard,
                title = "No Rewards Available",
                message = "Ask your parents to add some rewards!",
                actionLabel = null,
                onAction = null
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Choose Your Reward!",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "You have $userPointsBalance points to spend",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // Rewards list
                items(allRewards) { reward ->
                    RewardMarketplaceCard(
                        reward = reward,
                        userPoints = userPointsBalance,
                        onClick = { selectedReward = reward }
                    )
                }
            }
        }

        // Loading overlay
        if (redeemState is RedeemState.Loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun RewardMarketplaceCard(
    reward: Reward,
    userPoints: Int,
    onClick: () -> Unit
) {
    val canAfford = userPoints >= reward.pointCost
    val isAvailable = reward.quantity == null || reward.quantity > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canAfford && isAvailable, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !isAvailable -> Color(0xFFE0E0E0)
                !canAfford -> Color(0xFFFFE5E5)
                else -> Color(0xFFE5FFE5)
            }
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !isAvailable -> Color.Gray
                            !canAfford -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = reward.imageUrl ?: "🎁",
                    fontSize = 36.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reward.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                
                if (reward.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = reward.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black.copy(alpha = 0.6f),
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Status/affordability indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        !isAvailable -> {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Out of Stock",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray
                            )
                        }
                        !canAfford -> {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Need ${reward.pointCost - userPoints} more points",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "You can get this!",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Price badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            !isAvailable -> Color.Gray
                            !canAfford -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Stars,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${reward.pointCost}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun RedeemConfirmationDialog(
    reward: Reward,
    userPoints: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CardGiftcard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text("Redeem This Reward?", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("${reward.title}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Cost: ${reward.pointCost} points")
                Text("Your balance after: ${userPoints - reward.pointCost} points")
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Ask a parent to approve!",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Yes, Redeem!")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
